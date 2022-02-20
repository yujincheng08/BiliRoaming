package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.sPrefs
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.hookBeforeMethod

class PurifyShareHook (classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("purify_share", false)) return
        instance.shareClickResult?.hookBeforeMethod("getContent") {
            it.result = null
        }
        instance.shareClickResult?.hookBeforeMethod("getLink") {
            it.result = null
        }
    }
}