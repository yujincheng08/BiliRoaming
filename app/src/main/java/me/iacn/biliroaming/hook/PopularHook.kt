package me.iacn.biliroaming.hook

import de.robv.android.xposed.XC_MethodHook
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class PopularHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val hideTopEntrance = sPrefs.getBoolean("hide_top_entrance_popular", false)
    private val hideSuggestFollow = sPrefs.getBoolean("hide_suggest_follow_popular", false)

    private val hidden = sPrefs.getBoolean("hidden", false)

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

    companion object {
        private var dataVersion = ""
        private var dataCount = 0
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

    private fun isLowCountVideo(obj: Any): Boolean {
        if (hideLowPlayCountLimit == 0L) return false
        val rightDesc2 = obj.callMethodAs<String>("getRightDesc2")

        val text = rightDesc2.split(' ').first().removeSuffix("观看")
        return text.toPlayCount().let {
            if (it == -1L) false
            else it < hideLowPlayCountLimit
        }
    }

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

    private fun isContainsBlockKwd(obj: Any, base: Any?): Boolean {
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

            val hasContent = reasonText?.isNotEmpty() == true
            do {
                if (!hasContent) {
                    break
                }
                if (kwdFilterReasonRegexMode && kwdFilterReasonRegexes.any { reasonText!!.contains(it) }) {
                    return true
                } else if (kwdFilterReasonList.any { reasonText!!.contains(it) }) {
                    return true
                }
            } while (false)
        }

        return false
    }

    override fun startHook() {
        Log.d("startHook: Popular")
        if (!hidden) return

        fun cardV5Handle(obj: Any?): Boolean {
            obj ?: return false

            val base = obj.callMethod("getBase")
            if (dataCount % 10 == 0) {
                dataVersion = base?.callMethodAs<String>("getParam") ?: dataVersion
            }

            return isLowCountVideo(obj) || durationVideo(obj) || isContainsBlockKwd(obj, base)
        }

        fun MutableList<Any>.filter() = removeIf {
            when (val itemCase = it.callMethod("getItemCase")?.toString()) {
                "SMALL_COVER_V5" -> {
                    val v5 = it.callMethod("getSmallCoverV5")
                    dataCount++
                    return@removeIf cardV5Handle(v5)
                }
                "POPULAR_TOP_ENTRANCE" -> {
                    return@removeIf hideTopEntrance
                }
                "RCMD_ONE_ITEM" -> {
                    dataCount++
                    return@removeIf hideSuggestFollow
                }
                else -> {
                    dataCount++
                    return@removeIf false
                }
            }
        }

        instance.popularClass?.hookMethod(
            "index", "com.bapis.bilibili.app.show.popular.v1.PopularResultReq", object: XC_MethodHook(){
                override fun beforeHookedMethod(param: MethodHookParam?) {
                    param?.args ?: return

                    val versionField = param.args[0].javaClass.getDeclaredField("lastParam_")
                    val idxField = param.args[0].javaClass.getDeclaredField("idx_")
                    versionField.isAccessible = true
                    idxField.isAccessible = true

                    val idx = idxField.getLong(param.args[0])
                    if (idx == 0L) {
                        dataCount = 0
                        dataVersion = ""
                        return
                    }

                    versionField.set(param.args[0], dataVersion)
                    idxField.set(param.args[0], dataCount)
                }

                override fun afterHookedMethod(param: MethodHookParam?) {
                    param?.result ?: return

                    param.result.callMethod("ensureItemsIsMutable")
                    param.result.callMethodAs<MutableList<Any>>("getItemsList").filter()
                }
            })
    }
}
