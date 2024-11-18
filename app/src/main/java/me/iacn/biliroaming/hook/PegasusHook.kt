package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class PegasusHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val hidden = sPrefs.getBoolean("hidden", false)

    private val filterSet = sPrefs.getStringSet("customize_home_recommend", emptySet()).orEmpty()

    private val hideLowPlayCountLimit = sPrefs.getLong("hide_low_play_count_recommend_limit", 0)
    private val hideShortDurationLimit = sPrefs.getInt("hide_short_duration_recommend_limit", 0)
    private val hideLongDurationLimit = sPrefs.getInt("hide_long_duration_recommend_limit", 0)
    private val kwdFilterTitleList by lazy {
        migrateHomeFilterPrefsIfNeeded()
        sPrefs.getStringSet("home_filter_keywords_title", null).orEmpty()
    }
    private val kwdFilterTitleRegexes by lazy { kwdFilterTitleList.map { it.toRegex() } }
    private val kwdFilterTitleRegexMode = sPrefs.getBoolean("home_filter_title_regex_mode", false)
    private val kwdFilterReasonList by lazy {
        migrateHomeFilterPrefsIfNeeded()
        sPrefs.getStringSet("home_filter_keywords_reason", null).orEmpty()
    }
    private val kwdFilterReasonRegexes by lazy { kwdFilterReasonList.map { it.toRegex() } }
    private val kwdFilterReasonRegexMode = sPrefs.getBoolean("home_filter_reason_regex_mode", false)
    private val kwdFilterUidList by lazy {
        migrateHomeFilterPrefsIfNeeded()
        sPrefs.getStringSet("home_filter_keywords_uid", null)
            ?.mapNotNull { it.toLongOrNull() }.orEmpty()
    }
    private val kwdFilterUpnameList by lazy {
        migrateHomeFilterPrefsIfNeeded()
        sPrefs.getStringSet("home_filter_keywords_up", null).orEmpty()
    }
    private val kwdFilterUpnameRegexes by lazy { kwdFilterUpnameList.map { it.toRegex() } }
    private val kwdFilterUpnameRegexMode = sPrefs.getBoolean("home_filter_up_regex_mode", false)
    private val kwdFilterRnameList by lazy {
        migrateHomeFilterPrefsIfNeeded()
        sPrefs.getStringSet("home_filter_keywords_category", null).orEmpty()
    }
    private val kwdFilterTnameList by lazy {
        migrateHomeFilterPrefsIfNeeded()
        sPrefs.getStringSet("home_filter_keywords_channel", null).orEmpty()
    }
    private val disableAutoRefresh = sPrefs.getBoolean("disable_auto_refresh", false)
    private val removeRelatePromote = sPrefs.getBoolean("remove_video_relate_promote", false)
    private val removeRelateOnlyAv = sPrefs.getBoolean("remove_video_relate_only_av", false)
    private val removeRelateNothing = sPrefs.getBoolean("remove_video_relate_nothing", false)
    private val applyToRelate = sPrefs.getBoolean("home_filter_apply_to_relate", false)

    private val hideTopEntrance = sPrefs.getBoolean("hide_top_entrance_popular", false)
    private val hideSuggestFollow = sPrefs.getBoolean("hide_suggest_follow_popular", false)

    private val filterMap = mapOf(
        "advertisement" to listOf("ad", "cm", "cm_v2"),
        "article" to listOf("article"),
        "bangumi" to listOf("bangumi", "special", "pgc"),
        "game" to listOf("game"),
        "picture" to listOf("picture"),
        "vertical" to listOf("vertical", "story"),
        "banner" to listOf("banner"),
        "live" to listOf("live"),
        "inline" to listOf("inline"),
        "notify" to listOf("notify_tunnel"),
        "large_cover" to listOf("large_cover"),
        "middle_cover" to listOf("middle_cover"),
        "small_cover" to listOf("small_cover"),
    )

    private val filter = filterSet.flatMap {
        filterMap[it].orEmpty()
    }

    val handleCoverRatio = sPrefs.getString("pegasus_cover_ratio", "0")?.toFloat() ?: 0f

    companion object {
        private const val REASON_ID_TITLE = 1145140L
        private const val REASON_ID_RCMD_REASON = 1145141L
        private const val REASON_ID_UP_ID = 1145142L
        private const val REASON_ID_UP_NAME = 1145143L
        private const val REASON_ID_CATEGORY_NAME = 1145144L
        private const val REASON_ID_CHANNEL_NAME = 1145145L
        private val blockReasonIds = arrayOf(
            REASON_ID_TITLE,
            REASON_ID_RCMD_REASON,
            REASON_ID_UP_ID,
            REASON_ID_UP_NAME,
            REASON_ID_CATEGORY_NAME,
            REASON_ID_CHANNEL_NAME,
        )

        private var popularDataVersion = ""
        private var popularDataCount = 0L
    }

    private fun String.isNum() = isNotEmpty() && all { it.isDigit() }

    private fun String.toPlayCount() = runCatchingOrNull {
        when {
            isNum() -> toDouble().toLong()
            contains("万") -> (replace("万", "").toDouble() * 10_000).toLong()
            contains("亿") -> (replace("亿", "").toDouble() * 100_000_000).toLong()
            else -> -1L
        }
    } ?: -1L

    // 屏蔽过低的播放数
    private fun isLowCountVideo(obj: Any): Boolean {
        if (hideLowPlayCountLimit == 0L) return false
        val text = obj.runCatchingOrNull {
            getObjectFieldAs<String?>("coverLeftText1")
        }.orEmpty()
        return text.toPlayCount().let {
            if (it == -1L) false
            else it < hideLowPlayCountLimit
        }
    }
    private fun isLowCountVideoUnite(count: Long): Boolean {
        if (hideLowPlayCountLimit == 0L) return false
        return count < hideLowPlayCountLimit
    }

    // 屏蔽指定播放时长
    private fun durationVideo(obj: Any): Boolean {
        if (hideLongDurationLimit == 0 && hideShortDurationLimit == 0)
            return false
        val duration = obj.getObjectField("playerArgs")
            ?.getObjectFieldAs("fakeDuration") ?: 0
        if (hideLongDurationLimit != 0 && duration > hideLongDurationLimit)
            return true
        return hideShortDurationLimit != 0 && duration < hideShortDurationLimit
    }
    private fun durationVideoUnite(duration: Long): Boolean {
        if (hideLongDurationLimit == 0 && hideShortDurationLimit == 0)
            return false
        if (hideLongDurationLimit != 0 && duration > hideLongDurationLimit)
            return true
        return hideShortDurationLimit != 0 && duration < hideShortDurationLimit
    }

    private fun isContainsBlockKwd(obj: Any): Boolean {
        // 屏蔽标题关键词
        if (kwdFilterTitleList.isNotEmpty()) {
            val title = obj.getObjectFieldAs<String?>("title").orEmpty()
            if (kwdFilterTitleRegexMode && title.isNotEmpty()) {
                if (kwdFilterTitleRegexes.any { title.contains(it) })
                    return true
            } else if (title.isNotEmpty()) {
                if (kwdFilterTitleList.any { title.contains(it) })
                    return true
            }
        }

        // 屏蔽UID
        if (kwdFilterUidList.isNotEmpty()) {
            val uid = obj.getObjectField("args")?.getLongField("upId") ?: 0L
            if (uid != 0L && kwdFilterUidList.any { it == uid })
                return true
        }

        // 屏蔽UP主
        if (kwdFilterUpnameList.isNotEmpty()) {
            val upname = if (obj.getObjectField("goTo") == "picture") {
                obj.runCatchingOrNull { getObjectFieldAs<String?>("desc") }.orEmpty()
            } else {
                obj.getObjectField("args")?.getObjectFieldAs<String?>("upName").orEmpty()
            }
            if (kwdFilterUpnameRegexMode && upname.isNotEmpty()) {
                if (kwdFilterUpnameRegexes.any { upname.contains(it) })
                    return true
            } else if (upname.isNotEmpty()) {
                if (kwdFilterUpnameList.any { upname.contains(it) })
                    return true
            }
        }

        // 屏蔽分区
        if (kwdFilterRnameList.isNotEmpty()) {
            val rname = obj.getObjectField("args")
                ?.getObjectFieldAs<String?>("rname").orEmpty()
            if (rname.isNotEmpty() && kwdFilterRnameList.any { rname.contains(it) })
                return true
        }

        // 屏蔽频道
        if (kwdFilterTnameList.isNotEmpty()) {
            val tname = obj.getObjectField("args")
                ?.getObjectFieldAs<String?>("tname").orEmpty()
            if (tname.isNotEmpty() && kwdFilterTnameList.any { tname.contains(it) })
                return true
        }

        // 屏蔽推荐关键词（可能不存在，必须放最后）
        if (kwdFilterReasonList.isNotEmpty()) {
            val reason = obj.runCatchingOrNull {
                getObjectField("rcmdReason")?.getObjectFieldAs<String?>("text").orEmpty()
            }.orEmpty()
            if (kwdFilterReasonRegexMode && reason.isNotEmpty()) {
                if (kwdFilterReasonRegexes.any { reason.contains(it) })
                    return true
            } else if (reason.isNotEmpty()) {
                if (kwdFilterReasonList.any { reason.contains(it) })
                    return true
            }
        }

        return false
    }

    private fun isContainsBlockKwdUnite(card: Any): Boolean {
        if (card.callMethodAs("hasBasicInfo")) {
            card.callMethodAs<Any>("getBasicInfo").let { basicInfo ->
                // 屏蔽标题关键词
                if (kwdFilterTitleList.isNotEmpty()) {
                    val title = basicInfo.callMethodAs<String>("getTitle")
                    if (kwdFilterTitleRegexMode && title.isNotEmpty()) {
                        if (kwdFilterTitleRegexes.any { title.contains(it) })
                            return true
                    } else if (title.isNotEmpty()) {
                        if (kwdFilterTitleList.any { title.contains(it) })
                            return true
                    }
                }

                basicInfo.callMethodAs<Any>("getAuthor").let { author ->
                    // 屏蔽UID
                    if (kwdFilterUidList.isNotEmpty()) {
                        val uid = author.callMethodAs<Long>("getMid")
                        if (uid != 0L && kwdFilterUidList.any { it == uid })
                            return true
                    }

                    // 屏蔽UP主
                    if (kwdFilterUpnameList.isNotEmpty()) {
                        val upName = author.callMethodAs<String>("getTitle")
                        if (kwdFilterUpnameRegexMode && upName.isNotEmpty()) {
                            if (kwdFilterUpnameRegexes.any { upName.contains(it) })
                                return true
                        } else if (upName.isNotEmpty()) {
                            if (kwdFilterUpnameList.any { upName.contains(it) })
                                return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun ArrayList<Any>.appendReasons() = forEach { item ->
        val title = item.getObjectFieldAs<String?>("title").orEmpty()
        val rcmdReason = item.runCatchingOrNull {
            getObjectField("rcmdReason")?.getObjectFieldAs<String?>("text")
        }.orEmpty()
        val args = item.getObjectField("args")
        val upId = args?.getLongField("upId") ?: 0L
        val upName = if (item.getObjectField("goTo") == "picture") {
            item.runCatchingOrNull { getObjectFieldAs<String?>("desc") }.orEmpty()
        } else {
            args?.getObjectFieldAs<String?>("upName").orEmpty()
        }
        val categoryName = args?.getObjectFieldAs<String?>("rname").orEmpty()
        val channelName = args?.getObjectFieldAs<String?>("tname").orEmpty()
        val treePoint = item.getObjectFieldAs<MutableList<Any>?>("threePoint")
        val reasons = mutableListOf<Any>()
        instance.treePointItemClass?.new()?.apply {
            setObjectField("title", "漫游屏蔽")
            setObjectField("subtitle", "(本地屏蔽，重启生效，可前往首页推送过滤器查看)")
            setObjectField("type", "dislike")
            if (title.isNotEmpty()) {
                instance.dislikeReasonClass?.new()?.apply {
                    setLongField("id", REASON_ID_TITLE)
                    setObjectField("name", "标题:$title")
                }?.let { reasons.add(it) }
            }
            if (rcmdReason.isNotEmpty()) {
                instance.dislikeReasonClass?.new()?.apply {
                    setLongField("id", REASON_ID_RCMD_REASON)
                    setObjectField("name", "推荐原因:$rcmdReason")
                }?.let { reasons.add(it) }
            }
            if (upId != 0L) {
                instance.dislikeReasonClass?.new()?.apply {
                    setLongField("id", REASON_ID_UP_ID)
                    setObjectField("name", "UID:$upId")
                }?.let { reasons.add(it) }
            }
            if (upName.isNotEmpty()) {
                instance.dislikeReasonClass?.new()?.apply {
                    setLongField("id", REASON_ID_UP_NAME)
                    setObjectField("name", "UP主:$upName")
                }?.let { reasons.add(it) }
            }
            if (categoryName.isNotEmpty()) {
                instance.dislikeReasonClass?.new()?.apply {
                    setLongField("id", REASON_ID_CATEGORY_NAME)
                    setObjectField("name", "分区:$categoryName")
                }?.let { reasons.add(it) }
            }
            if (channelName.isNotEmpty()) {
                instance.dislikeReasonClass?.new()?.apply {
                    setLongField("id", REASON_ID_CHANNEL_NAME)
                    setObjectField("name", "频道:$channelName")
                }?.let { reasons.add(it) }
            }
            setObjectField("reasons", reasons)
        }?.let {
            if (treePoint != null && reasons.isNotEmpty()) {
                treePoint.add(it)
            } else if (reasons.isNotEmpty()) {
                item.setObjectField("threePoint", mutableListOf(it))
            }
        }
    }

    private fun Any.disableAutoRefresh() {
        if (!disableAutoRefresh) return
        // only exist on android and android_i now
        runCatchingOrNull {
            setLongField("autoRefreshTimeByActive", -1L)
            setLongField("autoRefreshTimeByAppear", -1L)
            setIntField("autoRefreshTimeByBehavior", -1)
        }
        // only exist on android now
        runCatchingOrNull {
            setIntField("autoRefreshByBehavior", -1)
        }
        // only exist on android_hd now
        runCatchingOrNull {
            setIntField("auto_refresh_time", 0)
        }
    }

    private fun Any.customSmallCoverWhRatio() {
        if (handleCoverRatio == 0f) return
        runCatchingOrNull {
            setFloatField("smallCoverWhRatio", handleCoverRatio)
        }
    }

    private fun isPromoteRelate(item: Any) = removeRelatePromote
            && (item.callMethod("getFromSourceType") == 2L ||
            item.callMethod("getGoto") == "cm")

    private fun isNotAvRelate(item: Any) = removeRelatePromote && removeRelateOnlyAv
            && item.callMethod("getGoto") != "av"

    private fun isLowCountRelate(item: Any): Boolean {
        if (hideLowPlayCountLimit == 0L) return false
        val text = item.callMethod("getStatV2")?.callMethod("getViewVt")
            ?.callMethodAs<String>("getText").orEmpty()
        return text.toPlayCount().let {
            if (it == -1L) false
            else it < hideLowPlayCountLimit
        }
    }

    private fun isDurationInvalidRelate(item: Any): Boolean {
        if (hideLongDurationLimit == 0 && hideShortDurationLimit == 0)
            return false
        val duration = item.callMethodAs("getDuration") ?: 0L
        if (hideLongDurationLimit != 0 && duration > hideLongDurationLimit)
            return true
        return hideShortDurationLimit != 0 && duration < hideShortDurationLimit
    }

    private fun isContainsBlockKwdRelate(item: Any): Boolean {
        if (kwdFilterTitleList.isNotEmpty()) {
            val title = item.callMethodAs<String>("getTitle")
            if (kwdFilterTitleRegexMode && title.isNotEmpty()) {
                if (kwdFilterTitleRegexes.any { title.contains(it) })
                    return true
            } else if (title.isNotEmpty()) {
                if (kwdFilterTitleList.any { title.contains(it) })
                    return true
            }
        }
        if (kwdFilterUidList.isNotEmpty()) {
            val uid = item.callMethod("getAuthor")
                ?.callMethodAs("getMid") ?: 0L
            if (uid != 0L && kwdFilterUidList.any { it == uid })
                return true
        }
        if (kwdFilterUpnameList.isNotEmpty()) {
            val upname = item.callMethod("getAuthor")
                ?.callMethodAs<String>("getName").orEmpty()
            if (kwdFilterUpnameRegexMode && upname.isNotEmpty()) {
                if (kwdFilterUpnameRegexes.any { upname.contains(it) })
                    return true
            } else if (upname.isNotEmpty()) {
                if (kwdFilterUpnameList.any { upname.contains(it) })
                    return true
            }
        }
        if (kwdFilterReasonList.isNotEmpty()) {
            val reason = item.callMethod("getRcmdReasonStyle")
                ?.callMethodAs<String>("getText").orEmpty()
            if (kwdFilterReasonRegexMode && reason.isNotEmpty()) {
                if (kwdFilterReasonRegexes.any { reason.contains(it) })
                    return true
            } else if (reason.isNotEmpty()) {
                if (kwdFilterReasonList.any { reason.contains(it) })
                    return true
            }
        }
        return false
    }

    private fun isLowCountVideoPopular(obj: Any): Boolean {
        if (hideLowPlayCountLimit == 0L) return false
        val rightDesc2 = obj.callMethodAs<String>("getRightDesc2")

        val text = rightDesc2.split(' ').first().removeSuffix("观看")
        return text.toPlayCount().let {
            if (it == -1L) false
            else it < hideLowPlayCountLimit
        }
    }

    private fun durationVideoPopular(obj: Any): Boolean {
        fun getTimeInSeconds(time: String): Long {
            val parts = time.split(":").map { it.toInt() }

            val seconds: Long = when (parts.size) {
                2 -> parts[0] * 60L + parts[1]
                3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
                else -> Long.MAX_VALUE
            }

            return seconds
        }

        if (hideLongDurationLimit == 0 && hideShortDurationLimit == 0)
            return false
        val text = obj.callMethodAs<String>("getCoverRightText1")
        val duration = getTimeInSeconds(text)

        if (hideLongDurationLimit != 0 && duration > hideLongDurationLimit)
            return true
        return hideShortDurationLimit != 0 && duration < hideShortDurationLimit
    }

    private fun isContainsBlockKwdPopular(obj: Any, base: Any?): Boolean {
        base?: return false

        val threePointV4 = base.callMethod("getThreePointV4")
        val sharePlane = threePointV4?.callMethod("getSharePlane")

        if (kwdFilterTitleList.isNotEmpty()) {
            val title = base.callMethodAs<String>("getTitle")
            if (kwdFilterTitleRegexMode && title.isNotEmpty()) {
                if (kwdFilterTitleRegexes.any { title.contains(it) })
                    return true
            } else if (title.isNotEmpty()) {
                if (kwdFilterTitleList.any { title.contains(it) }) {
                    return true
                }
            }
        }

        if (kwdFilterUidList.isNotEmpty()) {
            val uid = sharePlane?.callMethodAs<Long>("getAuthorId") ?: 0
            if (uid != 0L && kwdFilterUidList.any { it == uid })
                return true
        }

        if (kwdFilterUpnameList.isNotEmpty()) {
            val upname = obj.callMethodAs<String>("getRightDesc1")
            if (kwdFilterUpnameRegexMode && upname.isNotEmpty()) {
                if (kwdFilterUpnameRegexes.any { upname.contains(it) })
                    return true
            } else if (upname.isNotEmpty()) {
                if (kwdFilterUpnameList.any { upname.contains(it) })
                    return true
            }
        }

        if (kwdFilterReasonList.isNotEmpty()) {
            val reasonStyle = obj.callMethod("getRcmdReasonStyle")
            val reasonText = reasonStyle?.callMethodAs<String>("getText")

            do {
                if (reasonText.isNullOrEmpty()) {
                    break
                }
                if (kwdFilterReasonRegexMode && kwdFilterReasonRegexes.any { reasonText.contains(it) }) {
                    return true
                } else if (kwdFilterReasonList.any { reasonText.contains(it) }) {
                    return true
                }
            } while (false)
        }

        return false
    }
    private fun cardV5Handle(obj: Any?): Boolean {
        obj ?: return false

        val base = obj.callMethod("getBase")
        if (popularDataCount % 10 == 0L) {
            popularDataVersion = base?.callMethodAs<String>("getParam") ?: popularDataVersion
        }

        return isLowCountVideoPopular(obj) || durationVideoPopular(obj) || isContainsBlockKwdPopular(obj, base)
    }

    override fun startHook() {
        Log.d("startHook: Pegasus")
        instance.pegasusFeedClass?.hookAfterMethod(
            instance.pegasusFeed(),
            instance.responseBodyClass
        ) { param ->
            param.result ?: return@hookAfterMethod
            val data = param.result.getObjectField("data")
            data?.getObjectField("config")?.apply {
                disableAutoRefresh()
                customSmallCoverWhRatio()
            }
            if (!hidden) return@hookAfterMethod
            data?.getObjectFieldAs<ArrayList<Any>>("items")?.run {
                removeAll {
                    val cardGoto = it.getObjectFieldAs<String?>("cardGoto").orEmpty()
                    val cardType = it.getObjectFieldAs<String?>("cardType").orEmpty()
                    val goto = it.getObjectFieldAs<String?>("goTo").orEmpty()
                    filter.any { item ->
                        item in cardGoto || item in cardType || item in goto
                    } || isLowCountVideo(it) || isContainsBlockKwd(it) || durationVideo(it)
                }
                appendReasons()
            }
        }
        if (!hidden) return
        fun MutableList<Any>.filter() = removeAll {
            isPromoteRelate(it) || isNotAvRelate(it) || (applyToRelate && (isLowCountRelate(it)
                    || isDurationInvalidRelate(it) || isContainsBlockKwdRelate(it)))
        }

        fun MutableList<Any>.filterUnite() = removeAll {
            val allowTypeList = mutableListOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            var shouldFiltered = false
            allowTypeList.removeAll { digit ->
                (removeRelateOnlyAv && digit != 1) || (removeRelatePromote && digit in listOf(
                    3, // Resource, like mall
                    4, // GAME
                    5, // CM
                    10 // SPECIAL
                ))
            }
            // av filter
            if (applyToRelate) {
                if (it.callMethodAs("hasAv")) {
                    it.callMethodAs<Any>("getAv").let { av ->
                        val duration = av.callMethodAs<Long>("getDuration")
                        if (durationVideoUnite(duration)) {
                            shouldFiltered = true
                            return@let
                        }
                        if (av.callMethodAs("hasStat")) {
                            av.callMethodAs<Any>("getStat").let { stat ->
                                if (stat.callMethodAs("hasVt")) {
                                    stat.callMethodAs<Any>("getVt").let { vt ->
                                        if (isLowCountVideoUnite(vt.callMethodAs<Long>("getValue"))) {
                                            shouldFiltered = true
                                            return@let
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (isContainsBlockKwdUnite(it)) {
                        shouldFiltered = true
                    }
                }
                // todo: support rcmd
            }
            removeRelateNothing || it.callMethodAs("getRelateCardTypeValue") !in allowTypeList || shouldFiltered
        }

        instance.viewMossClass?.hookAfterMethod(
            if (instance.useNewMossFunc) "executeView" else "view",
            instance.viewReqClass
        ) { param ->
            param.result ?: return@hookAfterMethod
            if (removeRelatePromote && removeRelateOnlyAv && removeRelateNothing) {
                param.result.callMethod("clearRelates")
                param.result.callMethod("clearPagination")
                return@hookAfterMethod
            }
            param.result.callMethod("ensureRelatesIsMutable")
            param.result.callMethodAs<MutableList<Any>>("getRelatesList").filter()
        }
        instance.viewMossClass?.hookAfterMethod(
            if (instance.useNewMossFunc) "executeRelatesFeed" else "relatesFeed",
            "com.bapis.bilibili.app.view.v1.RelatesFeedReq"
        ) { param ->
            param.result ?: return@hookAfterMethod
            param.result.callMethod("ensureListIsMutable")
            param.result.callMethodAs<MutableList<Any>>("getListList").filter()
        }

        instance.viewUniteMossClass?.run {
            hookAfterMethod(
                if (instance.useNewMossFunc) "executeView" else "view",
                instance.viewUniteReqClass
            ) { param ->
                param.result ?: return@hookAfterMethod
                param.result.callMethod("getTab")?.run {
                    callMethod("ensureTabModuleIsMutable")
                    callMethodAs<MutableList<Any>>("getTabModuleList").map { originalTabModules ->
                        if (!originalTabModules.callMethodAs<Boolean>("hasIntroduction")) return@map
                        originalTabModules.callMethodAs<Any>("getIntroduction").run {
                            callMethod("ensureModulesIsMutable")
                            callMethodAs<MutableList<Any>>("getModulesList").map { module ->
                                if (!module.callMethodAs<Boolean>("hasRelates")) return@map
                                module.callMethodAs<Any>("getRelates").run {
                                    callMethod("ensureCardsIsMutable")
                                    callMethodAs<MutableList<Any>>("getCardsList").filterUnite()
                                }
                            }
                        }
                    }
                }
            }
            hookAfterMethod(
                if (instance.useNewMossFunc) "executeRelatesFeed" else "relatesFeed",
                "com.bapis.bilibili.app.viewunite.v1.RelatesFeedReq"
            ) { param ->
                param.result?.run {
                    callMethod("ensureRelatesIsMutable")
                    callMethodAs<MutableList<Any>>("getRelatesList").filterUnite()
                }
            }
        }

        instance.cardClickProcessorClass?.declaredMethods
            ?.find { it.name == instance.onFeedClicked() }?.hookBeforeMethod { param ->
                val reason = param.args[2]
                if (reason == null || reason.getLongField("id") !in blockReasonIds)
                    return@hookBeforeMethod
                val id = reason.getLongField("id")
                val name = reason.getObjectFieldAs<String?>("name").orEmpty()
                val value = name.substringAfter(":")
                when (id) {
                    REASON_ID_TITLE -> {
                        val validValue =
                            if (kwdFilterTitleRegexMode) Regex.escape(value) else value
                        sPrefs.appendStringForSet("home_filter_keywords_title", validValue)
                    }

                    REASON_ID_RCMD_REASON -> {
                        val validValue =
                            if (kwdFilterReasonRegexMode) Regex.escape(value) else value
                        sPrefs.appendStringForSet("home_filter_keywords_reason", validValue)
                    }

                    REASON_ID_UP_ID -> {
                        sPrefs.appendStringForSet("home_filter_keywords_uid", value)
                    }

                    REASON_ID_UP_NAME -> {
                        val validValue =
                            if (kwdFilterUpnameRegexMode) Regex.escape(value) else value
                        sPrefs.appendStringForSet("home_filter_keywords_up", validValue)
                    }

                    REASON_ID_CATEGORY_NAME -> {
                        sPrefs.appendStringForSet("home_filter_keywords_category", value)
                    }

                    REASON_ID_CHANNEL_NAME -> {
                        sPrefs.appendStringForSet("home_filter_keywords_channel", value)
                    }
                }
                Log.toast("添加成功", force = true)
                param.result = null
            }



        fun MutableList<Any>.filterPopular() = removeIf {
            when (it.callMethod("getItemCase")?.toString()) {
                "SMALL_COVER_V5" -> {
                    val v5 = it.callMethod("getSmallCoverV5")
                    popularDataCount++
                    return@removeIf cardV5Handle(v5)
                }
                "POPULAR_TOP_ENTRANCE" -> {
                    return@removeIf hideTopEntrance
                }
                "RCMD_ONE_ITEM" -> {
                    popularDataCount++
                    return@removeIf hideSuggestFollow
                }
                else -> {
                    popularDataCount++
                    return@removeIf false
                }
            }
        }

        instance.popularClass?.hookBeforeMethod(
            if (instance.useNewMossFunc) "executeIndex" else "index",
            "com.bapis.bilibili.app.show.popular.v1.PopularResultReq"
        ) { param ->
            param.args ?: return@hookBeforeMethod

            val idx = param.args[0].getLongFieldOrNull("idx_")
            if (idx == null || idx == 0L) {
                popularDataCount = 0
                popularDataVersion = ""
                return@hookBeforeMethod
            }

            param.args[0].setObjectField("lastParam_", popularDataVersion)
            param.args[0].setLongField("idx_", popularDataCount)
        }
        instance.popularClass?.hookAfterMethod(
            if (instance.useNewMossFunc) "executeIndex" else "index",
            "com.bapis.bilibili.app.show.popular.v1.PopularResultReq"
        ) { param ->
            param.result ?: return@hookAfterMethod

            param.result.callMethod("ensureItemsIsMutable")
            param.result.callMethodAs<MutableList<Any>>("getItemsList").filterPopular()
        }
    }
}
