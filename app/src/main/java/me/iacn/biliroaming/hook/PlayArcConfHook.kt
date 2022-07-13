package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.replaceMethod
import me.iacn.biliroaming.utils.sPrefs

class PlayArcConfHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("play_arc_conf", false)) return

        instance.arcConfClass?.replaceMethod("getDisabled") { false }
        instance.arcConfExtraContentClass?.replaceMethod("getDisabledCode") { 0L }
        instance.arcConfExtraContentClass?.replaceMethod("getDisabledReason") { "" }
    }
}
