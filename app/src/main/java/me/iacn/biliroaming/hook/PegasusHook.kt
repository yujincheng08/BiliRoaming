package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class PegasusHook(classLoader: ClassLoader) : BaseHook(classLoader) {
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

    private val filterMap = mapOf(
        "advertisement" to listOf("ad"),
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
    }

    private fun String.isNum() = all { it.isDigit() }

    private fun String.toLong() = runCatchingOrNull {
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
        text.toLong().let {
            return if (it == -1L) false
            else it < hideLowPlayCountLimit
        }
    }

    // 屏蔽指定播放时长
    private fun durationVideo(obj: Any): Boolean {
        if (hideLongDurationLimit == 0 && hideShortDurationLimit == 0)
            return false
        val duration = obj.getObjectField("playerArgs")
            ?.getObjectFieldAs("fakeDuration") ?: 0
        if (hideLongDurationLimit != 0 && duration > hideLongDurationLimit) return true
        if (hideShortDurationLimit != 0 && duration < hideShortDurationLimit) return true
        return false
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
            setObjectField("subtitle", "(本地屏蔽，重启生效)")
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
        trySetLongField("autoRefreshTimeByActive", -1L)
        trySetLongField("autoRefreshTimeByAppear", -1L)
        trySetIntField("autoRefreshTimeByBehavior", -1)
        // only exist on android now
        trySetIntField("autoRefreshByBehavior", -1)
        // only exist on android_hd now
        trySetIntField("auto_refresh_time", 0)
    }

    override fun startHook() {
        Log.d("startHook: Pegasus")
        instance.pegasusFeedClass?.hookAfterMethod(
            instance.pegasusFeed(),
            instance.responseBodyClass
        ) { param ->
            param.result ?: return@hookAfterMethod
            val data = param.result.getObjectField("data")
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
            data?.getObjectField("config")?.disableAutoRefresh()
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
    }
}
