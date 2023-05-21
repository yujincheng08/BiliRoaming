package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.UposReplaceHelper.enableLivePcdnBlock
import me.iacn.biliroaming.utils.UposReplaceHelper.enablePcdnBlock
import me.iacn.biliroaming.utils.UposReplaceHelper.forceUpos
import me.iacn.biliroaming.utils.UposReplaceHelper.gotchaRegex
import me.iacn.biliroaming.utils.UposReplaceHelper.initVideoUposList
import me.iacn.biliroaming.utils.UposReplaceHelper.ipPCdnRegex
import me.iacn.biliroaming.utils.UposReplaceHelper.isNeedReplaceVideoUpos
import me.iacn.biliroaming.utils.UposReplaceHelper.liveUpos
import me.iacn.biliroaming.utils.UposReplaceHelper.reconstructVideoUpos
import me.iacn.biliroaming.utils.UposReplaceHelper.replaceUpos
import me.iacn.biliroaming.utils.UposReplaceHelper.videoUposBackups
import me.iacn.biliroaming.utils.from
import me.iacn.biliroaming.utils.getObjectFieldOrNull
import me.iacn.biliroaming.utils.getObjectFieldOrNullAs
import me.iacn.biliroaming.utils.hookBeforeConstructor
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.setObjectField


class UposReplaceHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!(forceUpos || enablePcdnBlock || enableLivePcdnBlock)) return
        Log.d("startHook: UposReplaceHook")
        "tv.danmaku.ijk.media.player.IjkMediaAsset\$MediaAssertSegment\$Builder".from(mClassLoader)
            ?.run {
                hookBeforeConstructor(String::class.java, Int::class.javaPrimitiveType) { param ->
                    val baseUrl = param.args[0] as String
                    if (baseUrl.contains("live-bvc")) {
                        if (enableLivePcdnBlock && baseUrl.contains(gotchaRegex)) {
                            param.args[0] = baseUrl.replaceUpos(liveUpos)
                        }
                    } else if (baseUrl.contains(ipPCdnRegex)) {
                        // IP:Port type PCDN currently only exists in Live and Thai Video.
                    } else if (baseUrl.isNeedReplaceVideoUpos()) {
                        param.args[0] = baseUrl.replaceUpos()
                    }
                }

                if (!(enablePcdnBlock || forceUpos)) return@run
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

    private fun reconstructBackupUposList(
        baseUrl: String, backupUrls: List<String>, mediaAssertSegment: Any?
    ) = mutableListOf(
        backupUrls.getOrNull(0)?.reconstructVideoUpos(videoUposBackups[0]) ?: baseUrl.replaceUpos(
            videoUposBackups[0], true
        ),
        backupUrls.getOrNull(1)?.reconstructVideoUpos(videoUposBackups[1]) ?: baseUrl.replaceUpos(
            videoUposBackups[1], true
        ),
    ).apply {
        if (baseUrl.contains(ipPCdnRegex)) {
            mediaAssertSegment?.setObjectField(
                "url", backupUrls.first().replaceUpos()
            )
            this[0] = baseUrl
        }
    }
}
