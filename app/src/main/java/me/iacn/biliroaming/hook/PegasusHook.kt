package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class PegasusHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val filterSet = sPrefs.getStringSet("customize_home_recommend", emptySet()).orEmpty()

    private val hideLowPlayCountEnabled by lazy {
        sPrefs.getBoolean("hide_low_play_count_recommend", false)
    }
    private val hideShortDurationEnabled by lazy {
        sPrefs.getBoolean("hide_short_duration_recommend", false)
    }
    private val hideLongDurationEnabled by lazy {
        sPrefs.getBoolean("hide_long_duration_recommend", false)
    }
    private val kwdFilterTitleEnabled by lazy {
        sPrefs.getBoolean("keywords_filter_title_recommend", false)
    }
    private val kwdFilterReasonEnabled by lazy {
        sPrefs.getBoolean("keywords_filter_reason_recommend", false)
    }
    private val kwdFilterUidEnabled by lazy {
        sPrefs.getBoolean("keywords_filter_uid_recommend", false)
    }
    private val kwdFilterUpnameEnabled by lazy {
        sPrefs.getBoolean("keywords_filter_upname_recommend", false)
    }
    private val kwdFilterRnameEnabled by lazy {
        sPrefs.getBoolean("keywords_filter_rname_recommend", false)
    }
    private val kwdFilterTnameEnabled by lazy {
        sPrefs.getBoolean("keywords_filter_tname_recommend", false)
    }
    private val hideLowPlayCountLimit by lazy {
        sPrefs.getLong("hide_low_play_count_recommend_limit", 100)
    }
    private val hideShortDurationLimit by lazy {
        sPrefs.getInt("hide_short_duration_recommend_limit", 0)
    }
    private val hideLongDurationLimit by lazy {
        sPrefs.getInt("hide_long_duration_recommend_limit", 0)
    }
    private val kwdFilterTitleList by lazy {
        sPrefs.getString("keywords_filter_title_recommend_list", "")?.split("|") ?: emptyList()
    }
    private val kwdFilterReasonList by lazy {
        sPrefs.getString("keywords_filter_reason_recommend_list", "")?.split("|") ?: emptyList()
    }
    private val kwdFilterUidList by lazy {
        sPrefs.getString("keywords_filter_uid_recommend_list", "")?.split("|") ?: emptyList()
    }
    private val kwdFilterUpnameList by lazy {
        sPrefs.getString("keywords_filter_upname_recommend_list", "")?.split("|") ?: emptyList()
    }
    private val kwdFilterRnameList by lazy {
        sPrefs.getString("keywords_filter_rname_recommend_list", "")?.split("|") ?: emptyList()
    }
    private val kwdFilterTnameList by lazy {
        sPrefs.getString("keywords_filter_tname_recommend_list", "")?.split("|") ?: emptyList()
    }

    private val filterMap = mapOf(
        "advertisement" to arrayListOf("ad", "large_cover_v6", "large_cover_v9"),
        "article" to arrayListOf("article"),
        "bangumi" to arrayListOf("bangumi", "special"),
        "picture" to arrayListOf("picture"),
        "vertical" to arrayListOf("vertical"),
        "banner" to arrayListOf("banner"),
        "live" to arrayListOf("live"),
        "inline" to arrayListOf("inline"),
        "notify" to arrayListOf("notify_tunnel_v1"),
    )

    private val filter = filterSet.flatMap {
        filterMap[it].orEmpty()
    }

    private fun String.isNum(): Boolean {
        for (s in this) {
            if (!Character.isDigit(s)) return false
        }
        return true
    }

    private fun toLong(str: String): Long {
        return when {
            str.isNum() ->
                return str.toDouble().toLong()
            str.contains("万") ->
                (str.replace("万", "").toDouble() * 10_000).toLong()
            str.contains("亿") ->
                (str.replace("亿", "").toDouble() * 100_000_000).toLong()
            else ->
                -1L
        }
    }

    // 屏蔽过低的播放数
    private fun isLowCountVideo(obj: Any): Boolean {
        if (!hideLowPlayCountEnabled) return false
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
        if (hideLongDurationEnabled) {
            if (duration > hideLongDurationLimit && hideLongDurationLimit != 0) return true
        }
        if (hideShortDurationEnabled) {
            if (duration < hideShortDurationLimit && hideShortDurationLimit != 0) return true
        }
        return false
    }

    private fun isContainsBlockKwd(obj: Any): Boolean {
        // 屏蔽标题关键词
        if (kwdFilterTitleEnabled && kwdFilterTitleList.isNotEmpty()) {
            val title = try {
                obj.getObjectField("title").toString()
            } catch (thr: Throwable) {
                return false
            }
            kwdFilterTitleList.forEach {
                if (it.isNotEmpty() && title.contains(it)) return true
            }
        }

        // 屏蔽UID
        if (kwdFilterUidEnabled && kwdFilterUidList.isNotEmpty()) {
            val uid = try {
                obj.getObjectField("args")?.getObjectField("upId").toString()
            } catch (thr: Throwable) {
                return false
            }
            if (uid == "null") return false
            kwdFilterUidList.forEach {
                if (it.isNotEmpty() && it == uid) return true
            }
        }

        // 屏蔽UP主
        if (kwdFilterUpnameEnabled && kwdFilterUpnameList.isNotEmpty()) {
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
        if (kwdFilterRnameEnabled && kwdFilterRnameList.isNotEmpty()) {
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
        if (kwdFilterTnameEnabled && kwdFilterTnameList.isNotEmpty()) {
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
        if (kwdFilterReasonEnabled && kwdFilterReasonList.isNotEmpty()) {
            val reason = try {
                obj.getObjectField("rcmdReason")?.getObjectField("text").toString()
            } catch (thr: Throwable) {
                return false
            }
            if (reason == "null") return false
            kwdFilterReasonList.forEach {
                if (it.isNotEmpty() && reason.contains(it)) return true
            }
        }

        return false
    }

    override fun startHook() {
        if (filter.isEmpty()) return
        Log.d("startHook: Pegasus")
        instance.pegasusFeedClass?.hookAfterMethod(
            instance.pegasusFeed(),
            instance.okhttpResponseClass
        ) { param ->
            param.result.getObjectField("data")?.getObjectFieldAs<ArrayList<Any>>("items")
                ?.let { arr ->
                    arr.removeAll {
                        filter.fold(false) { acc, item ->
                            acc || item in it.getObjectFieldAs<String?>("cardGoto")
                                .orEmpty() || item in it.getObjectFieldAs<String?>("cardType")
                                .orEmpty() || item in it.getObjectFieldAs<String?>("goTo")
                                .orEmpty() || isLowCountVideo(it) || isContainsBlockKwd(it)
                                || durationVideo(it)
                        }
                    }
                }
        }
    }
}
