package me.iacn.biliroaming.hook

import android.os.Bundle
import java.util.List
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class P2pHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        Log.d("startHook: P2P")
        val blockPcdn = sPrefs.getBoolean("block_pcdn", false)
        val blockPcdnLive = sPrefs.getBoolean("block_pcdn_live", false)
        if (blockPcdn) {
            "tv.danmaku.ijk.media.player.P2P".from(mClassLoader)?.run {
                replaceMethod("resolveP2PServerUrls", Bundle::class.java) {}
                replaceMethod("isNeedCreateClient") { false }
                replaceMethod("getHttpServerPort") { -1 }
                replaceMethod("isServerEffective", List::class.java, List::class.java) { false }
            }
        }
        if (blockPcdnLive) {
            "com.bilibili.sistersplayer.p2p.P2PContext".from(mClassLoader)?.replaceMethod("setConfiguration", String::class.java) {}
            "com.bilibili.sistersplayer.p2p.SistersPlayerLoader".from(mClassLoader)?.run {
                hookBeforeMethod("setCanP2PUpload", Boolean::class.java) { param ->
                    param.args[0] = false
                }
                replaceMethod("initP2PContext") {}
            }
            "com.bilibili.sistersplayer.p2p.peer.NyaPeer".from(mClassLoader)?.run {
                replaceMethod("shouldUsedAsSeed") { false }
                replaceMethod("acceptAnswer", String::class.java) {}
            }
            "com.bilibili.sistersplayer.p2p.tracker.GetPeersResult".from(mClassLoader)?.run {
                replaceMethod("getPeers") {}
                replaceMethod("getLeeches") {}
            }
            // optimize non p2p perf
            instance.liveNetworkTypeClass?.replaceMethod(instance.liveNetworkType()) { "unknown" }
        }
    }
}
