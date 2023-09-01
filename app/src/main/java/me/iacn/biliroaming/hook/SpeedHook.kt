package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.callMethod
import me.iacn.biliroaming.utils.hookAfterMethod
import me.iacn.biliroaming.utils.sPrefs

class SpeedHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    override fun startHook() {
        Log.d("startHook: SpeedHook")
        val defaultPlaybackSpeed = sPrefs.getInt("default_speed", 100) / 100f
        if (defaultPlaybackSpeed == 1f) return
        instance.playerCoreServiceV2Class?.hookAfterMethod(
            instance.playerOnPrepare(),
            instance.playerCoreServiceV2Class,
            "tv.danmaku.ijk.media.player.IMediaPlayer"
        ) {
            val currentPlaybackSpeed = it.args[0].callMethod(instance.getPlaybackSpeed(), false)
            if (currentPlaybackSpeed == 1.0f) {
                instance.setPlaybackSpeed()
                    ?.let { mName ->
                        it.args[0].callMethod(mName, defaultPlaybackSpeed)
                        Log.toast("已设置 $defaultPlaybackSpeed 倍速")
                    }
                instance.theseusPlayerSetSpeed()
                    ?.let { mName -> it.args[0].callMethod(mName, defaultPlaybackSpeed) }
            }
        }
    }
}
