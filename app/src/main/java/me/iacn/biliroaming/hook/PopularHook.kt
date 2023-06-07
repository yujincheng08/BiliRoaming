package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class PopularHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val hidden = sPrefs.getBoolean("hidden", false)

    private val hideLowPlayCountLimit = sPrefs.getLong("hide_low_play_count_popular_limit", 0)
    private val hideShortDurationLimit = sPrefs.getInt("hide_short_duration_popular_limit", 0)
    private val hideLongDurationLimit = sPrefs.getInt("hide_long_duration_popular_limit", 0)

    private val hideTopEntrance = sPrefs.getBoolean("hide_top_entrance_popular", false)
    private val hideSuggestFollow = sPrefs.getBoolean("hide_suggest_follow_popular", false)

    private val kwdFilterTitleRegexMode = sPrefs.getBoolean("popular_filter_title_regex_mode", false)
    private val kwdFilterTitleRegexes by lazy { kwdFilterTitleList.map { it.toRegex() } }
    private val kwdFilterTitleList by lazy {
        sPrefs.getStringSet("popular_filter_keywords_title", null).orEmpty()
    }

    private val kwdFilterUpnameRegexMode = sPrefs.getBoolean("popular_filter_up_regex_mode", false)
    private val kwdFilterUpnameRegexes by lazy { kwdFilterUpnameList.map { it.toRegex() } }
    private val kwdFilterUpnameList by lazy {
        sPrefs.getStringSet("popular_filter_keywords_up", null).orEmpty()
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
        val rightDesc2 = obj.callMethodAs<String>("getRightDesc2") // xx万观看 · 时间

        val text = rightDesc2.split(' ').first().removeSuffix("观看")

        return text.toPlayCount().let {
            if (it == -1L) false
            else it < hideLowPlayCountLimit
        }
    }

    // 屏蔽指定播放时长
    private fun durationVideo(obj: Any): Boolean {
        fun getTimeInSeconds(time: String): Long {
            val parts = time.split(":").map { it.toInt() }

            val seconds: Long = when (parts.size) {
                2 -> parts[0] * 60L + parts[1]
                3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
                else -> throw IllegalArgumentException("Invalid time format: $time")
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

    // 屏蔽关键词
    private fun isContainsBlockKwd(obj: Any, base: Any?): Boolean {
        base?: return false

        // 屏蔽标题
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

        // 屏蔽UP主
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

        return false
    }

    override fun startHook() {
        Log.d("startHook: Popular")

        fun cardV5Handle(obj: Any?): Boolean {
            if (obj == null) return false

            // printFields(obj)
            val base = obj.callMethod("getBase")
            // val threePointV4 = base?.callMethod("getThreePointV4")
            // val sharePlane = threePointV4?.callMethod("getSharePlane")

            return isLowCountVideo(obj) || durationVideo(obj) || isContainsBlockKwd(obj, base)
        }

        fun MutableList<Any>.filter() = removeIf {
            when (val itemCase = it.callMethod("getItemCase")?.toString()) {
                "SMALL_COVER_V5" -> {
                    val v5 = it.callMethod("getSmallCoverV5")
                    return@removeIf cardV5Handle(v5)
                }
                "POPULAR_TOP_ENTRANCE" -> {
                    return@removeIf hideTopEntrance
                }
                "RCMD_ONE_ITEM" -> {
                    return@removeIf hideSuggestFollow
                }
                else -> {
                    Log.w("itemCase is $itemCase")
                    return@removeIf false
                }
            }
        }

        instance.popularClass?.hookAfterMethod(
            "index", "com.bapis.bilibili.app.show.popular.v1.PopularResultReq") { param ->
            param.result ?: return@hookAfterMethod
            param.result.callMethod("ensureItemsIsMutable")
            val card = param.result.callMethodAs<MutableList<Any>>("getItemsList")
            Log.d("before filter size: ${card.size}")
            card.filter()
            Log.d("after filter size: ${card.size}")
        }
    }
}
