package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.VodServerReplaceHelper.initVideoVodServer
import me.iacn.biliroaming.utils.VodServerReplaceHelper.ipPCdnRegex
import me.iacn.biliroaming.utils.VodServerReplaceHelper.isNeedReplaceVideoVodServer
import me.iacn.biliroaming.utils.VodServerReplaceHelper.reconstructBackupVideoVodServer
import me.iacn.biliroaming.utils.VodServerReplaceHelper.replaceLiveVodServer
import me.iacn.biliroaming.utils.VodServerReplaceHelper.replaceVideoVodServer
import me.iacn.biliroaming.utils.from
import me.iacn.biliroaming.utils.getObjectFieldOrNull
import me.iacn.biliroaming.utils.getObjectFieldOrNullAs
import me.iacn.biliroaming.utils.hookBeforeConstructor
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.sPrefs


class VodServerReplaceHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        private val forceVideoVod = sPrefs.getBoolean("force_video_vod", false)
        private val replaceVideo = sPrefs.getBoolean("block_pcdn", false)
        private val replaceLive = sPrefs.getBoolean("block_pcdn_live", false)
        private val gotchaRegex by lazy { Regex("""https?://\w*--\w*-gotcha\d*\.bilivideo""") }
    }

    override fun startHook() {
        if (!(forceVideoVod || replaceVideo || replaceLive)) return
        Log.d("startHook: VodServerReplaceHook")
        "tv.danmaku.ijk.media.player.IjkMediaAsset\$MediaAssertSegment\$Builder".from(mClassLoader)
            ?.run {
                hookBeforeConstructor(String::class.java, Int::class.javaPrimitiveType) { param ->
                    val baseUrl = param.args[0] as String
                    if (baseUrl.contains("live-bvc")) {
                        if (!replaceLive || baseUrl.contains(gotchaRegex)) return@hookBeforeConstructor
                        param.args[0] = baseUrl.replaceLiveVodServer()
                    } else if (baseUrl.contains(ipPCdnRegex)) {
                        // IP:Port type PCDN currently only exists in Live and Thai Video.
                        return@hookBeforeConstructor
                    } else if (baseUrl.isNeedReplaceVideoVodServer()) {
                        param.args[0] = baseUrl.replaceVideoVodServer()
                    }
                }

                if (!(replaceVideo || forceVideoVod)) return@run
                hookBeforeMethod("setBackupUrls", MutableCollection::class.java) { param ->
                    val mediaAssertSegment = param.thisObject.getObjectFieldOrNull("target")
                    val baseUrl =
                        mediaAssertSegment?.getObjectFieldOrNullAs<String>("url").orEmpty()
                    if (baseUrl.isEmpty()) return@hookBeforeMethod
                    val backupUrls = if (param.args[0] == null) {
                        if (baseUrl.contains("live-bvc")) return@hookBeforeMethod else {
                            emptyList<String>()
                        }
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        // Cannot simply replace IP:Port type PCDN's host
                        (param.args[0] as List<String>).filter { !it.contains(ipPCdnRegex) }
                            .takeIf { backupUrls ->
                                backupUrls.isEmpty() || !backupUrls.any { it.contains("live-bvc") }
                            } ?: return@hookBeforeMethod
                    }
                    param.args[0] =
                        reconstructBackupVideoVodServer(baseUrl, backupUrls, mediaAssertSegment)
                }
            }
    }

    override fun lateInitHook() {
        initVideoVodServer()
    }
}
