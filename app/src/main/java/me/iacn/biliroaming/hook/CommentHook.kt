package me.iacn.biliroaming.hook

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.text.SpannableStringBuilder
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class CommentHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("comment_copy", false)) return
        instance.descCopy()?.split(';')?.let {
            instance.descCopyView()?.split(';')?.zip(it)
        }?.forEach { p ->
            p.first.replaceMethod(
                mClassLoader,
                p.second,
                View::class.java,
                ClickableSpan::class.java
            ) { param ->
                if (!sPrefs.getBoolean("comment_copy_enhance", false)) return@replaceMethod Unit

                param.thisObject.getFirstFieldByExactTypeOrNull<SpannableStringBuilder>()?.let {
                    val view = param.args[0] as View
                    showCopyDialog(view.context, it, param)
                } ?: (param.args[0] as? TextView)?.let { tv ->
                    showCopyDialog(tv.context, tv.text, param)
                }
            }
        }


        instance.dynamicDescHolderListenerClass?.replaceMethod(
            "onLongClick",
            View::class.java
        ) { param ->
            if (!sPrefs.getBoolean("comment_copy_enhance", false)) return@replaceMethod true
            val dyCardTextId = getId("dy_card_text")
            (param.args[0] as? View)?.findViewById<TextView>(dyCardTextId)?.let {
                if (instance.ellipsizingTextViewClass?.isInstance(it) == true) it else null

            }?.let { view ->
                view.getFirstFieldByExactTypeOrNull<CharSequence>()?.also { text ->
                    showCopyDialog(view.context, text, param)
                }
            } ?: Log.toast("找不到动态内容", true)
            true
        }
        instance.commentCopyClass?.replaceMethod("onLongClick", View::class.java) { param ->
            if (!sPrefs.getBoolean("comment_copy_enhance", false)) return@replaceMethod true
            val messageId = getId("message")
            (param.args[0] as? View)?.findViewById<View>(messageId)?.let {
                if (instance.commentSpanTextViewClass?.isInstance(it) == true ||
                    instance.commentSpanEllipsisTextViewClass?.isInstance(it) == true
                ) it else null
            }?.let { view ->
                view.getFirstFieldByExactTypeOrNull<CharSequence>()?.also { text ->
                    showCopyDialog(view.context, text, param)
                }
            } ?: Log.toast("找不到评论内容", true)
            true
        }
    }

    private fun showCopyDialog(context: Context, text: CharSequence, param: MethodHookParam) {
        AlertDialog.Builder(context).run {
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
