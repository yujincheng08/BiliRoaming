package me.iacn.biliroaming.hook

import android.app.Activity
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

        private val COMMENT3_CONTENT_HANDLER_CLASSES = arrayOf(
            "com.bilibili.app.comment3.ui.holder.handle.CommentContentRichTextHandler",
            "com.bilibili.app.comment3.ui.nextholder.handle.CommentNextContentRichTextHandler",
            "com.bilibili.app.comment3.ui.nextholderexp3.handle.CommentNextExperiment3ContentRichTextHandler",
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
                    SelectableCopyDialog.show(view.context, it)
                } ?: (param.args[0] as? TextView)?.let { tv ->
                    SelectableCopyDialog.show(tv.context, tv.text)
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
                        SelectableCopyDialog.show(v.context, text)
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
                    SelectableCopyDialog.show(view.context, text)
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

        hookComment3LongClickCopy()

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
                    SelectableCopyDialog.show(activity, text)
                    param.args[6].callMethodOrNull("dismiss")
                    param.result = null
                }
            }
    }

    private fun hookComment3LongClickCopy() {
        COMMENT3_CONTENT_HANDLER_CLASSES.asSequence()
            .mapNotNull { it.findClassOrNull(mClassLoader) }
            .flatMap { clazz ->
                clazz.declaredMethods.asSequence().filter { method ->
                    method.returnType == Boolean::class.javaPrimitiveType &&
                        method.parameterTypes.lastOrNull() == View::class.java
                }
            }
            .forEach { method ->
                method.replaceMethod { param ->
                    if (!enhanceLongClickCopy) return@replaceMethod true
                    val view = param.args.lastOrNull() as? View
                        ?: return@replaceMethod param.invokeOriginalMethod()
                    val text = SelectableCopyDialog.findText(view)
                        ?: return@replaceMethod param.invokeOriginalMethod()
                    SelectableCopyDialog.show(view.context, text)
                    true
                }
            }
    }
}
