package me.iacn.biliroaming.hook

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.TextView
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class DownloadThreadHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("custom_download_thread", false)) return
        Log.d("startHook: DownloadThread")
        instance.downloadThreadListenerClass?.run {
            hookAllConstructors { chain ->
                val view = chain.args.find { it is TextView } as? TextView
                    ?: return@hookAllConstructors chain.proceed()
                val visibility = if (view.tag as Int == 1) {
                    view.text = "自定义"
                    View.VISIBLE
                } else {
                    View.INVISIBLE
                }
                (view.parent as ViewGroup).getChildAt(1).visibility = visibility
                chain.proceed()
            }
            hookMethod("onClick", View::class.java) { chain ->
                var textViewField: String? = null
                var viewHostField: String? = null
                declaredFields.forEach {
                    when (it.type) {
                        instance.downloadThreadViewHostClass -> viewHostField = it.name
                        TextView::class.java -> textViewField = it.name
                    }
                }
                val view = chain.thisObject!!.getObjectFieldAs<TextView>(textViewField)
                if (view.tag as? Int == 1) {
                    AlertDialog.Builder(view.context).create().run {
                        setTitle("自定义同时缓存数")
                        val numberPicker = NumberPicker(context).apply {
                            minValue = 1
                            maxValue = 64
                            wrapSelectorWheel = false
                            value = chain.thisObject!!.getObjectField(viewHostField)
                                ?.getIntField(instance.downloadingThread())
                                ?: 1
                        }
                        setView(numberPicker, 50, 0, 50, 0)
                        setButton(AlertDialog.BUTTON_POSITIVE, "OK") { _, _ ->
                            view.tag = numberPicker.value
                            chain.proceed()
                        }
                        show()
                    }
                } else {
                    chain.proceed()
                }
            }
        }
        instance.reportDownloadThreadClass?.hookMethod(
            instance.reportDownloadThread(),
            Context::class.java,
            Int::class.javaPrimitiveType
        ) { null }
    }
}
