package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.hookConstructor
import me.iacn.biliroaming.utils.sPrefs

class PublishToFollowingHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("disable_auto_select", false))
            return
        instance.publishToFollowingConfigClass?.hookConstructor(
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        ) { chain ->
            val args = chain.args.toTypedArray()
            args[2]/*autoSelectOnce*/ = false
            chain.proceed(args)
        }
    }
}
