package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.UposReplaceHelper.enableLivePcdnBlock
import me.iacn.biliroaming.utils.UposReplaceHelper.enablePcdnBlock
import me.iacn.biliroaming.utils.UposReplaceHelper.enableUposReplace
import me.iacn.biliroaming.utils.UposReplaceHelper.forceUpos
import me.iacn.biliroaming.utils.UposReplaceHelper.gotchaRegex
import me.iacn.biliroaming.utils.UposReplaceHelper.initVideoUposList
import me.iacn.biliroaming.utils.UposReplaceHelper.isNeedReplaceVideoUpos
import me.iacn.biliroaming.utils.UposReplaceHelper.isPCdnUpos
import me.iacn.biliroaming.utils.UposReplaceHelper.liveUpos
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
        if (!enableUposReplace || !(forceUpos || enablePcdnBlock || enableLivePcdnBlock)) return
        Log.d("startHook: UposReplaceHook")
        "tv.danmaku.ijk.media.player.IjkMediaAsset\$MediaAssertSegment\$Builder".from(mClassLoader)
            ?.run {
                hookBeforeConstructor(String::class.java, Int::class.javaPrimitiveType) { param ->
                    val baseUrl = param.args[0] as String
                    if (baseUrl.contains("live-bvc")) {
                        if (enableLivePcdnBlock && !baseUrl.contains(gotchaRegex)) {
                            param.args[0] = baseUrl.replaceUpos(liveUpos)
                        }
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
                        (param.args[0] as List<String>).filter { !it.isPCdnUpos() }
                            .takeIf { backupUrls ->
                                backupUrls.isEmpty() || !backupUrls.any { it.contains("live-bvc") }
                            } ?: return@hookBeforeMethod
                    }
                    reconstructBackupUposList(
                        baseUrl, backupUrls, mediaAssertSegment
                    ).takeIf { it.isNotEmpty() }?.let {
                        param.args[0] = it
                    }
                }
            }
    }

    override fun lateInitHook() {
        initVideoUposList()
    }

    private fun reconstructBackupUposList(
        baseUrl: String, backupUrls: List<String>, mediaAssertSegment: Any?
    ): List<String> {
        val rawUrl = backupUrls.firstOrNull() ?: baseUrl
        return if (baseUrl.isPCdnUpos()) {
            if (backupUrls.isNotEmpty()) {
                mediaAssertSegment?.setObjectField("url", rawUrl.replaceUpos())
                listOf(rawUrl.replaceUpos(videoUposBackups[0]), baseUrl)
            } else emptyList()
        } else {
            listOf(rawUrl.replaceUpos(videoUposBackups[0]), rawUrl.replaceUpos(videoUposBackups[1]))
        }
    }
}
