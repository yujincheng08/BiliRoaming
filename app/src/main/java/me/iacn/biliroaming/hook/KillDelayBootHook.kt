package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.Constant
import me.iacn.biliroaming.utils.hookAfterMethod
import me.iacn.biliroaming.utils.packageName

class KillDelayBootHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        instance.gripperBootExpClass?.hookAfterMethod(
            if (packageName == Constant.PLAY_PACKAGE_NAME) "b" else "getDelayMillis"
        ) { param ->
            param.result = -1L
        }
    }
}