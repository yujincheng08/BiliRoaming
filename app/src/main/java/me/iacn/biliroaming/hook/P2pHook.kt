package me.iacn.biliroaming.hook

import android.os.Bundle
import java.util.List
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class P2pHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        Log.d("startHook: P2P")
        val blockPcdn = sPrefs.getBoolean("block_pcdn", true)
        if (blockPcdn) {
            // general player
            "tv.danmaku.ijk.media.player.P2P".from(mClassLoader)?.run {
                replaceMethod("isNeedCreateClient") { 
                    Log.d("block pcdn -> tv.danmaku.ijk.media.player.P2P#isNeedCreateClient")
                    false
                }
                replaceMethod("getHttpServerPort") { 
                    Log.d("block pcdn -> tv.danmaku.ijk.media.player.P2P#getHttpServerPort")
                    -1
                }
                replaceMethod("resolveP2PServerUrls", Bundle::class.java) {
                    Log.d("block pcdn -> tv.danmaku.ijk.media.player.P2P#resolveP2PServerUrls")
                }
                replaceMethod("isServerEffective", List::class.java, List::class.java) {
                    Log.d("block pcdn -> tv.danmaku.ijk.media.player.P2P#isServerEffective")
                    false
                }
            }
            // live related
            "com.bilibili.sistersplayer.p2p.P2PContext".from(mClassLoader)?.run {
                replaceMethod("setConfiguration", String::class.java) {
                    Log.d("block pcdn -> com.bilibili.sistersplayer.p2p.P2PContext#setConfiguration")
                }
            }
            "com.bilibili.sistersplayer.p2p.SistersPlayerLoader".from(mClassLoader)?.run {
                hookBeforeMethod("setCanP2PUpload", Boolean::class.java) { param ->
                    Log.d("block pcdn -> com.bilibili.sistersplayer.p2p.SistersPlayerLoader#setCanP2PUpload RAW ${param.args[0]} -> false")
                    param.args[0] = false
                }
                replaceMethod("initP2PContext") {
                    Log.d("block pcdn -> com.bilibili.sistersplayer.p2p.SistersPlayerLoader#initP2PContext")
                }
            }
            "com.bilibili.sistersplayer.p2p.peer.NyaPeer".from(mClassLoader)?.run {
                replaceMethod("shouldUsedAsSeed") {
                    Log.d("block pcdn -> com.bilibili.sistersplayer.p2p.peer.NyaPeer#shouldUsedAsSeed")
                    false
                }
                replaceMethod("acceptAnswer", String::class.java) {
                    Log.d("block pcdn -> com.bilibili.sistersplayer.p2p.peer.NyaPeer#acceptAnswer")
                }
            }
            "com.bilibili.sistersplayer.p2p.tracker.GetPeersResult".from(mClassLoader)?.run {
                replaceMethod("getPeers") {
                    Log.d("block pcdn -> com.bilibili.sistersplayer.p2p.tracker.GetPeersResult#getPeers")
                }
                replaceMethod("getLeeches") {
                    Log.d("block pcdn -> com.bilibili.sistersplayer.p2p.tracker.GetPeersResult#getLeeches")
                }
            }
            // optimize non p2p perf
            instance.liveNetworkTypeClass?.replaceMethod(instance.liveNetworkType()) {
                Log.d("block pcdn -> liveNetworkTypeClass#liveNetworkType()")
                "unknown"
            }
        }
    }
}
