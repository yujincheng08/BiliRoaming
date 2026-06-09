package me.iacn.biliroaming.hook

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import me.iacn.biliroaming.utils.getFirstFieldByExactTypeOrNull
import me.iacn.biliroaming.utils.getResId

object SelectableCopyDialog {
    fun show(context: Context, text: CharSequence, shareTitle: String = "分享评论内容") {
        val copyText = text.toString()
        val appDialogTheme = getResId("AppTheme.Dialog.Alert", "style")
        AlertDialog.Builder(context, appDialogTheme).run {
            setTitle("自由复制内容")
            setMessage(copyText)
            setPositiveButton("分享") { _, _ ->
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, copyText)
                    type = "text/plain"
                }
                val chooser = Intent.createChooser(sendIntent, shareTitle)
                if (context !is Activity) {
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
            setNeutralButton("复制全部") { _, _ ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("text", copyText))
            }
            setNegativeButton(android.R.string.cancel, null)
            show()
        }.apply {
            findViewById<TextView>(android.R.id.message).setTextIsSelectable(true)
        }
    }

    fun findText(view: View): CharSequence? =
        sequenceOf(
            textFromViewSelf(view),
            textFromViewFields(view),
            textFromChildren(view),
        ).firstNotNullOfOrNull { it }

    private fun textFromViewSelf(view: View): CharSequence? =
        (view as? TextView)?.text?.takeIf { it.isUsefulText() }

    private fun textFromViewFields(view: View): CharSequence? =
        sequence {
            view.getFirstFieldByExactTypeOrNull<CharSequence>()?.let { yield(it) }
            var clazz: Class<*>? = view.javaClass
            while (clazz != null && clazz != View::class.java) {
                clazz.declaredFields.forEach { field ->
                    if (!CharSequence::class.java.isAssignableFrom(field.type)) return@forEach
                    runCatching {
                        field.isAccessible = true
                        field.get(view) as? CharSequence
                    }.getOrNull()?.let { yield(it) }
                }
                clazz = clazz.superclass
            }
        }.filter { it.isUsefulText() }
            .maxByOrNull { it.length }

    private fun textFromChildren(view: View): CharSequence? {
        val group = view as? ViewGroup ?: return null
        return (0 until group.childCount).asSequence()
            .mapNotNull { findText(group.getChildAt(it)) }
            .filter { it.isUsefulText() }
            .maxByOrNull { it.length }
    }

    private fun CharSequence.isUsefulText() = isNotBlank() && trim().length > 1
}
