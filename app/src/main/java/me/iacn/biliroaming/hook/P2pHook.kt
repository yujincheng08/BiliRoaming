package me.iacn.biliroaming.hook

import android.content.Context
import android.os.Bundle
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class P2pHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val blockPcdn = sPrefs.getBoolean("block_pcdn", false)
    private val blockPcdnLive = sPrefs.getBoolean("block_pcdn_live", false)
    override fun startHook() {
        if (!blockPcdn && !blockPcdnLive) return
        Log.d("startHook: P2P")
        "tv.danmaku.ijk.media.player.IjkMediaPlayer\$IjkMediaPlayerServiceConnection".from(
            mClassLoader
        )?.replaceMethod("initP2PClient") {}
        if (blockPcdn) {
            "tv.danmaku.ijk.media.player.P2P".from(mClassLoader)?.run {
                hookBeforeMethod("getInstance", Context::class.java, Bundle::class.java) { param ->
                    param.args[0] = null
                    param.args[1].callMethod("clear")
                }
                hookBeforeConstructor(Context::class.java, Bundle::class.java) { param ->
                    param.args[0] = null
                    param.args[1].callMethod("clear")
                }
            }
        }
        if (blockPcdnLive) {
            instance.liveRtcEnable()?.let {
                instance.liveRtcEnableClass?.replaceMethod(it) { false }
            }
            "com.bilibili.bililive.playercore.p2p.P2PType".from(mClassLoader)?.run {
                hookBeforeMethod("create", Int::class.javaPrimitiveType) { it.args[0] = 0 }
                hookBeforeMethod("createTo", Int::class.javaPrimitiveType) { it.args[0] = 0 }
            }
        }
    }
}
