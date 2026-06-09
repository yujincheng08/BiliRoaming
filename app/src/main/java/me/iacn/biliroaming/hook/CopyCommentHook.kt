package me.iacn.biliroaming.hook

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.TextView
import me.iacn.biliroaming.utils.currentContext
import me.iacn.biliroaming.utils.findClassOrNull
import me.iacn.biliroaming.utils.getResId
import me.iacn.biliroaming.utils.hookAfterMethod
import me.iacn.biliroaming.utils.sPrefs


class CopyCommentHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("copy_comment", false)) return
        val menuItemHolderClass =
            "com.bilibili.app.comment3.ui.widget.menu.CommentMoreMenuItemHolder".findClassOrNull(mClassLoader)
                ?: return
        val functionClass = "kotlin.jvm.functions.Function1".findClassOrNull(mClassLoader) ?: return
        val menuItemClass =
            "com.bilibili.app.comment3.data.model.CommentItem\$MenuItem".findClassOrNull(mClassLoader)
                ?: return

        menuItemHolderClass.declaredMethods.asSequence()
            .filter { method ->
                val params = method.parameterTypes
                params.size == 3 &&
                        params[0] == functionClass &&
                        params[1] == menuItemClass &&
                        View::class.java.isAssignableFrom(params[2])
            }
            .forEach { method ->
                method.hookAfterMethod { param ->
                    val menu = param.args[1]
                    val view = param.args[2] as View
                    if (!menu.toString().contains("COPY")) return@hookAfterMethod

                    val clipboard =
                        currentContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val txt = clipboard.primaryClip?.getItemAt(0)?.text ?: return@hookAfterMethod
                    AlertDialog.Builder(view.context, getResId("AppTheme.Dialog.Alert", "style"))
                        .run {
                            setTitle("自由复制内容")
                            setMessage(txt)
                            setPositiveButton("完成") { _, _ -> }
                            setNegativeButton("复制全部") { _, _ ->
                                clipboard.setPrimaryClip(ClipData.newPlainText("comment", txt))
                            }
                            show()
                        }.apply {
                            findViewById<TextView>(android.R.id.message).setTextIsSelectable(true)
                        }
                }
            }
    }
}
