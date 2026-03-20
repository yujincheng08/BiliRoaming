package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.Constant
import me.iacn.biliroaming.utils.hookMethod
import me.iacn.biliroaming.utils.packageName

class KillDelayBootHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        instance.gripperBootExpClass?.hookMethod(
            if (packageName == Constant.PLAY_PACKAGE_NAME) "b" else "getDelayMillis"
        ) { chain ->
            chain.proceed()
            -1L
        }
    }
}