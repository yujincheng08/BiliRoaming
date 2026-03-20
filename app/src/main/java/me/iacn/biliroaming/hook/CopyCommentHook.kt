package me.iacn.biliroaming.hook

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.TextView
import me.iacn.biliroaming.utils.currentContext
import me.iacn.biliroaming.utils.getResId
import me.iacn.biliroaming.utils.hookAfterMethod
import me.iacn.biliroaming.utils.sPrefs


class CopyCommentHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("copy_comment", false)) return
        "com.bilibili.app.comment3.ui.widget.menu.CommentMoreMenuItemHolder".hookAfterMethod(
            mClassLoader,
            "y3",
            "kotlin.jvm.functions.Function1",
            "com.bilibili.app.comment3.data.model.CommentItem\$MenuItem",
            "android.view.View",
        ) { param ->
            val menu = param.args[1]
            val view = param.args[2] as View
            if (!menu.toString().contains("COPY")) return@hookAfterMethod

            val clipboard =
                currentContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            // 实在是找不到了。那你就说有没有获取到吧
            val txt = clipboard.primaryClip!!.getItemAt(0).text
            AlertDialog.Builder(view.context, getResId("AppTheme.Dialog.Alert", "style"))
                .run {
                    setTitle("自由复制内容")
                    setMessage(txt)
                    setPositiveButton("完成") { _, _ -> }
                    setNegativeButton("复制全部") { _, _ -> }
                    show()
                }.apply {
                    findViewById<TextView>(android.R.id.message).setTextIsSelectable(true)
                }
        }
    }
}
