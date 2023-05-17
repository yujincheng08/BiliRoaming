package me.iacn.biliroaming.utils

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.future.future
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.DashItemKt
import me.iacn.biliroaming.R
import me.iacn.biliroaming.StreamKt
import me.iacn.biliroaming.UGCPlayViewReply
import me.iacn.biliroaming.VideoInfoKt
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.copy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

object VodServerReplaceHelper {
    private val aliHost = string(R.string.ali_host)
    private val cosHost = string(R.string.cos_host)
    private val hwHost = string(R.string.hw_host)
    private val aliovHost = string(R.string.aliov_host)
    private val hwovHost = string(R.string.hwov_host)
    private val hkBcacheHost = string(R.string.hk_bcache_host)

    private val isLocatedCn by lazy {
        (runCatchingOrNull { XposedInit.country.get(5L, TimeUnit.SECONDS) } ?: "cn") == "cn"
    }

    private val forceVideoVod = sPrefs.getBoolean("force_video_vod", false)
    private val enablePcdnBlock = sPrefs.getBoolean("block_pcdn", false)

    private lateinit var videoVodServer: CompletableFuture<List<String>>
    private val mainVideoVodServer = sPrefs.getString("video_vod_server", null)?.let {
        if (it == "\$1") null else it
    } ?: if (isLocatedCn) hwHost else aliovHost
    private val extraVideoVodServer = if (isLocatedCn) {
        when (mainVideoVodServer) {
            hwHost -> listOf(aliHost, cosHost)
            aliHost -> listOf(hwHost, cosHost)
            else -> listOf(aliHost, hwHost)
        }
    } else {
        when (mainVideoVodServer) {
            aliovHost -> listOf(hkBcacheHost, hwovHost)
            hkBcacheHost -> listOf(aliovHost, hwovHost)
            else -> listOf(hkBcacheHost, aliovHost)
        }
    }
    private val videoVodServerBase by lazy {
        runCatchingOrNull { videoVodServer.get(500L, TimeUnit.MILLISECONDS) }?.get(0)
            ?: mainVideoVodServer
    }
    private val videoVodServerBackups by lazy {
        runCatchingOrNull { videoVodServer.get(500L, TimeUnit.MILLISECONDS) }?.subList(1, 3)
            ?: extraVideoVodServer
    }
    private const val liveVodServer = "c1--cn-gotcha01.bilivideo.com"

    private val badVideoVodServerRegex by lazy {
        Regex("""(szbdyd\.com)|(^(https?://)\w*\.mcdn\.bilivideo)""")
    }
    private val overseaVideoVodServerRegex by lazy {
        Regex("""(akamai|(ali|hw|cos)\w*ov|hk-eq-bcache|bstar1)""")
    }
    private val urlBwRegex by lazy { Regex("""(bw=[^&]*)""") }
    val ipPCdnRegex by lazy { Regex("""^https?://\d{1,3}\.\d{1,3}""") }

    fun initVideoVodServer() {
        videoVodServer = MainScope().future(Dispatchers.IO) {
            val bCacheRegex = Regex("""cn-.*\.bilivideo""")
            val badCdnRegex = Regex("""(szbdyd\.com)|(\w*\.mcdn\.bilivideo)""")
            mutableListOf(mainVideoVodServer).apply {
                // 8K video sample, without area limitation, reply probably contains Mirror CDN
                val playViewReply = instance.playViewReqClass?.new()?.apply {
                    callMethod("setAid", 355749246L)
                    callMethod("setCid", 1115447032L)
                    callMethod("setQn", 127)
                    callMethod("setFnval", 4048)
                    callMethod("setFourk", true)
                    callMethod("setForceHost", 2)
                }?.let { playViewReqUgc ->
                    instance.playURLMossClass?.new()?.callMethod("playView", playViewReqUgc)
                        ?.callMethodAs<ByteArray>("toByteArray")?.let {
                            UGCPlayViewReply.parseFrom(it)
                        }
                }
                val officialList = playViewReply?.videoInfo?.let { info ->
                    mutableListOf<String>().apply {
                        info.streamListList?.forEach { stream ->
                            this.add(stream.dashVideo.baseUrl)
                            this.addAll(stream.dashVideo.backupUrlList)
                        }
                        info.dashAudioList?.forEach { dashItem ->
                            this.add(dashItem.baseUrl)
                            this.addAll(dashItem.backupUrlList)
                        }
                    }
                }?.mapNotNull { Uri.parse(it).encodedAuthority }?.distinct()
                    ?.filter { !it.contains(badCdnRegex) }.orEmpty()
                this.addAll(officialList.filter { !(it.contains(bCacheRegex) || it == mainVideoVodServer) }
                    .ifEmpty { officialList })
                this.addAll(extraVideoVodServer)
            }
        }
    }

    fun String.isNeedReplaceVideoVodServer() =
        forceVideoVod || (enablePcdnBlock && this.contains(badVideoVodServerRegex))

    fun String.replaceVideoVodServer(
        vodServer: String = videoVodServerBase, needReplace: Boolean = true
    ): String {
        fun String.replaceVideoVodBw(): String = this.replace(
            urlBwRegex, "bw=1280000"
        )

        return if (needReplace) {
            val uri = Uri.parse(this)
            val newVodServer = uri.getQueryParameter("xy_usource") ?: vodServer
            uri.buildUpon().authority(newVodServer).build().toString().replaceVideoVodBw()
        } else this.replaceVideoVodBw()
    }

    fun reconstructBackupVideoVodServer(
        baseUrl: String, backupUrls: List<String>, mediaAssertSegment: Any?
    ) = mutableListOf(
        backupUrls.getOrNull(0)?.reconstructVideoVodServer(videoVodServerBackups[0])
            ?: baseUrl.replaceVideoVodServer(
                videoVodServerBackups[0], true
            ),
        backupUrls.getOrNull(1)?.reconstructVideoVodServer(videoVodServerBackups[1])
            ?: baseUrl.replaceVideoVodServer(
                videoVodServerBackups[1], true
            ),
    ).apply {
        if (baseUrl.contains(ipPCdnRegex)) {
            mediaAssertSegment?.setObjectField(
                "url", backupUrls.first().replaceVideoVodServer()
            )
            this[0] = baseUrl
        }
    }

    fun String.replaceLiveVodServer(vodServer: String = liveVodServer) =
        Uri.parse(this).buildUpon().authority(vodServer).build().toString()

    fun VideoInfoKt.Dsl.reconstructVideoInfoVodServer(isDownload: Boolean = false) {
        if (forceVideoVod && !isDownload) return
        val newStreamList = streamList.map { stream ->
            stream.copy { reconstructStreamVodServer() }
        }
        val newDashAudio = dashAudio.map { dashItem ->
            dashItem.copy { reconstructDashItemVodServer() }
        }
        streamList.clear()
        dashAudio.clear()
        streamList.addAll(newStreamList)
        dashAudio.addAll(newDashAudio)
    }

    private fun StreamKt.Dsl.reconstructStreamVodServer() {
        if (hasDashVideo()) {
            dashVideo = dashVideo.copy {
                if (!hasBaseUrl()) return@copy
                val (newBaseUrl, newBackupUrl) = reconstructVideoInfoVodServer(baseUrl, backupUrl)
                baseUrl = newBaseUrl
                backupUrl.clear()
                backupUrl.addAll(newBackupUrl)
            }
        } else if (hasSegmentVideo()) {
            segmentVideo = segmentVideo.copy {
                val newSegment = segment.map { responseUrl ->
                    responseUrl.copy {
                        val (newUrl, newBackupUrl) = reconstructVideoInfoVodServer(url, backupUrl)
                        url = newUrl
                        backupUrl.clear()
                        backupUrl.addAll(newBackupUrl)
                    }
                }
                segment.clear()
                segment.addAll(newSegment)
            }
        }
    }

    private fun DashItemKt.Dsl.reconstructDashItemVodServer() {
        if (!hasBaseUrl()) return
        val (newBaseUrl, newBackupUrl) = reconstructVideoInfoVodServer(baseUrl, backupUrl)
        baseUrl = newBaseUrl
        backupUrl.clear()
        backupUrl.addAll(newBackupUrl)
    }

    private fun reconstructVideoInfoVodServer(
        baseUrl: String, backupUrls: List<String>
    ): Pair<String, List<String>> {
        val filteredBackupUrls = backupUrls.filter { !it.contains(ipPCdnRegex) }
        val newBackupUrls = mutableListOf(
            filteredBackupUrls.getOrNull(0)?.reconstructVideoVodServer(videoVodServerBackups[0])
                ?: baseUrl.replaceVideoVodServer(
                    videoVodServerBackups[0]
                ),
            filteredBackupUrls.getOrNull(1)?.reconstructVideoVodServer(videoVodServerBackups[1])
                ?: baseUrl.replaceVideoVodServer(
                    videoVodServerBackups[1]
                ),
        )
        return if (baseUrl.contains(ipPCdnRegex)) {
            val newBaseUrl = newBackupUrls.firstOrNull { !it.contains(ipPCdnRegex) }
                ?: return baseUrl to backupUrls
            newBackupUrls[0] = baseUrl
            newBaseUrl.reconstructVideoVodServer() to newBackupUrls
        } else {
            baseUrl.reconstructVideoVodServer() to newBackupUrls
        }
    }

    private fun String.reconstructVideoVodServer(vodServer: String = videoVodServerBase) =
        this.replaceVideoVodServer(
            vodServer, this.contains(badVideoVodServerRegex) || this.contains(
                overseaVideoVodServerRegex
            )
        )

    private fun string(resId: Int) = XposedInit.moduleRes.getString(resId)
}
