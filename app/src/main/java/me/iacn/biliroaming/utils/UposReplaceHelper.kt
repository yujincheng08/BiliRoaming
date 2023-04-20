package me.iacn.biliroaming.utils

import android.net.Uri
import me.iacn.biliroaming.*

@Suppress("UNUSED")
object UposReplaceHelper {
    private val hwUposHost by lazy { XposedInit.moduleRes.getString(R.string.hw_host) }
    private val replaceAllBaseUpos = sPrefs.getBoolean("replace_all_upos_base", false)
    private val replaceAllBackupUpos = sPrefs.getBoolean("replace_all_upos_backup", false)

    private val uposHost by lazy {
        sPrefs.getString("upos_host", null) ?: hwUposHost
    }

    private val baseUrlCdnUposReplaceRegexPattern by lazy {
        val regexText = mapOf(
            "replace_pcdn_upos_base" to """(szbdyd\.com)""",
            "replace_mcdn_upos_base" to """(.*\.mcdn\.bilivideo\.(com|cn|net))""",
            "replace_bcache_cdn_upos_base" to """(^(https?://)?cn-.*\.bilivideo\.(com|cn|net))""",
            "replace_mirror_cdn_upos_base" to """(^(https?://)?upos-(sz|hz)-mirror.*\.(bilivideo|akamaized)\.(com|cn|net))""",
        ).filterKeys {
            sPrefs.getBoolean(it, false)
        }.values.joinToString("|")
        Regex(regexText)
    }
    private val backupUrlCdnUposReplaceRegexPattern by lazy {
        val regexText = mapOf(
            "replace_pcdn_upos_backup" to """(szbdyd\.com)""",
            "replace_mcdn_upos_backup" to """(.*\.mcdn\.bilivideo\.(com|cn|net))""",
            "replace_bcache_cdn_upos_backup" to """(^(https?://)?cn-.*\.bilivideo\.(com|cn|net))""",
            "replace_mirror_cdn_upos_backup" to """(^(https?://)?upos-(sz|hz)-mirror.*\.(bilivideo|akamaized)\.(com|cn|net))""",
        ).filterKeys {
            sPrefs.getBoolean(it, false)
        }.values.joinToString("|")
        Regex(regexText)
    }

    private fun String.replaceBaseUrlUpos(): String {
        return if (replaceAllBaseUpos || this.contains(baseUrlCdnUposReplaceRegexPattern)) {
            this.replaceUpos(uposHost)
        } else this
    }

    private fun List<String>.replaceBackupUrlsUpos(): List<String> {
        return this.map { backupUrl ->
            if (replaceAllBackupUpos || backupUrl.contains(backupUrlCdnUposReplaceRegexPattern)) {
                backupUrl.replaceUpos(uposHost)
            } else backupUrl
        }
    }

    private fun String.replaceUpos(newUpos: String): String {
        val uri = Uri.parse(this)
        return uri.buildUpon().authority(uri.getQueryParameter("xy_usource") ?: newUpos).build()
            .toString()
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
                            url = url.replaceBaseUrlUpos()
                            val newBackupUrl = backupUrl.replaceBackupUrlsUpos()
                            backupUrl.clear()
                            backupUrl.addAll(newBackupUrl)
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
                baseUrl = baseUrl.replaceBaseUrlUpos()
                val newBackupUrl = backupUrl.replaceBackupUrlsUpos()
                backupUrl.clear()
                backupUrl.addAll(newBackupUrl)
            }
        } else if (hasSegmentVideo()) {
            segmentVideo = segmentVideo.copy {
                val newSegment = segment.map { responseUrl ->
                    responseUrl.copy {
                        url = url.replaceBaseUrlUpos()
                        val newBackupUrl = backupUrl.replaceBackupUrlsUpos()
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
        baseUrl = baseUrl.replaceBaseUrlUpos()
        val newBackupUrl = backupUrl.replaceBackupUrlsUpos()
        backupUrl.clear()
        backupUrl.addAll(newBackupUrl)
    }

    private fun ListenDashItemKt.Dsl.reconstructDashItemUpos() {
        if (!hasBaseUrl()) return
        baseUrl = baseUrl.replaceBaseUrlUpos()
        val newBackupUrl = backupUrl.replaceBackupUrlsUpos()
        backupUrl.clear()
        backupUrl.addAll(newBackupUrl)
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
