package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.sPrefs

class FullStoryHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("disable_story_full", false)) return
        instance.playerFullStoryWidget()?.let {
            instance.playerFullStoryWidgetClass?.hookBeforeMethod(it, instance.playerFullStoryWidgetClass) { param ->
                param.result = false
            }
        }
    }
}
