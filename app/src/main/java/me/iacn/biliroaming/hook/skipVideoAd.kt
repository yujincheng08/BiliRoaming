package me.iacn.biliroaming.hook

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.BilibiliSponsorBlock
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.av2bv
import me.iacn.biliroaming.utils.callMethod
import me.iacn.biliroaming.utils.callMethodAs
import me.iacn.biliroaming.utils.hookAfterMethod
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.mossResponseHandlerReplaceProxy
import me.iacn.biliroaming.utils.sPrefs
import java.lang.ref.WeakReference

class SkipVideoAd(classLoader: ClassLoader) : BaseHook(classLoader) {

    private var lastSeekTime = 0L
    private var playerRef: WeakReference<Any>? = null
    private val player get() = playerRef?.get()
    private var duration: Int = -1
    private var segments : List<BilibiliSponsorBlock.Segment>? = null
    private var bvid: String = ""
    private var cid: String = ""
    private var waitTime = 1000

    override fun startHook() {
        if (!sPrefs.getBoolean("skip_video_ad", false)) return

        Log.d("startHook: SkipVideoAd")

        instance.playerMossClass?.apply {
            hookBeforeMethod("executePlayViewUnite",
                instance.playViewUniteReqClass
            ) { param ->
                val req = param.args[0]
                bvid = req.callMethodAs("getBvid")
                val vod = req.callMethod("getVod")?:return@hookBeforeMethod
                if (bvid.isEmpty()){
                    val aid = vod.callMethodAs<Long>("getAid")
                    if (aid==-1L){
                        return@hookBeforeMethod
                    }
                    bvid = av2bv(aid)
                }
                cid = vod.callMethodAs<Long>("getCid").toString()
            }

            hookBeforeMethod("playViewUnite",
                instance.playViewUniteReqClass,
                instance.mossResponseHandlerClass
            ){ param ->
                param.args[1] = param.args[1].mossResponseHandlerReplaceProxy { reply ->
                    reply ?: return@mossResponseHandlerReplaceProxy null
                    val playArc = reply.callMethod("getPlayArc")?:return@mossResponseHandlerReplaceProxy null
                    cid = playArc.callMethodAs<Long>("getCid").toString()
                    val aid = playArc.callMethodAs<Long>("getAid")?:-1L
                    if (aid==-1L){
                        return@mossResponseHandlerReplaceProxy null
                    }
                    bvid = av2bv(aid)
                    null
                }
            }
        }

        instance.playerCoreServiceV2Class?.apply {
            hookAfterMethod("G1", Int::class.java) { param ->
                playerRef = WeakReference(param.thisObject)
                val state = param.args[0] as Int
                if (state in 3..5 && duration<=0) {
                    duration = (player?.callMethodAs<Int>("getDuration") ?: -1)
                }
                if(state == 2) {
                    duration = -1
                    segments = null
                    CoroutineScope(Dispatchers.IO).launch{
                        var retryCount = 0
                        val maxRetries = 3
                        while (retryCount < maxRetries) {
                            segments = BilibiliSponsorBlock(bvid, cid).getSegments()
                            if (segments.isNullOrEmpty()) {
                                retryCount++
                                delay(1000)
                            } else {
                                break
                            }
                        }
                        if (segments == null){
                            Log.toast("广告片段数据获取失败")
                            return@launch
                        }

                    }
                }
            }

            hookAfterMethod("getCurrentPosition") { param ->
                val now = System.currentTimeMillis()
                if (now - lastSeekTime > waitTime) {
                    lastSeekTime = now
                    waitTime = if(seekTo(param.result as Int)) 3000 else 1000
                }
            }
        }
    }

    private fun seekTo(position: Int?): Boolean {
        if (position != null) {
            if (position > duration) return  false
        }

        if (segments != null) {
            for (segment in segments) {
                val start = (segment.segment[0]*1000).toInt()
                val end = (segment.segment[1]*1000).toInt()
                if (position in start..<end) {
                    Log.toast("已跳过广告片段")
                    player?.callMethod("seekTo", end)
                    return true
                }
            }
        }
        return false
    }
}



