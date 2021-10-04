package me.iacn.biliroaming.hook

import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.robv.android.xposed.XposedHelpers
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.replaceMethod
import me.iacn.biliroaming.utils.sPrefs

class CommentHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("comment_copy", false)) return
        instance.commentCopyClass?.replaceMethod("onLongClick", View::class.java) {
            if (sPrefs.getBoolean("comment_copy_enhance", false)) {
                val view = it.args[0] as ViewGroup
                var text: CharSequence? = null
                for (i in 0 until view.childCount) {
                    val childView = view.getChildAt(i)
                    if (childView.javaClass == instance.commentExpandableTextViewClass) {
                        val field = XposedHelpers.findFirstFieldByExactType(childView.javaClass, java.lang.CharSequence::class.java)
                        text = field.get(childView) as CharSequence
                        break
                    }
                }
                if (text != null) {
                    val dialog = AlertDialog.Builder(view.context).setTitle("评论内容").setMessage(text).create()
                    dialog.show()
                    dialog.findViewById<TextView>(android.R.id.message).setTextIsSelectable(true)
                } else {
                    Log.toast("找不到评论内容", true)
                }
            }
            true
        }
    }
}
