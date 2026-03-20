package me.iacn.biliroaming.hook

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.text.SpannableStringBuilder
import android.text.style.ClickableSpan
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import org.json.JSONObject

class CopyHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        private val DYNAMIC_COPYABLE_IDS = arrayOf(
            "dy_card_text",
            "dy_opus_paragraph_desc",
            "dy_opus_paragraph_title",
            "dy_opus_copy_right_id",
            "dy_opus_paragraph_text",
        )
    }

    private val enhanceLongClickCopy = sPrefs.getBoolean("comment_copy_enhance", false)

    override fun startHook() {
        if (!sPrefs.getBoolean("comment_copy", false)) return
        instance.descCopyView().zip(instance.descCopy()).forEach { p ->
            val clazz = p.first ?: return@forEach
            val method = p.second ?: return@forEach
            clazz.hookMethod(
                method,
                View::class.java,
                ClickableSpan::class.java
            ) { chain ->
                if (!enhanceLongClickCopy) return@hookMethod Unit

                chain.thisObject!!.getFirstFieldByExactTypeOrNull<SpannableStringBuilder>()?.let {
                    val view = chain.args[0] as View
                    showCopyDialog(view.context, it)
                } ?: (chain.args[0] as? TextView)?.let { tv ->
                    showCopyDialog(tv.context, tv.text)
                }
            }
        }

        instance.dynamicDescHolderListeners().forEach { c ->
            c?.hookMethod("onLongClick", View::class.java) { chain ->
                if (!enhanceLongClickCopy)
                    return@hookMethod true
                val itemView = chain.args[0] as? View
                DYNAMIC_COPYABLE_IDS.asSequence().firstNotNullOfOrNull { n ->
                    getId(n).takeIf { it != 0 }?.let { itemView?.findViewById<TextView>(it) }
                }?.let { v ->
                    (if (instance.ellipsizingTextViewClass?.isInstance(v) == true) {
                        v.getFirstFieldByExactTypeOrNull()
                    } else v.text)?.also { text ->
                        showCopyDialog(v.context, text)
                    }
                } ?: Log.toast("找不到动态内容", true)
                true
            }
        }


        val commentCopyHook = fun(chain: XposedInterface.Chain, idName: String): Any? {
            if (!enhanceLongClickCopy) return true
            if (chain.args[0] is FrameLayout) return chain.proceed()
            (chain.args[0] as? View)?.findViewById<View>(getId(idName))?.let {
                if (instance.commentSpanTextViewClass?.isInstance(it) == true ||
                    instance.commentSpanEllipsisTextViewClass?.isInstance(it) == true
                ) it else null
            }?.let { view ->
                view.getFirstFieldByExactTypeOrNull<CharSequence>()?.also { text ->
                    showCopyDialog(view.context, text)
                }
            } ?: Log.toast("找不到评论内容", true)
            return true
        }
        instance.commentCopyClass?.hookMethod("onLongClick", View::class.java) { chain ->
            commentCopyHook(chain, "message")
        }
        instance.commentCopyNewClass?.hookMethod("onLongClick", View::class.java) { chain ->
            commentCopyHook(chain, "comment_message")
        }

        instance.comment3CopyClass?.let { c ->
            instance.comment3Copy()?.let { m ->
                instance.comment3ViewIndex().let { i ->
                    c.hookAllMethods(m) { chain ->
                        if (!enhanceLongClickCopy) return@hookAllMethods true
                        val view = chain.args[i] as View
                        view.getFirstFieldByExactTypeOrNull<CharSequence>()?.also { text ->
                            showCopyDialog(view.context, text)
                        }
                        return@hookAllMethods true
                    }
                }
            }
        }

        if (!enhanceLongClickCopy) return
        "com.bilibili.bplus.im.conversation.ConversationActivity".from(mClassLoader)
            ?.declaredMethods?.find {
                it.name == instance.onOperateClick() && it.parameterTypes.size == 8
            }?.hookMethod { chain ->
                if (chain.args.last() == chain.args.first()) {
                    val activity = chain.thisObject as Activity
                    val json = chain.args[1]!!.callMethodOrNullAs(instance.getContentString()) ?: ""
                    val text = runCatchingOrNull { json.toJSONObject() }?.run {
                        optString("content").ifEmpty {
                            buildString {
                                appendLine(optString("title").trim())
                                appendLine(optString("text").trim())
                                optJSONArray("modules")?.run {
                                    asSequence<JSONObject>().map {
                                        it.optString("title") + "：" + it.optString("detail")
                                    }.joinToString("\n").run {
                                        append(this)
                                    }
                                }
                            }.run { removeSuffix("\n") }
                        }
                    } ?: return@hookMethod chain.proceed()
                    showCopyDialog(activity, text)
                    chain.args[6]!!.callMethodOrNull("dismiss")
                    return@hookMethod null
                }
                chain.proceed()
            }
    }

    private fun showCopyDialog(context: Context, text: CharSequence) {
        val appDialogTheme = getResId("AppTheme.Dialog.Alert", "style")
        AlertDialog.Builder(context, appDialogTheme).run {
            setTitle("自由复制内容")
            setMessage(text)
            setPositiveButton("分享") { _, _ ->
                context.startActivity(
                    Intent.createChooser(
                        Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, text)
                            type = "text/plain"
                        }, "分享评论内容"
                    )
                )
            }
            setNeutralButton("复制全部") { _, _ ->
                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                clipboardManager.setPrimaryClip(
                    android.content.ClipData.newPlainText("copied_text", text)
                )
                Log.toast("已复制", false)
            }
            setNegativeButton(android.R.string.cancel, null)
            show()
        }.apply {
            findViewById<TextView>(android.R.id.message).setTextIsSelectable(true)
        }
    }
}
