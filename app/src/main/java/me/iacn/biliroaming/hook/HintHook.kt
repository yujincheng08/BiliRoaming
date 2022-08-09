package me.iacn.biliroaming.hook

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.R
import me.iacn.biliroaming.SettingDialog
import me.iacn.biliroaming.utils.hookAfterMethod
import me.iacn.biliroaming.utils.sPrefs

class HintHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("show_hint", true)) return
        // 设置解析服务器(只设置一次)
        sPrefs.edit().putString("cn_server", "哔哩.plus").apply()
        sPrefs.edit().putString("tw_server", "哔哩.plus").apply()
        sPrefs.edit().putString("hk_server", "哔哩.plus").apply()
        sPrefs.edit().putString("th_server", "哔哩.plus").apply()
        instance.mainActivityClass?.hookAfterMethod("onCreate", Bundle::class.java) { param ->
            AlertDialog.Builder(param.thisObject as Activity).run {
                SettingDialog.addModulePath(context)
                setTitle("哔哩漫游使用说明")
                setView(View.inflate(context, R.layout.feature, null))
                setNegativeButton("知道了") { _, _ ->
                    sPrefs.edit().putBoolean("show_hint", false).apply()
                }
                show()
            }
        }
    }
}
