package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class ProtoBufHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private val mainListReplyClass by Weak { "com.bapis.bilibili.main.community.reply.v1.MainListReply" from mClassLoader }
    private val emptyPageClass by Weak { "com.bapis.bilibili.main.community.reply.v1.EmptyPage" from mClassLoader }
    private val textClass by Weak { "com.bapis.bilibili.main.community.reply.v1.EmptyPage\$Text" from mClassLoader }
    private val textStyleClass by Weak { "com.bapis.bilibili.main.community.reply.v1.TextStyle" from mClassLoader }

    override fun startHook() {
        val hidden = sPrefs.getBoolean("hidden", false)
        val purifyCity = sPrefs.getBoolean("purify_city", false)
        val removeHonor = sPrefs.getBoolean("remove_video_honor", false)
        val removeUgcSeason = sPrefs.getBoolean("remove_video_UgcSeason", false)
        val removeCmdDms = sPrefs.getBoolean("remove_video_cmd_dms", false)
        val purifySearch = sPrefs.getBoolean("purify_search", false)
        val purifyCampus = sPrefs.getBoolean("purify_campus", false)
        val blockWordSearch = sPrefs.getBoolean("block_word_search", false)
        val blockModules = sPrefs.getBoolean("block_modules", false)
        val blockUpperRecommendAd = sPrefs.getBoolean("block_upper_recommend_ad", false)
        val disableMainPageStory = sPrefs.getBoolean("disable_main_page_story", false)
        val unlockPlayLimit = sPrefs.getBoolean("play_arc_conf", false)
        val blockCommentGuide = sPrefs.getBoolean("block_comment_guide", false)
        val blockVideoComment = sPrefs.getBoolean("block_video_comment", false)

        if (hidden && (purifyCity || purifyCampus)) {
            listOf(
                "com.bapis.bilibili.app.dynamic.v1.DynTabReply",
                "com.bapis.bilibili.app.dynamic.v2.DynTabReply"
            ).forEach { clazz ->
                clazz.hookAfterMethod(
                    mClassLoader,
                    "getDynTabList"
                ) { param ->
                    param.result = (param.result as List<*>).filterNot {
                        purifyCity && it?.callMethodAs<Long>("getCityId") != 0L
                                || purifyCampus && it?.callMethodAs<String>("getAnchor") == "campus"
                    }
                }
            }
        }

        instance.viewMossClass?.hookAfterMethod("view", instance.viewReqClass) { param ->
            param.result ?: return@hookAfterMethod
            val aid = param.result.callMethod("getArc")
                ?.callMethodAs("getAid") ?: -1L
            val like = param.result.callMethod("getReqUser")
                ?.callMethodAs("getLike") ?: -1
            AutoLikeHook.detail = aid to like
            BangumiPlayUrlHook.qnApplied.set(false)
            if (unlockPlayLimit)
                param.result.callMethod("getConfig")
                    ?.callMethod("setShowListenButton", true)
            if (blockCommentGuide) {
                param.result.runCatchingOrNull {
                    callMethod("getLikeCustom")
                        ?.callMethod("clearLikeComment")
                    callMethod("getReplyPreface")
                        ?.callMethod("clearBadgeType")
                }
            }
            if (hidden && removeHonor) {
                param.result.callMethod("clearHonor")
            }
            if (hidden && removeUgcSeason) {
                param.result.callMethod("clearUgcSeason")
            }
        }

        if (hidden && removeCmdDms) {
            instance.viewMossClass?.hookAfterMethod(
                "viewProgress",
                "com.bapis.bilibili.app.view.v1.ViewProgressReq"
            ) { it.result?.callMethod("clearVideoGuide") }
            "com.bapis.bilibili.app.viewunite.v1.ViewMoss".from(mClassLoader)?.hookAfterMethod(
                "viewProgress",
                "com.bapis.bilibili.app.viewunite.v1.ViewProgressReq"
            ) { param ->
                param.result?.run {
                    callMethod("clearDm")
                    callMethod("getVideoGuide")?.callMethod("clearContractCard")
                }
            }
            instance.viewMossClass?.replaceMethod(
                "tFInfo", "com.bapis.bilibili.app.view.v1.TFInfoReq"
            ) { null }
            instance.dmMossClass?.hookAfterMethod(
                "dmView", "com.bapis.bilibili.community.service.dm.v1.DmViewReq",
            ) {
                it.result?.run {
                    callMethod("clearActivityMeta")
                    runCatchingOrNull {
                        callMethod("clearCommand")
                    }
                    setObjectField("unknownFields", instance.unknownFieldSetLiteInstance)
                }
            }
        }
        if (hidden && purifySearch) {
            "com.bapis.bilibili.app.interfaces.v1.SearchMoss".hookAfterMethod(
                mClassLoader,
                "defaultWords",
                "com.bapis.bilibili.app.interfaces.v1.DefaultWordsReq"
            ) { param ->
                param.result = null
            }
        }
        if (hidden && blockWordSearch) {
            "com.bapis.bilibili.main.community.reply.v1.Content".hookAfterMethod(
                mClassLoader,
                "internalGetUrls"
            ) { param ->
                (param.result as LinkedHashMap<*, *>?)?.let { m ->
                    val iterator = m.iterator()
                    while (iterator.hasNext()) {
                        iterator.next().value.callMethodAs<String?>("getAppUrlSchema")
                            ?.takeIf {
                                it.startsWith("bilibili://search?from=appcommentline_search")
                            }?.run {
                                iterator.remove()
                            }
                    }
                }
            }
        }
        if (hidden && blockModules) {
            "com.bapis.bilibili.app.resource.v1.ModuleMoss".hookAfterMethod(
                mClassLoader,
                "list",
                "com.bapis.bilibili.app.resource.v1.ListReq"
            ) {
                it.result?.callMethod("clearPools")
            }
        }
        if (hidden && blockUpperRecommendAd) {
            "com.bapis.bilibili.ad.v1.SourceContentDto".from(mClassLoader)
                ?.replaceMethod("getAdContent") { null }
        }
        if (disableMainPageStory) {
            "com.bapis.bilibili.app.distribution.setting.experimental.MultipleTusConfig"
                .from(mClassLoader)?.hookAfterMethod("getTopLeft") { param ->
                    param.result?.runCatchingOrNull {
                        callMethod("clearBadge")
                        callMethod("clearListenBackgroundImage")
                        callMethod("clearListenForegroundImage")
                        callMethod("clearStoryBackgroundImage")
                        callMethod("clearStoryForegroundImage")
                        val tabUrl = "bilibili://root?tab_name=我的"
                        callMethod("getUrl")?.callMethod("setValue", tabUrl)
                        callMethod("getUrlV2")?.callMethod("setValue", tabUrl)
                        callMethod("getGoto")?.callMethod("setValue", "1")
                        callMethod("getGotoV2")?.callMethod("setValue", 1)
                    }
                }
        }
        if (blockCommentGuide || (hidden && blockVideoComment)) {
            "com.bapis.bilibili.main.community.reply.v1.ReplyMoss".hookBeforeMethod(
                mClassLoader,
                "mainList",
                "com.bapis.bilibili.main.community.reply.v1.MainListReq",
                instance.mossResponseHandlerClass
            ) { param ->
                val type = param.args[0].callMethodAs<Long>("getType")
                if (hidden && blockVideoComment && type == 1L) {
                    val handler = param.args[1]
                    val replay = mainListReplyClass?.new()?.apply {
                        val subjectControl = callMethod("getSubjectControl")
                        val emptyPage = emptyPageClass?.new()?.also {
                            subjectControl?.callMethod("setEmptyPage", it)
                        }
                        emptyPage?.callMethod(
                            "setImageUrl",
                            "https://i0.hdslb.com/bfs/app-res/android/img_holder_forbid_style1.webp"
                        )
                        textClass?.new()?.apply {
                            callMethod("setRaw", "评论区已由漫游屏蔽")
                            textStyleClass?.new()?.apply {
                                callMethod("setFontSize", 14)
                                callMethod("setTextDayColor", "#FF61666D")
                                callMethod("setTextNightColor", "#FFA2A7AE")
                            }?.let {
                                callMethod("setStyle", it)
                            }
                        }?.let {
                            emptyPage?.callMethod("addTexts", it)
                        }
                    }
                    handler.callMethod("onNext", replay)
                    param.result = null
                    return@hookBeforeMethod
                }
                if (!blockCommentGuide)
                    return@hookBeforeMethod
                param.args[1] = param.args[1].mossResponseHandlerProxy { reply ->
                    reply?.runCatchingOrNull {
                        callMethod("getSubjectControl")?.run {
                            callMethod("clearEmptyBackgroundTextPlain")
                            callMethod("clearEmptyBackgroundTextHighlight")
                            callMethod("clearEmptyBackgroundUri")
                            callMethod("getEmptyPage")?.let { page ->
                                page.callMethod("clearLeftButton")
                                page.callMethod("clearRightButton")
                                page.callMethodAs<List<Any>>("getTextsList")
                                    .takeIf { it.size > 1 }?.let {
                                        page.callMethod("clearTexts")
                                        page.callMethod("addTexts", it.first().apply {
                                            callMethod("setRaw", "还没有评论哦")
                                        })
                                    }
                            }
                        }
                    }
                }
            }
        }
    }
}
