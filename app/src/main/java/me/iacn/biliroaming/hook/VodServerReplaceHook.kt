package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.VodServerReplaceHelper.initVideoVodServer
import me.iacn.biliroaming.utils.VodServerReplaceHelper.ipPCdnReplaceRegex
import me.iacn.biliroaming.utils.VodServerReplaceHelper.isNeedReplaceVideoVodServer
import me.iacn.biliroaming.utils.VodServerReplaceHelper.reconstructBackupVideoVodServer
import me.iacn.biliroaming.utils.VodServerReplaceHelper.replaceBaseVideoVodServer
import me.iacn.biliroaming.utils.VodServerReplaceHelper.replaceLiveVodServer
import me.iacn.biliroaming.utils.from
import me.iacn.biliroaming.utils.getObjectFieldOrNull
import me.iacn.biliroaming.utils.getObjectFieldOrNullAs
import me.iacn.biliroaming.utils.hookAfterMethod
import me.iacn.biliroaming.utils.hookBeforeConstructor
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.sPrefs


class VodServerReplaceHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        private val enableVideoVodServerReplace = sPrefs.getBoolean("block_pcdn", false)
        private val enableLiveVodServerReplace = sPrefs.getBoolean("block_pcdn_live", false)
        private val liveVodServerGotchaRegex by lazy { Regex("""https?://\w*--\w*-gotcha\d*\.bilivideo""") }
    }

    override fun startHook() {
        if (!(enableVideoVodServerReplace || enableLiveVodServerReplace)) return
        Log.d("startHook: VodServerReplaceHook")
        "tv.danmaku.ijk.media.player.IjkMediaAsset\$MediaAssertSegment\$Builder".from(mClassLoader)
            ?.run {
                hookBeforeConstructor(String::class.java, Int::class.javaPrimitiveType) { param ->
                    val baseUrl = param.args[0] as String
                    if (baseUrl.contains("live-bvc")) {
                        if (!enableLiveVodServerReplace || baseUrl.contains(liveVodServerGotchaRegex)) return@hookBeforeConstructor
                        param.args[0] = baseUrl.replaceLiveVodServer()
                    } else if (baseUrl.contains(ipPCdnReplaceRegex)) {
                        // IP:Port type PCDN currently only exists in Live and Thai Video.
                        return@hookBeforeConstructor
                    } else if (baseUrl.isNeedReplaceVideoVodServer()) {
                        param.args[0] = baseUrl.replaceBaseVideoVodServer()
                    }
                }

                if (!enableVideoVodServerReplace) return@run
                hookBeforeMethod("setBackupUrls", MutableCollection::class.java) { param ->
                    val mediaAssertSegment by lazy {
                        param.thisObject.getObjectFieldOrNull("target")
                    }
                    val baseUrl by lazy {
                        mediaAssertSegment?.getObjectFieldOrNullAs<String>("url").orEmpty()
                    }
                    val backupUrls = if (param.args[0] == null) {
                        if (baseUrl.contains("live-bvc")) return@hookBeforeMethod else {
                            emptyList<String>()
                        }
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        // Cannot simply replace IP:Port type PCDN's host
                        (param.args[0] as List<String>).filter { !it.contains(ipPCdnReplaceRegex) }
                            .takeIf { backupUrls ->
                                backupUrls.isEmpty() || !backupUrls.any { it.contains("live-bvc") }
                            } ?: return@hookBeforeMethod
                    }
                    if (baseUrl.isEmpty()) return@hookBeforeMethod
                    param.args[0] =
                        reconstructBackupVideoVodServer(baseUrl, backupUrls, mediaAssertSegment)
                }
            }
    }

    override fun lateInitHook() {
        initVideoVodServer()
    }
}
