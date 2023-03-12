package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.*
import java.util.List
import android.content.SharedPreferences;
import android.os.Bundle;
import android.content.Context;

class P2pHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        Log.d("startHook: P2P")
        val blockPcdn = sPrefs.getBoolean("block_pcdn", false);
        if (blockPcdn) {
            // detect if pcdn is need, set default as false 
            "tv.danmaku.ijk.media.player.P2P".from(mClassLoader)
                ?.replaceMethod(
                    "isNeedCreateClient"
                ) { param ->
                    Log.d("p2p block -> tv.danmaku.ijk.media.player.P2P#isNeedCreateClient")
                    false
                }
            // sometimes client will invoke this, set default as -1
            "tv.danmaku.ijk.media.player.P2P".from(mClassLoader)
                ?.replaceMethod(
                    "getHttpServerPort"
                ) { param ->
                    Log.d("p2p block -> tv.danmaku.ijk.media.player.P2P#getHttpServerPort")
                    -1
                }
            // bili livestream p2p use this, not testing webrtc
            "com.bilibili.sistersplayer.p2p.P2PContext".from(mClassLoader)
                ?.replaceMethod(
                    "setConfiguration",
                    String::class.java
                ) { param ->
                    Log.d("p2p block -> com.bilibili.sistersplayer.p2p.P2PContext#setConfiguration")
                }
            // the following are for backup, no action noticed
            "tv.danmaku.ijk.media.player.P2P".from(mClassLoader)
                ?.replaceMethod(
                    "initGrpcAndStunServerConfig",
                    Bundle::class.java, SharedPreferences::class.java, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java
                ) { param ->
                    Log.d("p2p block -> tv.danmaku.ijk.media.player.P2P#initGrpcAndStunServerConfig")
                    
                }
            "tv.danmaku.ijk.media.player.P2P".from(mClassLoader)
                ?.replaceMethod(
                    "saveSharedPreferences",
                    SharedPreferences::class.java, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java
                ) { param ->
                    Log.d("p2p block -> tv.danmaku.ijk.media.player.P2P#saveSharedPreferences")
                }
            "tv.danmaku.ijk.media.player.P2P".from(mClassLoader)
                ?.replaceMethod(
                    "resolveP2PServerUrls",
                    Bundle::class.java
                ) { param ->
                    Log.d("p2p block -> tv.danmaku.ijk.media.player.P2P#resolveP2PServerUrls")
                }
            "tv.danmaku.ijk.media.player.P2P".from(mClassLoader)
                ?.replaceMethod(
                    "getTrackerChannelFd"
                ) { param ->
                    Log.d("p2p block -> tv.danmaku.ijk.media.player.P2P#getTrackerChannelFd")
                }
            "tv.danmaku.ijk.media.player.P2P".from(mClassLoader)
                ?.replaceMethod(
                    "applyNewConfig",
                    Context::class.java, Bundle::class.java
                ) { param ->
                    Log.d("p2p block -> tv.danmaku.ijk.media.player.P2P#applyNewConfig")
                }
            "tv.danmaku.ijk.media.player.P2P".from(mClassLoader)
                ?.replaceMethod(
                    "isServerEffective",
                    List::class.java, List::class.java
                ) { param ->
                    Log.d("p2p block -> tv.danmaku.ijk.media.player.P2P#isServerEffective")
                    false
                }
        }
    }
}
