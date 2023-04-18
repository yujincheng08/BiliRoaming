package me.iacn.biliroaming.utils

import android.net.Uri
import me.iacn.biliroaming.*
import java.util.concurrent.TimeUnit

@Suppress("UNUSED")
object UposReplaceHelper {
    private val isLocatedCn by lazy {
        (runCatchingOrNull { XposedInit.country.get(5L, TimeUnit.SECONDS) } ?: "cn") == "cn"
    }
    private val aliUposHost by lazy { XposedInit.moduleRes.getString(R.string.ali_host) }
    private val cosUposHost by lazy { XposedInit.moduleRes.getString(R.string.cos_host) }
    private val hwUposHost by lazy { XposedInit.moduleRes.getString(R.string.hw_host) }
    private val akamaiUposHost by lazy { XposedInit.moduleRes.getString(R.string.akamai_host) }
    private val aliovUposHost by lazy { XposedInit.moduleRes.getString(R.string.aliov_host) }
    private val cosovUposHost by lazy { XposedInit.moduleRes.getString(R.string.cosov_host) }
    private val hwovUposHost by lazy { XposedInit.moduleRes.getString(R.string.hwov_host) }
    private val hkBcacheUposHost by lazy { XposedInit.moduleRes.getString(R.string.hk_bcache_host) }
    private val replaceAllBaseUpos = sPrefs.getBoolean("replace_all_upos_base", false)
    private val replaceAllBackupUpos = sPrefs.getBoolean("replace_all_upos_backup", false)
    private val replaceUposBw = sPrefs.getBoolean("replace_upos_bw", false)

    private val uposHost by lazy {
        sPrefs.getString("upos_host", null) ?: if (isLocatedCn) hwUposHost else aliovUposHost
    }
    private val uposBackupHostPair by lazy {
        if (isLocatedCn) {
            when (uposHost) {
                hwUposHost -> Pair(aliUposHost, cosUposHost)
                aliUposHost -> Pair(hwUposHost, cosUposHost)
                // cosUposHost -> Pair(aliUposHost, hwUposHost)
                // akamaiUposHost -> Pair(aliUposHost, hwUposHost)
                else -> Pair(aliUposHost, hwUposHost)
            }
        } else {
            when (uposHost) {
                // akamaiUposHost -> Pair(hkBcacheUposHost, aliovUposHost)
                aliovUposHost -> Pair(hkBcacheUposHost, hwovUposHost)
                // cosovUposHost -> Pair(hkBcacheUposHost, aliovUposHost)
                hwovUposHost -> Pair(hwUposHost, cosUposHost)
                hkBcacheUposHost -> Pair(aliovUposHost, hwovUposHost)
                else -> Pair(hkBcacheUposHost, aliovUposHost)
            }
        }
    }

    private val baseUrlCdnUposReplaceRegexPattern by lazy {
        val regexText = mapOf(
            "replace_pcdn_upos_base" to """(szbdyd\.com)""",
            "replace_mcdn_upos_base" to """(.*\.mcdn\.bilivideo\.(com|cn|net))""",
            "replace_bcache_cdn_upos_base" to """(^(https?://)cn-.*\.bilivideo\.(com|cn|net))""",
            "replace_mirror_cdn_upos_base" to """(^(https?://)upos-(\w.*)-mirror.*\.(bilivideo|akamaized)\.(com|cn|net))""",
        ).filterKeys {
            sPrefs.getBoolean(it, false)
        }.values.joinToString("|").ifEmpty {
            sPrefs.edit().putBoolean("replace_pcdn_upos_base", true).apply()
            """(szbdyd\.com)"""
        }
        Regex(regexText)
    }
    private val backupUrlCdnUposReplaceRegexPattern by lazy {
        val regexText = mapOf(
            "replace_pcdn_upos_backup" to """(szbdyd\.com)""",
            "replace_mcdn_upos_backup" to """(.*\.mcdn\.bilivideo\.(com|cn|net))""",
            "replace_bcache_cdn_upos_backup" to """(^(https?://)cn-.*\.bilivideo\.(com|cn|net))""",
            "replace_mirror_cdn_upos_backup" to """(^(https?://)upos-(\w.*)-mirror.*\.(bilivideo|akamaized)\.(com|cn|net))""",
        ).filterKeys {
            sPrefs.getBoolean(it, false)
        }.values.joinToString("|").ifEmpty {
            sPrefs.edit().putBoolean("replace_pcdn_upos_backup", true).apply()
            """(szbdyd\.com)"""
        }
        Regex(regexText)
    }
    private val bStarPCdnUposReplaceRegexPattern by lazy {
        Regex("""^(https?://)(((\d{1,2})|(1\d{2})|(2[0-4]\d)|(25[0-5]))\.){3}((\d{1,2})|(1\d{2})|(2[0-4]\d)|(25[0-5]))""")
    }
    private val replaceUrlBwParamPattern by lazy { Regex("""(bw=[^&]*)""") }

    private fun replaceUpos(
        baseUrl: String, backupUrls: List<String>
    ): Pair<String, List<String>> {
        val backupUrlReplaced: (String, String) -> String = { url, newUpos ->
            if (replaceAllBackupUpos || url.contains(backupUrlCdnUposReplaceRegexPattern)) {
                url.replaceUpos(newUpos).replaceBw()
            } else url.replaceBw()
        }
        val newBaseUrl =
            if (replaceAllBaseUpos || baseUrl.contains(baseUrlCdnUposReplaceRegexPattern)) {
                baseUrl.replaceUpos(uposHost).replaceBw()
            } else baseUrl.replaceBw()
        val newBackupUrls = when (backupUrls.size) {
            2 -> listOf(
                backupUrlReplaced(backupUrls[0], uposBackupHostPair.first),
                backupUrlReplaced(backupUrls[1], uposBackupHostPair.second)
            )
            else -> listOf(
                backupUrlReplaced(baseUrl, uposBackupHostPair.first),
                backupUrlReplaced(baseUrl, uposBackupHostPair.second)
            )
        }
        return Pair(newBaseUrl, newBackupUrls)
    }

    private fun String.replaceUpos(newUpos: String): String {
        val uri = Uri.parse(this)
        return uri.buildUpon().authority(uri.getQueryParameter("xy_usource") ?: newUpos).build()
            .toString()
    }

    private fun String.replaceBw(): String {
        return if (replaceUposBw) this.replace(replaceUrlBwParamPattern, "bw=1280000") else this
    }

    fun replaceBStarAIpUpos(baseUrl: String, backupUrls: List<String>): String {
        return if (baseUrl.contains(bStarPCdnUposReplaceRegexPattern) && backupUrls.isNotEmpty()) {
            backupUrls.first()
        } else baseUrl
    }

    fun Any.replaceRawVodInfoUpos(): Any? {
        val serializedVodInfo = this.callMethodAs<ByteArray>("toByteArray")
        val newSerializedVodInfo =
            VodInfo.parseFrom(serializedVodInfo).copy { reconstructVodUpos() }.toByteArray()
        return this.javaClass.callStaticMethodOrNull("parseFrom", newSerializedVodInfo)
    }

    fun Any.replaceRawPlayDubbingInfoUpos(): Any? {
        val serializedPlayDubbingInfo = this.callMethodAs<ByteArray>("toByteArray")
        val newSerializedPlayDubbingInfo = PlayDubbingInfo.parseFrom(serializedPlayDubbingInfo)
            .copy { reconstructPlayDubbingInfoUpos() }.toByteArray()
        return this.javaClass.callStaticMethodOrNull("parseFrom", newSerializedPlayDubbingInfo)
    }

    fun ListenPlayInfoKt.Dsl.reconstructListenPlayInfoUpos() {
        if (hasPlayUrl()) {
            playUrl = playUrl.copy {
                val newDUrl = durl.map { listenResponseUrl ->
                    listenResponseUrl.copy {
                        if (hasUrl()) {
                            val newPair = replaceUpos(url, backupUrl)
                            backupUrl.clear()
                            backupUrl.addAll(newPair.second)
                            url = newPair.first
                        }
                    }
                }
                durl.clear()
                durl.addAll(newDUrl)
            }
        } else if (hasPlayDash()) {
            playDash = playDash.copy {
                val newAudio = audio.map { dashItem ->
                    dashItem.copy { reconstructDashItemUpos() }
                }
                audio.clear()
                audio.addAll(newAudio)
            }
        }
    }

    private fun VodInfo.reconstructVodUpos() = vodInfo {
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
        // segment video not support Dolby or LossLess
        if (hasDolby()) {
            dolby = dolby.copy {
                val newAudio = audio.map { dashItem ->
                    dashItem.copy { reconstructDashItemUpos() }
                }
                audio.clear()
                audio.addAll(newAudio)
            }
        }
        if (hasLossLessItem()) {
            lossLessItem = lossLessItem.copy {
                audio = audio.copy { reconstructDashItemUpos() }
            }
        }
    }

    private fun VodInfoKt.Dsl.reconstructVodUpos() {
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
        // segment video not support Dolby or LossLess
        if (hasDolby()) {
            dolby = dolby.copy {
                val newAudio = audio.map { dashItem ->
                    dashItem.copy { reconstructDashItemUpos() }
                }
                audio.clear()
                audio.addAll(newAudio)
            }
        }
        if (hasLossLessItem()) {
            lossLessItem = lossLessItem.copy {
                audio = audio.copy { reconstructDashItemUpos() }
            }
        }
    }

    private fun StreamKt.Dsl.reconstructStreamUpos() {
        if (hasDashVideo()) {
            dashVideo = dashVideo.copy {
                if (!hasBaseUrl()) return@copy
                val newPair = replaceUpos(baseUrl, backupUrl)
                backupUrl.clear()
                backupUrl.addAll(newPair.second)
                baseUrl = newPair.first
            }
        } else if (hasSegmentVideo()) {
            segmentVideo = segmentVideo.copy {
                val newSegment = segment.map { responseUrl ->
                    responseUrl.copy {
                        val newPair = replaceUpos(url, backupUrl)
                        backupUrl.clear()
                        backupUrl.addAll(newPair.second)
                        url = newPair.first
                    }
                }
                segment.clear()
                segment.addAll(newSegment)
            }
        }
    }

    private fun DashItemKt.Dsl.reconstructDashItemUpos() {
        if (!hasBaseUrl()) return
        val newPair = replaceUpos(baseUrl, backupUrl)
        backupUrl.clear()
        backupUrl.addAll(newPair.second)
        baseUrl = newPair.first
    }

    private fun ListenDashItemKt.Dsl.reconstructDashItemUpos() {
        if (!hasBaseUrl()) return
        val newPair = replaceUpos(baseUrl, backupUrl)
        backupUrl.clear()
        backupUrl.addAll(newPair.second)
        baseUrl = newPair.first
    }

    private fun PlayDubbingInfoKt.Dsl.reconstructPlayDubbingInfoUpos() {
        if (!hasBackgroundAudio() || roleAudioList.isEmpty()) return
        backgroundAudio = backgroundAudio.copy { reconstructAudioMaterialUpos() }
        roleAudioList.map { roleAudio ->
            roleAudio.copy {
                val newAudioMaterialList = this.audioMaterialList.map { audioMaterial ->
                    audioMaterial.copy { reconstructAudioMaterialUpos() }
                }
                this.audioMaterialList.clear()
                this.audioMaterialList.addAll(newAudioMaterialList)
            }
        }.apply {
            roleAudioList.clear()
            roleAudioList.addAll(this)
        }
    }

    private fun AudioMaterialProtoKt.Dsl.reconstructAudioMaterialUpos() {
        audio.map { dashItem ->
            dashItem.copy { reconstructDashItemUpos() }
        }.apply {
            audio.clear()
            audio.addAll(this)
        }
    }
}
