package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.from
import me.iacn.biliroaming.utils.getStaticObjectField
import me.iacn.biliroaming.utils.hookBeforeConstructor
import me.iacn.biliroaming.utils.sPrefs

class AllowMiniPlayHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("main_func", false)) return

        if (sPrefs.getBoolean("allow_mini_play", false)) {
            "com.bilibili.lib.media.resource.PlayConfig\$PlayMenuConfig".from(mClassLoader)
                ?.hookBeforeConstructor(
                    Boolean::class.javaPrimitiveType,
                    "com.bilibili.lib.media.resource.PlayConfig\$PlayConfigType"
                ) { param ->
                    val type = param.args[1]
                    val miniPlayerType =
                        "com.bilibili.lib.media.resource.PlayConfig\$PlayConfigType"
                            .from(mClassLoader)?.getStaticObjectField("MINIPLAYER")
                    if (type == miniPlayerType)
                        param.args[0] = true
                }
        }
    }
}
