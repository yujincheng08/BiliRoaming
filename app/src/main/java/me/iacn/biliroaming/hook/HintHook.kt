package me.iacn.biliroaming.hook

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.R
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.hookAfterMethod
import me.iacn.biliroaming.utils.sPrefs
import me.iacn.biliroaming.utils.setObjectField

class HintHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("show_hint", true)) return
        instance.mainActivityClass?.hookAfterMethod("onCreate", Bundle::class.java) { param ->
            AlertDialog.Builder(param.thisObject as Activity).run {
                val newContext =
                    context.applicationContext.createConfigurationContext(XposedInit.moduleRes.configuration)
                newContext.setObjectField("mResources", XposedInit.moduleRes)
                setTitle("哔哩漫游使用说明")
                setView(View.inflate(newContext, R.layout.feature, null))
                setNegativeButton("知道了") { _, _ ->
                    sPrefs.edit().putBoolean("show_hint", false).apply()
                }
                show()
            }
        }
    }
}
