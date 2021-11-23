package me.iacn.biliroaming.hook

import android.app.AlertDialog
import android.content.Intent
import android.view.View
import android.widget.TextView
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class CommentHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("comment_copy", false)) return
        val messageId = getId("message")
        instance.commentCopyClass?.replaceMethod("onLongClick", View::class.java) { param ->
            if (sPrefs.getBoolean("comment_copy_enhance", false)) {
                (param.args[0] as View?)?.findViewById<View>(messageId)?.let {
                    if (instance.commentSpanTextViewClass?.isInstance(it) == true) it else null
                }?.let { view ->
                    view.getFirstFieldByExactTypeOrNull<CharSequence>()?.also { text ->
                        AlertDialog.Builder(view.context).run {
                            setTitle("自由复制评论内容")
                            setMessage(text)
                            setPositiveButton("分享") { _, _ ->
                                view.context.startActivity(
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
                } ?: Log.toast("找不到评论内容", true)
            }
            true
        }
    }
}
