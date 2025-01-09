package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.replaceMethod
import me.iacn.biliroaming.utils.sPrefs

class TryWatchVipQualityHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("disable_try_watch_vip_quality", false)) return

        instance.vipQualityTrialService?.replaceMethod(instance.canTrialMethod()) { false }
    }
}
