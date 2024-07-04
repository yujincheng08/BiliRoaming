package me.iacn.biliroaming.hook

import de.robv.android.xposed.XposedHelpers
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.findClassOrNull
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.sPrefs

class MusicNotificationHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("music_notification", false)) return

        Log.d("startHook: MusicNotification")

        "com.bilibili.lib.blconfig.ConfigManager\$Companion".findClassOrNull(mClassLoader)?.run {
            hookBeforeMethod(
                    "isHitFF",
                    String::class.java
            ) { param ->
                (param.args[0] as String).run {
                    if (this == "ff_background_use_system_media_controls") {
                        param.result = true
                    }
                }
            }
        }

        // Play store
        "com.bilibili.lib.blconfig.ConfigManager\$a".findClassOrNull(mClassLoader)?.run {
            XposedHelpers.findMethodExactIfExists(this, "g", String::class.java)?.hookBeforeMethod {
                (it.args[0] as String).run {
                    if (this == "ff_background_use_system_media_controls") {
                        it.result = true
                    }
                }
            }
        }
    }

}
