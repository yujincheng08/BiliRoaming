package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.from
import me.iacn.biliroaming.utils.getObjectFieldOrNull
import me.iacn.biliroaming.utils.getObjectFieldOrNullAs
import me.iacn.biliroaming.utils.hookBeforeConstructor
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.sPrefs
import me.iacn.biliroaming.utils.UposReplaceHelper.initVideoUposList
import me.iacn.biliroaming.utils.UposReplaceHelper.ipPCdnRegex
import me.iacn.biliroaming.utils.UposReplaceHelper.isNeedReplaceVideoUpos
import me.iacn.biliroaming.utils.UposReplaceHelper.reconstructBackupUposList
import me.iacn.biliroaming.utils.UposReplaceHelper.replaceLiveUpos
import me.iacn.biliroaming.utils.UposReplaceHelper.replaceVideoUpos


class UposReplaceHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        private val forceUpos = sPrefs.getBoolean("force_upos", false)
        private val replaceVideo = sPrefs.getBoolean("block_pcdn", false)
        private val replaceLive = sPrefs.getBoolean("block_pcdn_live", false)
        private val gotchaRegex by lazy { Regex("""https?://\w*--\w*-gotcha\d*\.bilivideo""") }
    }

    override fun startHook() {
        if (!(forceUpos || replaceVideo || replaceLive)) return
        Log.d("startHook: UposReplaceHook")
        "tv.danmaku.ijk.media.player.IjkMediaAsset\$MediaAssertSegment\$Builder".from(mClassLoader)
            ?.run {
                hookBeforeConstructor(String::class.java, Int::class.javaPrimitiveType) { param ->
                    val baseUrl = param.args[0] as String
                    if (baseUrl.contains("live-bvc")) {
                        if (!replaceLive || baseUrl.contains(gotchaRegex)) return@hookBeforeConstructor
                        param.args[0] = baseUrl.replaceLiveUpos()
                    } else if (baseUrl.contains(ipPCdnRegex)) {
                        // IP:Port type PCDN currently only exists in Live and Thai Video.
                        return@hookBeforeConstructor
                    } else if (baseUrl.isNeedReplaceVideoUpos()) {
                        param.args[0] = baseUrl.replaceVideoUpos()
                    }
                }

                if (!(replaceVideo || forceUpos)) return@run
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
                        reconstructBackupUposList(baseUrl, backupUrls, mediaAssertSegment)
                }
            }
    }

    override fun lateInitHook() {
        initVideoUposList()
    }
}
