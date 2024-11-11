package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.hookAfterMethod

class KillDelayBootHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        instance.gripperBootExpClass?.hookAfterMethod("getDelayMillis") { param ->
            param.result = -1L
        }
    }
}