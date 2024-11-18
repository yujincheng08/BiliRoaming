package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class ProtoBufHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        fun Any.removeCmdDms() {
            callMethod("clearActivityMeta")
            runCatchingOrNull {
                callMethod("clearCommand")
            }
            setObjectField("unknownFields", instance.unknownFieldSetLiteInstance)
        }
    }

    private val mainListReplyClass by Weak { "com.bapis.bilibili.main.community.reply.v1.MainListReply" from mClassLoader }
    private val emptyPageV1Class by Weak { "com.bapis.bilibili.main.community.reply.v1.EmptyPage" from mClassLoader }
    private val textV1Class by Weak { "com.bapis.bilibili.main.community.reply.v1.EmptyPage\$Text" from mClassLoader }
    private val textStyleV1Class by Weak { "com.bapis.bilibili.main.community.reply.v1.TextStyle" from mClassLoader }
    private val textV2Class by Weak { "com.bapis.bilibili.main.community.reply.v2.EmptyPage\$Text" from mClassLoader }
    private val textStyleV2Class by Weak { "com.bapis.bilibili.main.community.reply.v2.TextStyle" from mClassLoader }
    private val videoGuideClass by Weak { "com.bapis.bilibili.app.view.v1.VideoGuide" from mClassLoader }

    override fun startHook() {
        val hidden = sPrefs.getBoolean("hidden", false)
        val blockLiveOrder = sPrefs.getBoolean("block_live_order", false)
        val purifyCity = sPrefs.getBoolean("purify_city", false)
        val removeHonor = sPrefs.getBoolean("remove_video_honor", false)
        val removeUgcSeason = sPrefs.getBoolean("remove_video_UgcSeason", false)
        val removeCmdDms = sPrefs.getBoolean("remove_video_cmd_dms", false)
        val removeUpVipLabel = sPrefs.getBoolean("remove_up_vip_label", false)
        val purifySearch = sPrefs.getBoolean("purify_search", false)
        val searchFilterUid = run {
            sPrefs.getStringSet("search_filter_keyword_uid", null)
                ?.mapNotNull { it.toLongOrNull() }.orEmpty()
        }
        val searchFilterContents = run {
            sPrefs.getStringSet("search_filter_keyword_content", null).orEmpty()
        }
        val searchFilterContentRegexes by lazy { searchFilterContents.map { it.toRegex() } }
        val searchFilterContentRegexMode = sPrefs.getBoolean("search_filter_content_regex_mode", false)
        val searchFilterUpNames = run {
            sPrefs.getStringSet("search_filter_keyword_upname", null).orEmpty()
        }
        val searchRemoveRelatePromote = sPrefs.getBoolean("search_filter_remove_relate_promote", false)
        val commentFilterAtUid = run {
            sPrefs.getStringSet("comment_filter_keyword_at_uid", null)
                ?.mapNotNull { it.toLongOrNull() }.orEmpty()
        }
        val commentFilterContents = run {
            sPrefs.getStringSet("comment_filter_keyword_content", null).orEmpty()
        }
        val commentFilterContentRegexes by lazy { commentFilterContents.map { it.toRegex() } }
        val commentFilterContentRegexMode = sPrefs.getBoolean("comment_filter_content_regex_mode", false)
        val commentFilterAtUpNames = run {
            sPrefs.getStringSet("comment_filter_keyword_at_upname", null).orEmpty()
        }
        val commentFilterBlockAtComment = sPrefs.getBoolean("comment_filter_block_at_comment", false)
        val targetCommentAuthorLevel = sPrefs.getLong("target_comment_author_level", 0L)
        val purifyCampus = sPrefs.getBoolean("purify_campus", false)
        val blockWordSearch = sPrefs.getBoolean("block_word_search", false)
        val blockModules = sPrefs.getBoolean("block_modules", false)
        val blockUpperRecommendAd = sPrefs.getBoolean("block_upper_recommend_ad", false)
        val disableMainPageStory = sPrefs.getBoolean("disable_main_page_story", false)
        val unlockPlayLimit = sPrefs.getBoolean("play_arc_conf", false)
        val blockCommentGuide = sPrefs.getBoolean("block_comment_guide", false)
        val blockVideoComment = sPrefs.getBoolean("block_video_comment", false)
        val blockViewPageAds = sPrefs.getBoolean("block_view_page_ads", false)

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

        instance.viewMossClass?.hookAfterMethod(
            if (instance.useNewMossFunc) "executeView" else "view",
            instance.viewReqClass
        ) { param ->
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
            if (hidden && blockLiveOrder) {
                param.result.callMethod("clearLiveOrderInfo")
            }
            if (hidden && removeUpVipLabel) {
                param.result.callMethod("getOwnerExt")?.callMethod("getVip")
                    ?.callMethod("clearLabel")
            }
        }

        if (hidden && removeCmdDms) {
            instance.viewMossClass?.hookAfterMethod(
                if (instance.useNewMossFunc) "executeViewProgress" else "viewProgress",
                "com.bapis.bilibili.app.view.v1.ViewProgressReq"
            ) { param ->
                param.result?.callMethod("setVideoGuide", videoGuideClass?.new())
            }
            instance.viewUniteMossClass?.hookAfterMethod(
                if (instance.useNewMossFunc) "executeViewProgress" else "viewProgress",
                "com.bapis.bilibili.app.viewunite.v1.ViewProgressReq"
            ) { param ->
                param.result?.run {
                    callMethod("clearDm")
                    callMethod("getVideoGuide")?.callMethod("clearContractCard")
                }
            }
            instance.viewMossClass?.replaceMethod(
                if (instance.useNewMossFunc) "executeTFInfo" else "tFInfo",
                "com.bapis.bilibili.app.view.v1.TFInfoReq"
            ) { null }
            instance.dmMossClass?.hookAfterMethod(
                if (instance.useNewMossFunc) "executeDmView" else "dmView",
                instance.dmViewReqClass,
            ) { it.result?.removeCmdDms() }
        }
        if (hidden && purifySearch) {
            "com.bapis.bilibili.app.interfaces.v1.SearchMoss".hookAfterMethod(
                mClassLoader,
                if (instance.useNewMossFunc) "executeDefaultWords" else "defaultWords",
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
                if (instance.useNewMossFunc) "executeList" else "list",
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
                if (instance.useNewMossFunc) "executeMainList" else "mainList",
                "com.bapis.bilibili.main.community.reply.v1.MainListReq",
            ) { param ->
                val type = param.args[0].callMethodAs<Long>("getType")
                if (hidden && blockVideoComment && type == 1L) {
                    val reply = mainListReplyClass?.new()?.apply {
                        val subjectControl = callMethod("getSubjectControl")
                        val emptyPage = emptyPageV1Class?.new()?.also {
                            subjectControl?.callMethod("setEmptyPage", it)
                        }
                        emptyPage?.callMethod(
                            "setImageUrl",
                            "https://i0.hdslb.com/bfs/app-res/android/img_holder_forbid_style1.webp"
                        )
                        textV1Class?.new()?.apply {
                            callMethod("setRaw", "评论区已由漫游屏蔽")
                            textStyleV1Class?.new()?.apply {
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
                    param.result = reply
                    return@hookBeforeMethod
                }
                if (!blockCommentGuide) return@hookBeforeMethod
                param.result.runCatchingOrNull {
                    callMethod("getSubjectControl")?.run {
                        callMethod("clearEmptyBackgroundTextPlain")
                        callMethod("clearEmptyBackgroundTextHighlight")
                        callMethod("clearEmptyBackgroundUri")
                        callMethod("getEmptyPage")?.let { page ->
                            page.callMethod("clearLeftButton")
                            page.callMethod("clearRightButton")
                            page.callMethodAs<List<Any>>("getTextsList").takeIf { it.size > 1 }
                                ?.let {
                                    page.callMethod("clearTexts")
                                    page.callMethod("addTexts", it.first().apply {
                                        callMethod("setRaw", "还没有评论哦")
                                    })
                                }
                        }
                    }
                }
            }
            "com.bapis.bilibili.main.community.reply.v2.ReplyMoss".from(mClassLoader)
                ?.hookBeforeMethod(
                    if (instance.useNewMossFunc) "executeSubjectDescription" else "subjectDescription",
                    "com.bapis.bilibili.main.community.reply.v2.SubjectDescriptionReq"
                ) { param ->
                    val defaultText = textV2Class?.new()?.apply {
                        val tipStr = if (hidden && blockVideoComment) {
                            "评论区已由漫游屏蔽"
                        } else {
                            "还没有评论哦"
                        }
                        callMethod("setRaw", tipStr)
                        textStyleV2Class?.new()?.apply {
                            callMethod("setFontSize", 14)
                            callMethod("setTextDayColor", "#FF61666D")
                            callMethod("setTextNightColor", "#FFA2A7AE")
                        }?.let {
                            callMethod("setStyle", it)
                        }
                    } ?: return@hookBeforeMethod
                    param.result.runCatchingOrNull {
                        callMethod("getEmptyPage")?.run {
                            callMethod("clearLeftButton")
                            callMethod("clearRightButton")
                            callMethod("ensureTextsIsMutable")
                            callMethodAs<MutableList<Any>>("getTextsList").run {
                                clear()
                                add(defaultText)
                            }
                            if (!(hidden && blockVideoComment)) return@run
                            callMethod(
                                "setImageUrl",
                                "https://i0.hdslb.com/bfs/app-res/android/img_holder_forbid_style1.webp"
                            )
                        }
                    }
                }
        }
        val needSearchFilter = hidden and (searchFilterContents.isNotEmpty() or searchFilterUid.isNotEmpty() or searchFilterUpNames.isNotEmpty()) or searchRemoveRelatePromote
        if (needSearchFilter) {
            instance.searchAllResponseClass?.hookAfterMethod("getItemList") { p ->
                val items = p.result as? List<Any?> ?: return@hookAfterMethod
                p.result = items.filter { item ->
                    val videoCard = item?.getObjectField("cardItem_") ?: return@filter true
                    if (instance.searchVideoCardClass?.isInstance(videoCard) == false) {
                        if (searchRemoveRelatePromote) {
                            if (item.callMethodAs<Boolean>("hasCm")) return@filter false
                            if (item.callMethodAs<Boolean>("hasSpecial")) return@filter false
                        }
                        return@filter true
                    }
                    if (videoCard.getLongField("mid_") in searchFilterUid) return@filter false
                    if (videoCard.getObjectFieldAs<String>("author_") in searchFilterUpNames) return@filter false
                    if (searchFilterContentRegexMode) {
                        if (searchFilterContentRegexes.any { it.containsMatchIn(videoCard.getObjectFieldAs<String>("title_")) })
                            return@filter false
                    } else {
                        if (searchFilterContents.any { videoCard.getObjectFieldAs<String>("title_").contains(it) }) return@filter false
                    }
                    true
                }
            }
        }

        val needCommentFilter =
            hidden && (commentFilterBlockAtComment || commentFilterContents.isNotEmpty() || commentFilterAtUid.isNotEmpty() || commentFilterAtUpNames.isNotEmpty() || targetCommentAuthorLevel != 0L)
        if (needCommentFilter) {
            val blockAtCommentSplitRegex = Regex("\\s+")

            fun Any.validCommentAuthorLevel(): Boolean {
                if (targetCommentAuthorLevel == 0L) return true
                val authorLevel = getObjectField("member_")?.getObjectFieldAs<Long>("level_") ?: 6L
                return authorLevel >= targetCommentAuthorLevel
            }

            fun Any.validCommentContent(): Boolean {
                val content = getObjectField("content_") ?: return true
                val commentMessage = content.getObjectFieldAs<String>("message_")

                val contentIsToBlock = commentFilterContents.isNotEmpty() && if (commentFilterContentRegexMode) {
                    commentFilterContentRegexes.any { commentMessage.contains(it) }
                } else {
                    commentFilterContents.any { commentMessage.contains(it) }
                }
                if (contentIsToBlock) return false

                if (commentFilterBlockAtComment && commentMessage.trim()
                        .split(blockAtCommentSplitRegex).all { it.startsWith("@") }
                ) return false

                if (commentFilterAtUpNames.isEmpty() && commentFilterAtUid.isEmpty()) return true
                val atNameToMid = content.getObjectFieldAs<Map<String, Long>>("atNameToMid_")
                return !(atNameToMid.keys.any { it in commentFilterAtUpNames } || atNameToMid.values.any { it in commentFilterAtUid })
            }

            fun Any.filterComment() = validCommentAuthorLevel() && validCommentContent()

            "com.bapis.bilibili.main.community.reply.v1.MainListReply".from(mClassLoader)
                ?.hookAfterMethod("getRepliesList") { p ->
                    val l = p.result as? List<*> ?: return@hookAfterMethod
                    p.result = l.filter { it?.filterComment() ?: true }
                }
            "com.bapis.bilibili.main.community.reply.v1.ReplyInfo".from(mClassLoader)
                ?.hookAfterMethod("getRepliesList") { p ->
                    val l = p.result as? List<*> ?: return@hookAfterMethod
                    p.result = l.filter { it?.filterComment() ?: true }
                }
        }

        if (!(hidden && (blockViewPageAds || removeHonor || blockVideoComment))) return
        // 视频详情页荣誉卡片
        fun Any.isHonor() = callMethodAs("hasHonor") && removeHonor

        // 视频详情页活动卡片(含会员购等), 区分于视频下方推荐处的广告卡片
        fun Any.isActivityEntranceModule() =
            callMethodAs("hasActivityEntranceModule") && blockViewPageAds

        // 视频详情页直播预约卡片
        fun Any.isLiveOrder() = callMethodAs("hasLiveOrder") && blockLiveOrder

        fun MutableList<Any>.filter() = removeAll {
            it.isActivityEntranceModule() || it.isHonor() || it.isLiveOrder()
        }

        instance.viewUniteMossClass?.hookAfterMethod(
            if (instance.useNewMossFunc) "executeView" else "view",
            instance.viewUniteReqClass
        ) { param ->
            if (instance.networkExceptionClass?.isInstance(param.throwable) == true) return@hookAfterMethod
            param.result ?: return@hookAfterMethod

            if (blockViewPageAds) {
                param.result.callMethod("clearCm")
            }

            param.result.callMethod("getTab")?.run {
                callMethod("ensureTabModuleIsMutable")
                val tabModuleList = callMethodAs<MutableList<Any>>("getTabModuleList")
                tabModuleList.removeAll {
                    blockVideoComment && it.callMethodAs("hasReply")
                }
                if (!(blockViewPageAds || removeHonor)) return@run
                tabModuleList.map {
                    if (!it.callMethodAs<Boolean>("hasIntroduction")) return@map
                    it.callMethodAs<Any>("getIntroduction").run {
                        callMethod("ensureModulesIsMutable")
                        callMethodAs<MutableList<Any>>("getModulesList").filter()
                    }
                }
            }
        }

        if (!sPrefs.getBoolean("replace_story_video", false)) return
        val disableBooleanValue = "com.bapis.bilibili.app.distribution.BoolValue".from(mClassLoader)?.callStaticMethod("getDefaultInstance") ?: return
        "com.bapis.bilibili.app.distribution.setting.play.PlayConfig".from(mClassLoader)?.run {
            replaceAllMethods("getLandscapeAutoStory") { disableBooleanValue }
            replaceAllMethods("getShouldAutoStory") { disableBooleanValue }
        }
    }
}
