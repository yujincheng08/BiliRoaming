package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.hookBeforeConstructor
import me.iacn.biliroaming.utils.sPrefs

class PublishToFollowingHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("disable_auto_select", false))
            return
        instance.publishToFollowingConfigClass?.hookBeforeConstructor(
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        ) { it.args[2]/*autoSelectOnce*/ = false }
    }
}
