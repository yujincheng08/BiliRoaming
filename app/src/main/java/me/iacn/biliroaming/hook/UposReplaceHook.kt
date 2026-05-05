package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.UposReplaceHelper.enableLivePcdnBlock
import me.iacn.biliroaming.utils.UposReplaceHelper.enablePcdnBlock
import me.iacn.biliroaming.utils.UposReplaceHelper.enableUposReplace
import me.iacn.biliroaming.utils.UposReplaceHelper.forceUpos
import me.iacn.biliroaming.utils.UposReplaceHelper.gotchaRegex
import me.iacn.biliroaming.utils.UposReplaceHelper.initVideoUposList
import me.iacn.biliroaming.utils.UposReplaceHelper.isNeedReplaceVideoUpos
import me.iacn.biliroaming.utils.UposReplaceHelper.isOverseaUpos
import me.iacn.biliroaming.utils.UposReplaceHelper.isPCdnUpos
import me.iacn.biliroaming.utils.UposReplaceHelper.liveUpos
import me.iacn.biliroaming.utils.UposReplaceHelper.replaceUpos
import me.iacn.biliroaming.utils.UposReplaceHelper.videoUposBackups
import me.iacn.biliroaming.utils.from
import me.iacn.biliroaming.utils.getObjectFieldOrNull
import me.iacn.biliroaming.utils.getObjectFieldOrNullAs
import me.iacn.biliroaming.utils.hookConstructor
import me.iacn.biliroaming.utils.hookMethod
import me.iacn.biliroaming.utils.setObjectField


class UposReplaceHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!enableUposReplace || !(forceUpos || enablePcdnBlock || enableLivePcdnBlock)) return
        Log.d("startHook: UposReplaceHook")
        "tv.danmaku.ijk.media.player.IjkMediaAsset\$MediaAssertSegment\$Builder".from(mClassLoader)
            ?.run {
                hookConstructor(String::class.java, Int::class.javaPrimitiveType) { chain ->
                    val baseUrl = chain.args[0] as String
                    val args = chain.args.toTypedArray()
                    if (baseUrl.contains("live-bvc")) {
                        if (enableLivePcdnBlock && !baseUrl.contains(gotchaRegex)) {
                            args[0] = baseUrl.replaceUpos(liveUpos)
                        }
                    } else if (baseUrl.isNeedReplaceVideoUpos()) {
                        args[0] = baseUrl.replaceUpos()
                    }
                    chain.proceed(args)
                }

                if (!(enablePcdnBlock || forceUpos)) return@run
                hookMethod("setBackupUrls", MutableCollection::class.java) { chain ->
                    val mediaAssertSegment = chain.thisObject?.getObjectFieldOrNull("target")
                    val baseUrl =
                        mediaAssertSegment?.getObjectFieldOrNullAs<String>("url").orEmpty()
                    if (baseUrl.isEmpty()) return@hookMethod chain.proceed()
                    val backupUrls = if (chain.args[0] == null) {
                        if (baseUrl.contains("live-bvc")) return@hookMethod chain.proceed() else {
                            emptyList<String>()
                        }
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        (chain.args[0] as List<String>).filter { !it.isPCdnUpos() }
                            .takeIf { backupUrls ->
                                backupUrls.isEmpty() || !backupUrls.any { it.contains("live-bvc") }
                            } ?: return@hookMethod chain.proceed()
                    }
                    val newBackupUrls = reconstructBackupUposList(
                        baseUrl, backupUrls, mediaAssertSegment
                    ).takeIf { it.isNotEmpty() }
                    if (newBackupUrls != null) {
                        val args = chain.args.toTypedArray()
                        args[0] = newBackupUrls
                        chain.proceed(args)
                    } else {
                        chain.proceed()
                    }
                }
            }
    }

    override fun lateInitHook() {
        initVideoUposList(mClassLoader)
    }

    private fun reconstructBackupUposList(
        baseUrl: String, backupUrls: List<String>, mediaAssertSegment: Any?
    ): List<String> {
        val rawUrl = backupUrls.firstOrNull() ?: baseUrl
        return if (baseUrl.isPCdnUpos()) {
            if (backupUrls.isNotEmpty()) {
                mediaAssertSegment?.setObjectField("url", rawUrl.replaceUpos())
                listOf(rawUrl.replaceUpos(videoUposBackups[0], rawUrl.isOverseaUpos()), baseUrl)
            } else emptyList()
        } else {
            if (enablePcdnBlock || forceUpos || backupUrls.isEmpty() || rawUrl.isOverseaUpos()) {
                listOf(
                    rawUrl.replaceUpos(videoUposBackups[0]), rawUrl.replaceUpos(videoUposBackups[1])
                )
            } else emptyList()
        }
    }
}
