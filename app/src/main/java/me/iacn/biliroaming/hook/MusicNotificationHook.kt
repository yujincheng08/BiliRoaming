package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.findClassOrNull
import me.iacn.biliroaming.utils.hookMethod
import me.iacn.biliroaming.utils.sPrefs

class MusicNotificationHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("music_notification", false)) return

        Log.d("startHook: MusicNotification")

        "com.bilibili.lib.blconfig.ConfigManager\$Companion".findClassOrNull(mClassLoader)?.run {
            hookMethod(
                "isHitFF",
                String::class.java
            ) { chain ->
                (chain.args[0] as String).run {
                    if (this == "ff_background_use_system_media_controls") {
                        return@hookMethod true
                    }
                }
                chain.proceed()
            }
        }

        "com.bilibili.lib.dd.DeviceDecision".findClassOrNull(mClassLoader)?.hookMethod(
            "getBoolean",
            String::class.java,
            Boolean::class.javaPrimitiveType
        ) { chain ->
            if (chain.args[0] == "dd_enable_system_media_control") {
                return@hookMethod true
            }
            chain.proceed()
        }

        // Play store
        "com.bilibili.lib.blconfig.ConfigManager\$a".findClassOrNull(mClassLoader)?.run {
            declaredMethods.firstOrNull {
                it.name == "g" && it.parameterTypes.contentEquals(arrayOf(String::class.java))
            }?.hookMethod { chain ->
                (chain.args[0] as String).run {
                    if (this == "ff_background_use_system_media_controls") {
                        return@hookMethod true
                    }
                }
                chain.proceed()
            }
        }
    }

}
