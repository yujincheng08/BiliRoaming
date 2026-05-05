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
        )?.hookMethod("initP2PClient") { null }
        if (blockPcdn) {
            "tv.danmaku.ijk.media.player.P2P".from(mClassLoader)?.run {
                hookMethod("getInstance", Context::class.java, Bundle::class.java) { chain ->
                    val args = chain.args.toTypedArray()
                    args[0] = null
                    args[1]!!.callMethod("clear")
                    chain.proceed(args)
                }
                hookConstructor(Context::class.java, Bundle::class.java) { chain ->
                    val args = chain.args.toTypedArray()
                    args[0] = null
                    args[1]!!.callMethod("clear")
                    chain.proceed(args)
                }
            }
        }
        if (blockPcdnLive) {
            instance.liveRtcEnable()?.let {
                instance.liveRtcEnableClass?.hookMethod(it) { false }
            }
            "com.bilibili.bililive.playercore.p2p.P2PType".from(mClassLoader)?.run {
                hookMethod("create", Int::class.javaPrimitiveType) { chain ->
                    val args = chain.args.toTypedArray()
                    args[0] = 0
                    chain.proceed(args)
                }
                hookMethod("createTo", Int::class.javaPrimitiveType) { chain ->
                    val args = chain.args.toTypedArray()
                    args[0] = 0
                    chain.proceed(args)
                }
            }
        }
    }
}
