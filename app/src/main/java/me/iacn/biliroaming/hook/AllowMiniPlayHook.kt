package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.from
import me.iacn.biliroaming.utils.getStaticObjectField
import me.iacn.biliroaming.utils.hookAllConstructors
import me.iacn.biliroaming.utils.hookConstructor
import me.iacn.biliroaming.utils.sPrefs

class AllowMiniPlayHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("main_func", false)) return

        if (sPrefs.getBoolean("allow_mini_play", false)) {
            val miniPlayerType =
                    "com.bilibili.lib.media.resource.PlayConfig\$PlayConfigType"
                            .from(mClassLoader)?.getStaticObjectField("MINIPLAYER")
            "com.bilibili.lib.media.resource.PlayConfig\$PlayMenuConfig".from(mClassLoader)?.run {
                hookConstructor(
                        Boolean::class.javaPrimitiveType,
                        "com.bilibili.lib.media.resource.PlayConfig\$PlayConfigType"
                ) { chain ->
                    val args = chain.args.toTypedArray()
                    val type = args[1]
                    if (type == miniPlayerType)
                        args[0] = true
                    chain.proceed(args)
                }
                hookConstructor(Boolean::class.javaPrimitiveType,
                        "com.bilibili.lib.media.resource.PlayConfig\$PlayConfigType",
                        List::class.java
                ) { chain ->
                    val args = chain.args.toTypedArray()
                    val type = args[1]
                    if (type == miniPlayerType)
                        args[0] = true
                    chain.proceed(args)
                }
            }
        }
    }
}
