package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class PegasusHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val filterSet = sPrefs.getStringSet("customize_home_recommend", emptySet()).orEmpty()

    private val hideLowPlayCountLimit by lazy {
        sPrefs.getLong("hide_low_play_count_recommend_limit", 0)
    }
    private val hideShortDurationLimit by lazy {
        sPrefs.getInt("hide_short_duration_recommend_limit", 0)
    }
    private val hideLongDurationLimit by lazy {
        sPrefs.getInt("hide_long_duration_recommend_limit", 0)
    }
    private val kwdFilterTitleList by lazy {
        migrateHomeFilterPrefsIfNeeded()
        sPrefs.getStringSet("home_filter_keywords_title", null).orEmpty()
    }
    private val kwdFilterTitleRegexes by lazy { kwdFilterTitleList.map { it.toRegex() } }
    private val kwdFilterTitleRegexMode by lazy {
        sPrefs.getBoolean("home_filter_title_regex_mode", false)
    }
    private val kwdFilterReasonList by lazy {
        migrateHomeFilterPrefsIfNeeded()
        sPrefs.getStringSet("home_filter_keywords_reason", null).orEmpty()
    }
    private val kwdFilterReasonRegexes by lazy { kwdFilterReasonList.map { it.toRegex() } }
    private val kwdFilterReasonRegexMode by lazy {
        sPrefs.getBoolean("home_filter_reason_regex_mode", false)
    }
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
    private val kwdFilterUpnameRegexMode by lazy {
        sPrefs.getBoolean("home_filter_up_regex_mode", false)
    }
    private val kwdFilterRnameList by lazy {
        migrateHomeFilterPrefsIfNeeded()
        sPrefs.getStringSet("home_filter_keywords_category", null).orEmpty()
    }
    private val kwdFilterTnameList by lazy {
        migrateHomeFilterPrefsIfNeeded()
        sPrefs.getStringSet("home_filter_keywords_channel", null).orEmpty()
    }

    private val filterMap = mapOf(
        "advertisement" to arrayListOf("ad"),
        "article" to arrayListOf("article"),
        "bangumi" to arrayListOf("bangumi", "special", "pgc"),
        "game" to arrayListOf("game"),
        "picture" to arrayListOf("picture"),
        "vertical" to arrayListOf("vertical", "story"),
        "banner" to arrayListOf("banner"),
        "live" to arrayListOf("live"),
        "inline" to arrayListOf("inline"),
        "notify" to arrayListOf("notify_tunnel"),
        "large_cover" to arrayListOf("large_cover"),
        "middle_cover" to arrayListOf("middle_cover"),
        "small_cover" to arrayListOf("small_cover"),
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

    private fun toLong(str: String): Long {
        return try {
            when {
                str.isNum() -> return str.toDouble().toLong()

                str.contains("万") ->
                    (str.replace("万", "").toDouble() * 10_000).toLong()

                str.contains("亿") ->
                    (str.replace("亿", "").toDouble() * 100_000_000).toLong()

                else -> -1L
            }
        } catch (e: Throwable) {
            -1
        }
    }

    // 屏蔽过低的播放数
    private fun isLowCountVideo(obj: Any): Boolean {
        if (hideLowPlayCountLimit == 0.toLong()) return false
        val text = try {
            obj.getObjectField("coverLeftText1")
        } catch (thr: Throwable) {
            return false
        }
        toLong(text.toString()).let {
            return if (it == -1L) false
            else it < hideLowPlayCountLimit
        }
    }

    // 屏蔽指定播放时长
    private fun durationVideo(obj: Any): Boolean {
        val duration = try {
            obj.getObjectField("playerArgs")?.getObjectFieldAs<Int?>("fakeDuration")!!.toInt()
        } catch (thr: Throwable) {
            return false
        }
        if (duration > hideLongDurationLimit && hideLongDurationLimit != 0) return true
        if (duration < hideShortDurationLimit && hideShortDurationLimit != 0) return true
        return false
    }

    private fun isContainsBlockKwd(obj: Any): Boolean {
        // 屏蔽标题关键词
        if (kwdFilterTitleList.isNotEmpty()) {
            val title = try {
                obj.getObjectField("title").toString()
            } catch (thr: Throwable) {
                return false
            }
            if (kwdFilterTitleRegexMode) {
                if (kwdFilterTitleRegexes.any { title.contains(it) })
                    return true
            } else {
                if (kwdFilterTitleList.any { title.contains(it) })
                    return true
            }
        }

        // 屏蔽UID
        if (kwdFilterUidList.isNotEmpty()) {
            val uid = try {
                obj.getObjectField("args")?.getObjectFieldAs("upId") ?: 0L
            } catch (thr: Throwable) {
                return false
            }
            kwdFilterUidList.forEach {
                if (it == uid) return true
            }
        }

        // 屏蔽UP主
        if (kwdFilterUpnameList.isNotEmpty()) {
            val upname = try {
                obj.getObjectField("args")?.getObjectField("upName").toString()
            } catch (thr: Throwable) {
                return false
            }
            if (upname == "null") return false
            if (kwdFilterUpnameRegexMode) {
                if (kwdFilterUpnameRegexes.any { upname.contains(it) })
                    return true
            } else {
                if (kwdFilterUpnameList.any { upname.contains(it) })
                    return true
            }
        }

        // 屏蔽分区
        if (kwdFilterRnameList.isNotEmpty()) {
            val rname = try {
                obj.getObjectField("args")?.getObjectField("rname").toString()
            } catch (thr: Throwable) {
                return false
            }
            if (rname == "null") return false
            kwdFilterRnameList.forEach {
                if (it.isNotEmpty() && rname.contains(it)) return true
            }
        }

        // 屏蔽频道
        if (kwdFilterTnameList.isNotEmpty()) {
            val tname = try {
                obj.getObjectField("args")?.getObjectField("tname").toString()
            } catch (thr: Throwable) {
                return false
            }
            if (tname == "null") return false
            kwdFilterTnameList.forEach {
                if (it.isNotEmpty() && tname.contains(it)) return true
            }
        }

        // 屏蔽推荐关键词（可能不存在，必须放最后）
        if (kwdFilterReasonList.isNotEmpty()) {
            val reason = try {
                obj.getObjectField("rcmdReason")?.getObjectField("text").toString()
            } catch (thr: Throwable) {
                return false
            }
            if (reason == "null") return false
            if (kwdFilterReasonRegexMode) {
                kwdFilterReasonRegexes.forEach {
                    if (reason.contains(it)) return true
                }
            } else {
                kwdFilterReasonList.forEach {
                    if (it.isNotEmpty() && reason.contains(it)) return true
                }
            }
        }

        return false
    }

    private fun ArrayList<Any>.appendReasons() {
        for (item in this) {
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
            val treePoint = item.getObjectFieldAs<MutableList<Any>?>("threePoint") ?: continue
            instance.treePointItemClass?.new()?.apply {
                setObjectField("title", "漫游屏蔽")
                setObjectField("subtitle", "(本地屏蔽，重启生效)")
                setObjectField("type", "dislike")
                val reasons = mutableListOf<Any>()
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
            }?.let { treePoint.add(it) }
        }
    }

    override fun startHook() {
        Log.d("startHook: Pegasus")
        instance.pegasusFeedClass?.hookAfterMethod(
            instance.pegasusFeed(),
            instance.responseBodyClass
        ) { param ->
            param.result.getObjectField("data")?.getObjectFieldAs<ArrayList<Any>>("items")?.run {
                removeAll {
                    filter.any { item ->
                        item in it.getObjectFieldOrNullAs<String>("cardGoto")
                            .orEmpty() || item in it.getObjectFieldOrNullAs<String>("cardType")
                            .orEmpty() || item in it.getObjectFieldOrNullAs<String>("goTo")
                            .orEmpty()
                    } || isLowCountVideo(it) || isContainsBlockKwd(it) || durationVideo(it)
                }
                appendReasons()
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
    }
}
