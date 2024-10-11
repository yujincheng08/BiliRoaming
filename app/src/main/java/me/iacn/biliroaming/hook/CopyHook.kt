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
            clazz.replaceMethod(
                method,
                View::class.java,
                ClickableSpan::class.java
            ) { param ->
                if (!enhanceLongClickCopy) return@replaceMethod Unit

                param.thisObject.getFirstFieldByExactTypeOrNull<SpannableStringBuilder>()?.let {
                    val view = param.args[0] as View
                    showCopyDialog(view.context, it, param)
                } ?: (param.args[0] as? TextView)?.let { tv ->
                    showCopyDialog(tv.context, tv.text, param)
                }
            }
        }

        instance.dynamicDescHolderListeners().forEach { c ->
            c?.replaceMethod("onLongClick", View::class.java) { param ->
                if (!enhanceLongClickCopy)
                    return@replaceMethod true
                val itemView = param.args[0] as? View
                DYNAMIC_COPYABLE_IDS.asSequence().firstNotNullOfOrNull { n ->
                    getId(n).takeIf { it != 0 }?.let { itemView?.findViewById<TextView>(it) }
                }?.let { v ->
                    (if (instance.ellipsizingTextViewClass?.isInstance(v) == true) {
                        v.getFirstFieldByExactTypeOrNull()
                    } else v.text)?.also { text ->
                        showCopyDialog(v.context, text, param)
                    }
                } ?: Log.toast("找不到动态内容", true)
                true
            }
        }

        val commentCopyHook = fun(param: MethodHookParam, idName: String): Any? {
            if (!enhanceLongClickCopy) return true
            if (param.args[0] is FrameLayout) return param.invokeOriginalMethod()
            (param.args[0] as? View)?.findViewById<View>(getId(idName))?.let {
                if (instance.commentSpanTextViewClass?.isInstance(it) == true ||
                    instance.commentSpanEllipsisTextViewClass?.isInstance(it) == true
                ) it else null
            }?.let { view ->
                view.getFirstFieldByExactTypeOrNull<CharSequence>()?.also { text ->
                    showCopyDialog(view.context, text, param)
                }
            } ?: Log.toast("找不到评论内容", true)
            return true
        }
        instance.commentCopyClass?.replaceMethod("onLongClick", View::class.java) {
            commentCopyHook(it, "message")
        }
        instance.commentCopyNewClass?.replaceMethod("onLongClick", View::class.java) {
            commentCopyHook(it, "comment_message")
        }

        instance.comment3CopyClass?.let { c ->
            instance.comment3Copy()?.let { m ->
                instance.comment3ViewIndex().let { i ->
                    c.replaceAllMethods(m) { param ->
                        if (!enhanceLongClickCopy) return@replaceAllMethods true
                        val view = param.args[i] as View
                        view.getFirstFieldByExactTypeOrNull<CharSequence>()?.also { text ->
                            showCopyDialog(view.context, text, param)
                        }
                        return@replaceAllMethods true
                    }
                }
            }
        }

        if (!enhanceLongClickCopy) return
        "com.bilibili.bplus.im.conversation.ConversationActivity".from(mClassLoader)
            ?.declaredMethods?.find {
                it.name == instance.onOperateClick() && it.parameterTypes.size == 8
            }?.hookBeforeMethod { param ->
                if (param.args.last() == param.args.first()) {
                    val activity = param.thisObject as Activity
                    val json = param.args[1].callMethodOrNullAs(instance.getContentString()) ?: ""
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
                    } ?: return@hookBeforeMethod
                    showCopyDialog(activity, text, param)
                    param.args[6].callMethodOrNull("dismiss")
                    param.result = null
                }
            }
    }

    private fun showCopyDialog(context: Context, text: CharSequence, param: MethodHookParam) {
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
                param.invokeOriginalMethod()
            }
            setNegativeButton(android.R.string.cancel, null)
            show()
        }.apply {
            findViewById<TextView>(android.R.id.message).setTextIsSelectable(true)
        }
    }
}
