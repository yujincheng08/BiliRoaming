package me.iacn.biliroaming.hook

import android.app.AlertDialog
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
                        val dialog =
                            AlertDialog.Builder(view.context).setTitle("自由复制评论内容")
                                .setMessage(text).create()
                        dialog.show()
                        dialog.findViewById<TextView>(android.R.id.message)
                            .setTextIsSelectable(true)
                    }
                } ?: Log.toast("找不到评论内容", true)
            }
            true
        }
    }
}
