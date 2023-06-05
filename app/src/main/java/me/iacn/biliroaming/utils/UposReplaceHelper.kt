package me.iacn.biliroaming.utils

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.future.future
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.R
import me.iacn.biliroaming.UGCPlayViewReply
import me.iacn.biliroaming.XposedInit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

object UposReplaceHelper {
    private val aliHost = string(R.string.ali_host)
    private val cosHost = string(R.string.cos_host)
    private val hwHost = string(R.string.hw_host)
    private val aliovHost = string(R.string.aliov_host)
    private val hwovHost = string(R.string.hwov_host)
    private val hkBcacheHost = string(R.string.hk_bcache_host)

    val isLocatedCn by lazy {
        (runCatchingOrNull { XposedInit.country.get(5L, TimeUnit.SECONDS) } ?: "cn") == "cn"
    }

    val forceUpos = sPrefs.getBoolean("force_upos", false)
    val enablePcdnBlock = sPrefs.getBoolean("block_pcdn", false)
    val enableLivePcdnBlock = sPrefs.getBoolean("block_pcdn_live", false)

    private lateinit var videoUposList: CompletableFuture<List<String>>
    private val mainVideoUpos =
        sPrefs.getString("upos_host", null) ?: if (isLocatedCn) hwHost else aliovHost
    private val serverList = XposedInit.moduleRes.getStringArray(R.array.upos_values)
    private val extraVideoUposList = when (serverList.indexOf(mainVideoUpos)) {
        in 1..3 -> listOf(hwHost, cosHost)
        in 5..7 -> listOf(hwHost, aliHost)
        in 8..15 -> listOf(aliHost, cosHost)
        else -> listOf(aliHost, hkBcacheHost)
    }
    private val videoUposBase by lazy {
        runCatchingOrNull { videoUposList.get(500L, TimeUnit.MILLISECONDS) }?.get(0)
            ?: mainVideoUpos
    }
    val videoUposBackups by lazy {
        runCatchingOrNull { videoUposList.get(500L, TimeUnit.MILLISECONDS) }?.subList(1, 3)
            ?: extraVideoUposList
    }
    const val liveUpos = "c1--cn-gotcha01.bilivideo.com"

    val enableUposReplace = (mainVideoUpos != "\$1")

    private val overseaVideoUposRegex by lazy {
        Regex("""(akamai|(ali|hw|cos)\w*ov|hk-eq-bcache|bstar1)""")
    }
    private val urlBwRegex by lazy { Regex("""(bw=[^&]*)""") }
    val ipPCdnRegex by lazy { Regex("""^https?://\d{1,3}\.\d{1,3}""") }
    val gotchaRegex by lazy { Regex("""https?://\w*--\w*-gotcha\d*\.bilivideo""") }

    fun initVideoUposList() {
        videoUposList = MainScope().future(Dispatchers.IO) {
            val bCacheRegex = Regex("""cn-.*\.bilivideo""")
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
                            add(stream.dashVideo.baseUrl)
                            addAll(stream.dashVideo.backupUrlList)
                        }
                        info.dashAudioList?.forEach { dashItem ->
                            add(dashItem.baseUrl)
                            addAll(dashItem.backupUrlList)
                        }
                    }
                }?.mapNotNull { Uri.parse(it).encodedAuthority }?.distinct()
                    ?.filter { !it.isBadUpos() }.orEmpty()
                addAll(officialList.filter { !(it.contains(bCacheRegex) || it == mainVideoUpos) }
                    .ifEmpty { officialList })
                addAll(extraVideoUposList)
            }
        }
    }

    fun String.isNeedReplaceVideoUpos() = forceUpos || (enablePcdnBlock && isBadUpos())

    fun String.replaceUpos(
        upos: String = videoUposBase, needReplace: Boolean = true
    ): String {
        fun String.replaceUposBw(): String = replace(urlBwRegex, "bw=1280000")

        return if (needReplace) {
            val uri = Uri.parse(this)
            val newUpos = uri.getQueryParameter("xy_usource") ?: upos
            uri.replaceUpos(newUpos).toString().replaceUposBw()
        } else replaceUposBw()
    }

    fun Uri.replaceUpos(upos: String): Uri = buildUpon().authority(upos).build()

    private fun String.isBadUpos() = contains("szbdyd.com") || contains(".mcdn.bilivideo")

    fun String.reconstructVideoUpos(upos: String = videoUposBase) = replaceUpos(
        upos, isBadUpos() || this.contains(overseaVideoUposRegex)
    )

    private fun string(resId: Int) = XposedInit.moduleRes.getString(resId)
}
