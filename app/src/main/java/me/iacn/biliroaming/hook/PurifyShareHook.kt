package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.sPrefs
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.hookBeforeMethod

class PurifyShareHook (classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("purify_share", false)) return
        instance.shareClickResultClass?.hookBeforeMethod("getContent") {
            it.result = null
        }
        instance.shareClickResultClass?.hookBeforeMethod("getLink") {
            it.result = null
        }
    }
}
