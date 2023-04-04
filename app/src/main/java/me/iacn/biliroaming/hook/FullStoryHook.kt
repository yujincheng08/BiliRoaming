package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.replaceMethod
import me.iacn.biliroaming.utils.sPrefs

class FullStoryHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("disable_story_full", false)) return
        instance.playerFullStoryWidgets().forEach { (clazz, method) ->
            clazz?.replaceMethod(method, clazz) { false }
        }
    }
}
