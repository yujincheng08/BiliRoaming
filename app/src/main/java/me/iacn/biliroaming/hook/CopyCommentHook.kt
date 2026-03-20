package me.iacn.biliroaming.hook

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.TextView
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.getResId
import me.iacn.biliroaming.utils.hookAllMethods
import me.iacn.biliroaming.utils.sPrefs


class CopyCommentHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("copy_comment", false)) return
        val helperClass = instance.commentMenuCopyClass ?: return
        val methodName = instance.commentMenuCopyActionHandler() ?: return
        helperClass.hookAllMethods(methodName) { chain ->
            val action = chain.args.last()
            if (action.toString() != "COPY") return@hookAllMethods chain.proceed()

            val context = chain.args[0] as Context
            val txt = chain.args[7] as? String ?: return@hookAllMethods chain.proceed()

            AlertDialog.Builder(context, getResId("AppTheme.Dialog.Alert", "style"))
                .run {
                    setTitle("自由复制内容")
                    setMessage(txt)
                    setPositiveButton("完成") { _, _ -> }
                    setNegativeButton("复制全部") { _, _ ->
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("comment", txt))
                    }
                    show()
                }.apply {
                    findViewById<TextView>(android.R.id.message).setTextIsSelectable(true)
                }
            null
        }
    }
}
