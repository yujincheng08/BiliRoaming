package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.sPrefs

class QualityHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        sPrefs.getString("cn_server_accessKey", null) ?: return
        Log.d("startHook: Quality")

        "com.bilibili.lib.accountinfo.model.VipUserInfo".hookBeforeMethod(
            mClassLoader,
            "isEffectiveVip"
        ) {
            Thread.currentThread().stackTrace.find { stack ->
                stack.className.contains(".quality.")
            } ?: return@hookBeforeMethod
            it.result = true
        }
    }
}
