package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.replaceMethod
import me.iacn.biliroaming.utils.sPrefs

class QualityVerifierHooker(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("quality_verify", false)) return

        Log.d("startHook: QualityVerifierHooker")

        "com.bilibili.lib.accountinfo.model.VipUserInfo".replaceMethod(
            mClassLoader,
            "isEffectiveVip"
        ) {
            true
        }
    }
}
