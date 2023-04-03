package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class VideoQualityHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("main_func", false)) return

        val halfScreenQuality = sPrefs.getString("half_screen_quality", "0")?.toInt() ?: 0
        val fullScreenQuality = sPrefs.getString("full_screen_quality", "0")?.toInt() ?: 0
        if (halfScreenQuality != 0) {
            instance.playerPreloadHolderClass?.replaceMethod(
                instance.getPreload(),
                "tv.danmaku.bili.videopage.common.preload.PreloadType",
                String::class.java
            ) { null }
        }
        if (fullScreenQuality != 0) {
            instance.playerSettingHelperClass?.replaceMethod(instance.getDefaultQn()) { fullScreenQuality }
        }
    }
}
