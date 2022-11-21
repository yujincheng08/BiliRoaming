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
        sPrefs.getStringSet("home_filter_keywords_title", null) ?: setOf()
    }
    private val kwdFilterTitleRegexes by lazy { kwdFilterTitleList.map { it.toRegex() } }
    private val kwdFilterTitleRegexMode by lazy {
        sPrefs.getBoolean("home_filter_title_regex_mode", false)
    }
    private val kwdFilterReasonList by lazy {
        migrateHomeFilterPrefsIfNeeded()
        sPrefs.getStringSet("home_filter_keywords_reason", null) ?: setOf()
    }
    private val kwdFilterReasonRegexes by lazy { kwdFilterReasonList.map { it.toRegex() } }
    private val kwdFilterReasonRegexMode by lazy {
        sPrefs.getBoolean("home_filter_reason_regex_mode", false)
    }
    private val kwdFilterUidList by lazy {
        migrateHomeFilterPrefsIfNeeded()
        sPrefs.getStringSet("home_filter_keywords_uid", null)
            ?.mapNotNull { it.toLongOrNull() } ?: listOf()
    }
    private val kwdFilterUpnameList by lazy {
        migrateHomeFilterPrefsIfNeeded()
        sPrefs.getStringSet("home_filter_keywords_up", null) ?: setOf()
    }
    private val kwdFilterRnameList by lazy {
        migrateHomeFilterPrefsIfNeeded()
        sPrefs.getStringSet("home_filter_keywords_category", null) ?: setOf()
    }
    private val kwdFilterTnameList by lazy {
        migrateHomeFilterPrefsIfNeeded()
        sPrefs.getStringSet("home_filter_keywords_channel", null) ?: setOf()
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
                kwdFilterTitleRegexes.forEach {
                    if (title.contains(it)) return true
                }
            } else {
                kwdFilterTitleList.forEach {
                    if (it.isNotEmpty() && title.contains(it)) return true
                }
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
            kwdFilterUpnameList.forEach {
                if (it.isNotEmpty() && upname.contains(it)) return true
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

    override fun startHook() {
        Log.d("startHook: Pegasus")
        instance.pegasusFeedClass?.hookAfterMethod(
            instance.pegasusFeed(),
            instance.responseBodyClass
        ) { param ->
            param.result.getObjectField("data")?.getObjectFieldAs<ArrayList<Any>>("items")
                ?.let { arr ->
                    arr.removeAll {
                        filter.fold(false) { acc, item ->
                            acc || item in it.getObjectFieldOrNullAs<String>("cardGoto")
                                .orEmpty() || item in it.getObjectFieldOrNullAs<String>("cardType")
                                .orEmpty() || item in it.getObjectFieldOrNullAs<String>("goTo")
                                .orEmpty()
                        } || isLowCountVideo(it) || isContainsBlockKwd(it) || durationVideo(it)
                    }
                }
        }
    }
}
