package me.iacn.biliroaming.hook

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.R
import me.iacn.biliroaming.utils.addModuleAssets
import me.iacn.biliroaming.utils.hookMethod
import me.iacn.biliroaming.utils.inflateLayout
import me.iacn.biliroaming.utils.sPrefs

class HintHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("show_hint", true)) return
        instance.mainActivityClass?.hookMethod("onCreate", Bundle::class.java) { chain ->
            val result = chain.proceed()
            AlertDialog.Builder(chain.thisObject as Activity).run {
                context.addModuleAssets()
                setTitle("哔哩漫游使用说明")
                setView(context.inflateLayout(R.layout.feature))
                setNegativeButton("知道了") { _, _ ->
                    sPrefs.edit().putBoolean("show_hint", false).apply()
                }
                show()
            }
            result
        }
    }
}
