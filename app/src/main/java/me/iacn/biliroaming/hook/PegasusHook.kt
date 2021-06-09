package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class PegasusHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val filterSet = sPrefs.getStringSet("customize_home_recommend", emptySet()).orEmpty()

    private val hideLowPlayCountEnabled by lazy {
        sPrefs.getBoolean("hide_low_play_count_recommend", false)
    }
    private val kwdFilterTitleEnabled by lazy {
        sPrefs.getBoolean("keywords_filter_title_recommend", false)
    }
    private val hideLowPlayCountLimit by lazy {
        sPrefs.getLong("hide_low_play_count_recommend_limit", 100)
    }
    private val kwdFilterTitleList by lazy {
        sPrefs.getString("keywords_filter_title_recommend_list", "")?.split("|") ?: emptyList()
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


    private fun isContainsBlockKwd(obj: Any): Boolean {
        if (!kwdFilterTitleEnabled || kwdFilterTitleList.isEmpty()) return false
        val title = try {
            obj.getObjectField("title").toString()
        } catch (thr: Throwable) {
            return false
        }
        kwdFilterTitleList.forEach {
            if (it.isNotEmpty() && title.contains(it)) return true
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
                                .orEmpty() || item in it.getObjectFieldAs<String?>("goTo")
                                .orEmpty() || isLowCountVideo(it) || isContainsBlockKwd(it)
                        }
                    }
                }
        }
    }
}
