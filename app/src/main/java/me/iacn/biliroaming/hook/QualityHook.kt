package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookMethod
import me.iacn.biliroaming.utils.sPrefs

class QualityHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        sPrefs.getString("cn_server_accessKey", null) ?: return
        Log.d("startHook: Quality")

        "com.bilibili.lib.accountinfo.model.VipUserInfo".hookMethod(
            mClassLoader,
            "isEffectiveVip"
        ) { chain ->
            Thread.currentThread().stackTrace.find { stack ->
                stack.className.contains(".quality.")
            } ?: return@hookMethod chain.proceed()
            true
        }
    }
}
