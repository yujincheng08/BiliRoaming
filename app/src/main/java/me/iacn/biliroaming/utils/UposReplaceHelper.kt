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

object UposReplaceHelper {
    private val aliHost = string(R.string.ali_host)
    private val cosHost = string(R.string.cos_host)
    private val hwHost = string(R.string.hw_host)
    private val aliovHost = string(R.string.aliov_host)
    private val hwovHost = string(R.string.hwov_host)
    private val hkBcacheHost = string(R.string.hk_bcache_host)

    private val isLocatedCn by lazy {
        (runCatchingOrNull { XposedInit.country.get(5L, TimeUnit.SECONDS) } ?: "cn") == "cn"
    }

    private val forceUpos = sPrefs.getBoolean("force_upos", false)
    private val enablePcdnBlock = sPrefs.getBoolean("block_pcdn", false)

    private lateinit var videoUposList: CompletableFuture<List<String>>
    private val mainVideoUpos = sPrefs.getString("upos_host", null)?.let {
        if (it == "\$1") null else it
    } ?: if (isLocatedCn) hwHost else aliovHost
    private val extraVideoUposList = if (isLocatedCn) {
        when (mainVideoUpos) {
            hwHost -> listOf(aliHost, cosHost)
            aliHost -> listOf(hwHost, cosHost)
            else -> listOf(aliHost, hwHost)
        }
    } else {
        when (mainVideoUpos) {
            aliovHost -> listOf(hkBcacheHost, hwovHost)
            hkBcacheHost -> listOf(aliovHost, hwovHost)
            else -> listOf(hkBcacheHost, aliovHost)
        }
    }
    private val videoUposBase by lazy {
        runCatchingOrNull { videoUposList.get(500L, TimeUnit.MILLISECONDS) }?.get(0)
            ?: mainVideoUpos
    }
    private val videoUposBackups by lazy {
        runCatchingOrNull { videoUposList.get(500L, TimeUnit.MILLISECONDS) }?.subList(1, 3)
            ?: extraVideoUposList
    }
    private const val liveUpos = "c1--cn-gotcha01.bilivideo.com"

    private val badVideoUposRegex by lazy {
        Regex("""(szbdyd\.com)|(^(https?://)\w*\.mcdn\.bilivideo)""")
    }
    private val overseaVideoUposRegex by lazy {
        Regex("""(akamai|(ali|hw|cos)\w*ov|hk-eq-bcache|bstar1)""")
    }
    private val urlBwRegex by lazy { Regex("""(bw=[^&]*)""") }
    val ipPCdnRegex by lazy { Regex("""^https?://\d{1,3}\.\d{1,3}""") }

    fun initVideoUposList() {
        videoUposList = MainScope().future(Dispatchers.IO) {
            val bCacheRegex = Regex("""cn-.*\.bilivideo""")
            val badCdnRegex = Regex("""(szbdyd\.com)|(\w*\.mcdn\.bilivideo)""")
            mutableListOf(mainVideoUpos).apply {
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
                this.addAll(officialList.filter { !(it.contains(bCacheRegex) || it == mainVideoUpos) }
                    .ifEmpty { officialList })
                this.addAll(extraVideoUposList)
            }
        }
    }

    fun String.isNeedReplaceVideoUpos() =
        forceUpos || (enablePcdnBlock && this.contains(badVideoUposRegex))

    fun String.replaceVideoUpos(
        upos: String = videoUposBase, needReplace: Boolean = true
    ): String {
        fun String.replaceUposBw(): String = this.replace(
            urlBwRegex, "bw=1280000"
        )

        return if (needReplace) {
            val uri = Uri.parse(this)
            val newUpos = uri.getQueryParameter("xy_usource") ?: upos
            uri.buildUpon().authority(newUpos).build().toString().replaceUposBw()
        } else this.replaceUposBw()
    }

    fun reconstructBackupUposList(
        baseUrl: String, backupUrls: List<String>, mediaAssertSegment: Any?
    ) = mutableListOf(
        backupUrls.getOrNull(0)?.reconstructVideoUpos(videoUposBackups[0])
            ?: baseUrl.replaceVideoUpos(
                videoUposBackups[0], true
            ),
        backupUrls.getOrNull(1)?.reconstructVideoUpos(videoUposBackups[1])
            ?: baseUrl.replaceVideoUpos(
                videoUposBackups[1], true
            ),
    ).apply {
        if (baseUrl.contains(ipPCdnRegex)) {
            mediaAssertSegment?.setObjectField(
                "url", backupUrls.first().replaceVideoUpos()
            )
            this[0] = baseUrl
        }
    }

    fun String.replaceLiveUpos(upos: String = liveUpos) =
        Uri.parse(this).buildUpon().authority(upos).build().toString()

    fun VideoInfoKt.Dsl.reconstructVideoInfoUpos(isDownload: Boolean = false) {
        if (forceUpos && !isDownload) return
        val newStreamList = streamList.map { stream ->
            stream.copy { reconstructStreamUpos() }
        }
        val newDashAudio = dashAudio.map { dashItem ->
            dashItem.copy { reconstructDashItemUpos() }
        }
        streamList.clear()
        dashAudio.clear()
        streamList.addAll(newStreamList)
        dashAudio.addAll(newDashAudio)
    }

    private fun StreamKt.Dsl.reconstructStreamUpos() {
        if (hasDashVideo()) {
            dashVideo = dashVideo.copy {
                if (!hasBaseUrl()) return@copy
                val (newBaseUrl, newBackupUrl) = reconstructVideoInfoUpos(baseUrl, backupUrl)
                baseUrl = newBaseUrl
                backupUrl.clear()
                backupUrl.addAll(newBackupUrl)
            }
        } else if (hasSegmentVideo()) {
            segmentVideo = segmentVideo.copy {
                val newSegment = segment.map { responseUrl ->
                    responseUrl.copy {
                        val (newUrl, newBackupUrl) = reconstructVideoInfoUpos(url, backupUrl)
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

    private fun DashItemKt.Dsl.reconstructDashItemUpos() {
        if (!hasBaseUrl()) return
        val (newBaseUrl, newBackupUrl) = reconstructVideoInfoUpos(baseUrl, backupUrl)
        baseUrl = newBaseUrl
        backupUrl.clear()
        backupUrl.addAll(newBackupUrl)
    }

    private fun reconstructVideoInfoUpos(
        baseUrl: String, backupUrls: List<String>
    ): Pair<String, List<String>> {
        val filteredBackupUrls = backupUrls.filter { !it.contains(ipPCdnRegex) }
        val newBackupUrls = mutableListOf(
            filteredBackupUrls.getOrNull(0)?.reconstructVideoUpos(videoUposBackups[0])
                ?: baseUrl.replaceVideoUpos(
                    videoUposBackups[0]
                ),
            filteredBackupUrls.getOrNull(1)?.reconstructVideoUpos(videoUposBackups[1])
                ?: baseUrl.replaceVideoUpos(
                    videoUposBackups[1]
                ),
        )
        return if (baseUrl.contains(ipPCdnRegex)) {
            val newBaseUrl = newBackupUrls.firstOrNull { !it.contains(ipPCdnRegex) }
                ?: return baseUrl to backupUrls
            newBackupUrls[0] = baseUrl
            newBaseUrl.reconstructVideoUpos() to newBackupUrls
        } else {
            baseUrl.reconstructVideoUpos() to newBackupUrls
        }
    }

    private fun String.reconstructVideoUpos(upos: String = videoUposBase) = this.replaceVideoUpos(
        upos, this.contains(badVideoUposRegex) || this.contains(
            overseaVideoUposRegex
        )
    )

    private fun string(resId: Int) = XposedInit.moduleRes.getString(resId)
}
