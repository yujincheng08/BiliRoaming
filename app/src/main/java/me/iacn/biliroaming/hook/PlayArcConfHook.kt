package me.iacn.biliroaming.hook

import android.net.Uri
import me.iacn.biliroaming.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import kotlin.math.ceil

class PlayArcConfHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val playURLMoss get() = instance.playURLMossClass?.new()

    override fun startHook() {
        if (!sPrefs.getBoolean("play_arc_conf", false)) return

        instance.arcConfClass?.run {
            replaceMethod("getDisabled") { false }
            replaceMethod("getIsSupport") { true }
        }
        val supportedArcConf = "com.bapis.bilibili.playershared.ArcConf"
            .from(mClassLoader)?.new()?.apply {
                callMethod("setDisabled", false)
                callMethod("setIsSupport", true)
            }
        instance.playerMossClass?.hookAfterMethod(
            "playViewUnite", instance.playViewUniteReqClass
        ) { param ->
            param.result?.callMethod("getPlayArcConf")
                ?.callMethodAs<LinkedHashMap<Int, Any?>>("internalGetMutableArcConfs")
                ?.run {
                    // CASTCONF,BACKGROUNDPLAY,BACKGROUNDPLAY,LISTEN
                    intArrayOf(2, 9, 23, 36).forEach { this[it] = supportedArcConf }
                }
        }
        "com.bapis.bilibili.app.listener.v1.ListenerMoss".from(mClassLoader)?.hookAfterMethod(
            "playURL", "com.bapis.bilibili.app.listener.v1.PlayURLReq"
        ) { param ->
            if (instance.networkExceptionClass?.isInstance(param.throwable) == true)
                return@hookAfterMethod
            val resp = param.result ?: "com.bapis.bilibili.app.listener.v1.PlayURLResp"
                .on(mClassLoader).new()
            val playable = resp.callMethodAs<Int>("getPlayable")
            val playerInfoMap = resp.callMethodAs<Map<*, *>>("getPlayerInfoMap")
            if (playable == 0 && playerInfoMap.isNotEmpty())
                return@hookAfterMethod
            runCatching {
                val req =
                    ListenPlayURLReq.parseFrom(param.args[0].callMethodAs<ByteArray>("toByteArray"))
                val subId = req.item.subIdList.first()
                val playViewReq =
                    instance.playViewReqClass?.callStaticMethod("parseFrom", playViewReq {
                        epId = req.item.oid
                        cid = subId
                        qn = req.playerArgs.qn
                        fnval = BangumiPlayUrlHook.MAX_FNVAL
                        forceHost = req.playerArgs.forceHost.toInt()
                        fourk = true
                        preferCodecType = CodeType.CODE265
                    }.toByteArray()) ?: return@hookAfterMethod
                val playViewReply = UGCPlayViewReply.parseFrom(
                    playURLMoss?.callMethod("playView", playViewReq)
                        ?.callMethodAs<ByteArray>("toByteArray") ?: return@hookAfterMethod
                )
                val playInfo = listenPlayInfo {
                    var deadline = 0L
                    fnval = BangumiPlayUrlHook.MAX_FNVAL
                    format = playViewReply.videoInfo.format
                    length = playViewReply.videoInfo.timelength
                    qn = playViewReply.videoInfo.quality
                    videoCodecid = playViewReply.videoInfo.videoCodecid
                    playDash = listenPlayDASH {
                        playViewReply.videoInfo.dashAudioList.map {
                            listenDashItem {
                                id = it.id
                                size = it.size
                                bandwidth = it.bandwidth
                                baseUrl = it.baseUrl
                                backupUrl.addAll(it.backupUrlList)
                                if (deadline == 0L)
                                    deadline = Uri.parse(baseUrl).getQueryParameter("deadline")
                                        ?.toLongOrNull() ?: 0
                            }
                        }.let { audio.addAll(it) }
                        duration = ceil(playViewReply.videoInfo.timelength / 1000.0).toInt()
                        minBufferTime = 0.0F
                    }
                    playViewReply.videoInfo.streamListList.map {
                        formatDescription {
                            it.streamInfo.let { si ->
                                description = si.description
                                displayDesc = si.displayDesc
                                format = si.format
                                quality = si.quality
                                superscript = si.superscript
                            }
                        }
                    }.let { formats.addAll(it) }
                    expireTime = deadline
                }
                val newResp = listenPlayURLResp {
                    this.item = req.item
                    this.playable = 0
                    this.playerInfo[subId] = playInfo
                }
                param.result = resp.javaClass.callStaticMethod("parseFrom", newResp.toByteArray())
            }.onFailure { Log.e(it) }
        }
    }
}
