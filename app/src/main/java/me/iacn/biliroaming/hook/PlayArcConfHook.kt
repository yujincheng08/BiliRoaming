package me.iacn.biliroaming.hook

import android.net.Uri
import me.iacn.biliroaming.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import kotlin.math.ceil

class PlayArcConfHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val playURLMossClass by Weak { "com.bapis.bilibili.app.playurl.v1.PlayURLMoss" from mClassLoader }
    private val playViewReqClass by Weak { "com.bapis.bilibili.app.playurl.v1.PlayViewReq" from mClassLoader }
    private val playURLMoss get() = playURLMossClass?.new()

    override fun startHook() {
        if (!sPrefs.getBoolean("play_arc_conf", false)) return

        playURLMossClass?.hookAfterMethod("playView", playViewReqClass) { param ->
            param.result?.callMethod("getPlayArc")?.run {
                arrayOf(
                    callMethod("getBackgroundPlayConf"),
                    callMethod("getCastConf"),
                    callMethod("getSmallWindowConf")
                ).forEach {
                    it?.callMethod("setDisabled", false)
                    it?.callMethod("setIsSupport", true)
                    it?.callMethod("clearExtraContent")
                }
            }
        }
        val supportedArcConf = "com.bapis.bilibili.playershared.ArcConf"
            .from(mClassLoader)?.new()?.apply {
                callMethod("setDisabled", false)
                callMethod("setIsSupport", true)
            }
        "com.bapis.bilibili.app.playerunite.v1.PlayerMoss".from(mClassLoader)?.hookAfterMethod(
            "playViewUnite",
            "com.bapis.bilibili.app.playerunite.v1.PlayViewUniteReq"
        ) { param ->
            param.result?.callMethod("getPlayArcConf")
                ?.callMethodAs<LinkedHashMap<Int, Any?>>("internalGetMutableArcConfs")
                ?.let {
                    //CASTCONF
                    it[2] = supportedArcConf
                    //BACKGROUNDPLAY
                    it[9] = supportedArcConf
                    //SMALLWINDOW
                    it[23] = supportedArcConf
                    //LISTEN
                    it[36] = supportedArcConf
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
                val playViewReq = playViewReqClass?.callStaticMethod("parseFrom", playViewReq {
                    epId = req.item.oid
                    cid = subId
                    qn = req.playerArgs.qn
                    fnval = BangumiPlayUrlHook.MAX_FNVAL
                    forceHost = req.playerArgs.forceHost.toInt()
                    fourk = true
                    preferCodecType = CodeType.CODE265
                }.toByteArray()) ?: return@hookAfterMethod
                val playViewReply = PlayViewReply.parseFrom(
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
