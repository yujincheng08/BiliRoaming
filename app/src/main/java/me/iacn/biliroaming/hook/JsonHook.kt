package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import java.lang.reflect.Type

class JsonHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        val bottomItems = mutableListOf<BottomItem>()
    }

    override fun startHook() {
        Log.d("startHook: Json")

        val tabResponseClass =
            "tv.danmaku.bili.ui.main2.resource.MainResourceManager\$TabResponse".findClassOrNull(
                mClassLoader
            )
        val accountMineClass = "tv.danmaku.bili.ui.main2.api.AccountMine".findClassOrNull(mClassLoader)
        val splashClass = "tv.danmaku.bili.ui.splash.SplashData".findClassOrNull(mClassLoader)
        val tabClass =
            "tv.danmaku.bili.ui.main2.resource.MainResourceManager\$Tab".findClassOrNull(mClassLoader)
        val defaultWordClass =
            "tv.danmaku.bili.ui.main2.api.SearchDefaultWord".findClassOrNull(mClassLoader)
        val defaultKeywordClass = "com.bilibili.search.api.DefaultKeyword".findClassOrNull(mClassLoader)
        val brandSplashDataClass =
            "tv.danmaku.bili.ui.splash.brand.BrandSplashData".findClassOrNull(mClassLoader)
        val eventEntranceClass =
            "tv.danmaku.bili.ui.main.event.model.EventEntranceModel".findClassOrNull(mClassLoader)
        val cursorListClass =
            "com.bilibili.app.comm.comment2.model.BiliCommentCursorList".findClassOrNull(
                mClassLoader
            )
        val searchRanksClass = "com.bilibili.search.api.SearchRanks".findClassOrNull(mClassLoader)
        val searchReferralClass = "com.bilibili.search.api.SearchReferral".findClassOrNull(mClassLoader)
        val followingcardSearchRanksClass =
            "com.bilibili.bplus.followingcard.net.entity.b".findClassOrNull(mClassLoader)

        instance.fastJsonClass?.hookAfterMethod(
            instance.fastJsonParse(),
            String::class.java,
            Type::class.java,
            Int::class.javaPrimitiveType,
            "com.alibaba.fastjson.parser.Feature[]"
        ) { param ->
            var result = param.result ?: return@hookAfterMethod
            if (result.javaClass == instance.generalResponseClass) {
                result = result.getObjectField("data") ?: return@hookAfterMethod
            }

            when (result.javaClass) {
                tabResponseClass -> {
                    val data = result.getObjectField("tabData")

                    bottomItems.clear()
                    val hides = sPrefs.getStringSet("hided_bottom_items", mutableSetOf())!!
                    data?.getObjectFieldAs<MutableList<*>?>("bottom")?.removeAll {
                        val uri = it?.getObjectFieldAs<String>("uri")
                        val id = it?.getObjectFieldAs<String>("tabId")
                        val showing = id !in hides
                        bottomItems.add(
                            BottomItem(
                                it?.getObjectFieldAs("name"),
                                uri, id, showing
                            )
                        )
                        showing.not()
                    }

                    if (sPrefs.getBoolean("drawer", false)) {
                        data?.getObjectFieldAs<MutableList<*>?>("bottom")?.removeAll {
                            it?.getObjectFieldAs<String?>("uri")
                                ?.startsWith("bilibili://user_center/mine")
                                ?: false
                        }
                    }

                    if (sPrefs.getBoolean("add_live", false)) {
                        val tab = data?.getObjectFieldAs<MutableList<Any>>("tab")
                        val hasLive = tab?.fold(false) { acc, it ->
                            val uri = it.getObjectFieldAs<String>("uri")
                            acc || uri.startsWith("bilibili://live/home")
                        }
                        if (hasLive != null && !hasLive) {
                            val live = tabClass?.new()
                                ?.setObjectField("tabId", "20")
                                ?.setObjectField("name", "直播")
                                ?.setObjectField("uri", "bilibili://live/home")
                                ?.setObjectField("reportId", "直播tab")
                                ?.setIntField("pos", 1)
                            live?.let { l ->
                                tab.forEach {
                                    it.setIntField("pos", it.getIntField("pos") + 1)
                                }
                                tab.add(0, l)
                            }
                        }
                    }

                    if (sPrefs.getBoolean("add_bangumi", false)) {
                        val tab = data?.getObjectFieldAs<MutableList<Any>>("tab")
                        val hasBangumiCN = tab?.fold(false) { acc, it ->
                            val uri = it.getObjectFieldAs<String>("uri")
                            acc || uri.toString() == "bilibili://pgc/home"
                        }
                        val hasBangumiTW = tab?.fold(false) { acc, it ->
                            val uri = it.getObjectFieldAs<String>("uri")
                            acc || uri.startsWith("bilibili://following/home_activity_tab/6544")
                        }
                        if (hasBangumiCN != null && !hasBangumiCN) {
                            val bangumiCN = tabClass?.new()
                                ?.setObjectField("tabId", "50")
                                ?.setObjectField("name", "追番（大陸）")
                                ?.setObjectField("uri", "bilibili://pgc/home")
                                ?.setObjectField("reportId", "追番tab")
                                ?.setIntField("pos", 50)
                            bangumiCN?.let { l ->
                                tab.forEach {
                                    it.setIntField("pos", it.getIntField("pos") + 0)
                                }
                                tab.add(0, l)
                            }
                        }
                        if (hasBangumiTW != null && !hasBangumiTW) {
                            val bangumiTW = tabClass?.new()
                                ?.setObjectField("tabId", "60")
                                ?.setObjectField("name", "追番（港澳台）")
                                ?.setObjectField("uri", "bilibili://following/home_activity_tab/6544")
                                ?.setObjectField("reportId", "港澳台tab")
                                ?.setIntField("pos", 60)
                            bangumiTW?.let { l ->
                                tab.forEach {
                                    it.setIntField("pos", it.getIntField("pos") + 0)
                                }
                                tab.add(0, l)
                            }
                        }
                    }

                    if (sPrefs.getBoolean("add_movie", false)) {
                        val tab = data?.getObjectFieldAs<MutableList<Any>>("tab")
                        val hasMovieCN = tab?.fold(false) { acc, it ->
                            val uri = it.getObjectFieldAs<String>("uri")
                            acc || uri.toString() == "bilibili://pgc/home?home_flow_type=2"
                        }
                        val hasMovieTW = tab?.fold(false) { acc, it ->
                            val uri = it.getObjectFieldAs<String>("uri")
                            acc || uri.startsWith("bilibili://pegasus/op/70465")
                        }
                        if (hasMovieCN != null && !hasMovieCN) {
                            val movieCN = tabClass?.new()
                                ?.setObjectField("tabId", "70")
                                ?.setObjectField("name", "影視（大陸）")
                                ?.setObjectField("uri", "bilibili://pgc/home?home_flow_type=2")
                                ?.setObjectField("reportId", "影視tab")
                                ?.setIntField("pos", 70)
                            movieCN?.let { l ->
                                tab.forEach {
                                    it.setIntField("pos", it.getIntField("pos") + 0)
                                }
                                tab.add(0, l)
                            }
                        }
                        if (hasMovieTW != null && !hasMovieTW) {
                            if (hasMovieCN != null && !hasMovieCN && platform != "android_b") {
                                val movieTW = tabClass?.new()
                                    ?.setObjectField("tabId", "40")
                                    ?.setObjectField("name", "影視")
                                    ?.setObjectField("uri", "bilibili://pegasus/op/70465?name=影視")
                                    ?.setObjectField("reportId", "港澳台tab")
                                    ?.setIntField("pos", 40)
                                movieTW?.let { l ->
                                    tab.forEach {
                                        it.setIntField("pos", it.getIntField("pos") + 0)
                                    }
                                    tab.add(0, l)
                                }
                            } else {
                                val movieTW = tabClass?.new()
                                    ?.setObjectField("tabId", "80")
                                    ?.setObjectField("name", "影视（港澳台）")
                                    ?.setObjectField("uri", "bilibili://pegasus/op/70465?name=影視")
                                    ?.setObjectField("reportId", "港澳台tab")
                                    ?.setIntField("pos", 80)
                                movieTW?.let { l ->
                                    tab.forEach {
                                        it.setIntField("pos", it.getIntField("pos") + 0)
                                    }
                                    tab.add(0, l)
                                }
                            }
                        }
                    }

                    if (sPrefs.getStringSet("customize_home_tab", emptySet())
                            ?.isNotEmpty() == true
                    ) {
                        val tab = data?.getObjectFieldAs<MutableList<Any>>("tab")
                        val purifytabset =
                            sPrefs.getStringSet("customize_home_tab", emptySet()).orEmpty()
                        tab?.removeAll {
                            it.getObjectFieldAs<String>("uri").run {
                                when {
                                    this == "bilibili://live/home" -> purifytabset.contains("live")
                                    this == "bilibili://pegasus/promo" -> purifytabset.contains("promo")
                                    this == "bilibili://pegasus/hottopic" -> purifytabset.contains("hottopic")
                                    this == "bilibili://pgc/home" || this == "bilibili://following/home_activity_tab/6544" -> purifytabset.contains("bangumi")
                                    this == "bilibili://pgc/home?home_flow_type=2" || this == "bilibili://pegasus/op/70465?name=影視" -> purifytabset.contains(
                                        "movie"
                                    )
                                    startsWith("bilibili://pegasus/op/") || startsWith("bilibili://following/home_activity_tab") -> purifytabset.contains(
                                        "activity"
                                    )
                                    else -> purifytabset.contains("other_tabs")
                                }
                            }
                        }
                    }

                    if (sPrefs.getBoolean("purify_game", false) &&
                        sPrefs.getBoolean("hidden", false)
                    ) {
                        val top = data?.getObjectFieldAs<MutableList<*>?>("top")
                        top?.removeAll {
                            val uri = it?.getObjectFieldAs<String?>("uri")
                            uri?.startsWith("bilibili://game_center/home") ?: false
                        }
                    }

                }
                accountMineClass -> {
                    if (sPrefs.getBoolean("purify_drawer", false) &&
                        sPrefs.getBoolean("hidden", false)
                    ) {
                        arrayOf(
                            result.getObjectFieldAs<MutableList<*>?>("sectionList"),
                            result.getObjectFieldAs<MutableList<*>>("sectionListV2")
                        ).forEach { sections ->
                            var button: Any? = null
                            sections?.removeAll { item ->
                                item?.getObjectField("button")?.run {
                                    if (!getObjectFieldAs<String?>("text").isNullOrEmpty())
                                        button = this
                                }
                                when {
                                    item?.getObjectFieldAs<String?>("title")
                                        .isNullOrEmpty() -> false
                                    item?.getIntField("style") == 2 -> {
                                        item.setObjectField("button", button)
                                        false
                                    }
                                    else -> true
                                }
                            }
                        }
                        accountMineClass.findFieldOrNull("vipSectionRight")?.set(result, null)
                    }
                    if (sPrefs.getBoolean("custom_theme", false)) {
                        result.setObjectField("garbEntrance", null)
                    }
                }
                splashClass -> if (sPrefs.getBoolean("purify_splash", false) &&
                    sPrefs.getBoolean("hidden", false)
                ) {
                    result.getObjectFieldAs<MutableList<*>?>("splashList")?.clear()
                    result.getObjectFieldAs<MutableList<*>?>("strategyList")?.clear()
                }
                defaultWordClass, defaultKeywordClass, searchRanksClass, searchReferralClass, followingcardSearchRanksClass -> if (sPrefs.getBoolean(
                        "purify_search",
                        false
                    ) &&
                    sPrefs.getBoolean("hidden", false)
                ) {
                    result.javaClass.fields.forEach {
                        if (it.type != Int::class.javaPrimitiveType)
                            result.setObjectField(it.name, null)
                    }
                }
                brandSplashDataClass -> if (sPrefs.getBoolean("custom_splash", false) ||
                    sPrefs.getBoolean("custom_splash_logo", false)
                ) {
                    val brandList = result.getObjectFieldAs<MutableList<Any>>("brandList")
                    val showList = result.getObjectFieldAs<MutableList<Any>>("showList")
                    brandList.clear()
                    showList.clear()
                }
                eventEntranceClass -> if (sPrefs.getBoolean("purify_game", false) &&
                    sPrefs.getBoolean("hidden", false)
                ) {
                    result.setObjectField("online", null)
                    result.setObjectField("hash", "")
                }
                cursorListClass -> if (sPrefs.getBoolean("comment_floor", false)) {
                    result.getObjectField("cursor")
                        ?.setObjectField("supportMode", intArrayOf(1, 2, 3))
                }
            }
        }

        val searchRankClass = "com.bilibili.search.api.SearchRank".findClass(mClassLoader)
        val searchGuessClass =
            "com.bilibili.search.api.SearchReferral\$Guess".findClass(mClassLoader)
        val categoryClass = "tv.danmaku.bili.category.CategoryMeta".findClass(mClassLoader)

        instance.fastJsonClass?.hookAfterMethod(
            "parseArray",
            String::class.java,
            Class::class.java
        ) { param ->
            @Suppress("UNCHECKED_CAST")
            val result = param.result as MutableList<Any>?
            when (param.args[1] as Class<*>) {
                searchRankClass, searchGuessClass ->
                    if (sPrefs.getBoolean("purify_search", false) && sPrefs.getBoolean(
                            "hidden",
                            false
                        )
                    ) {
                        result?.clear()
                    }
                categoryClass ->
                    if (sPrefs.getBoolean("music_notification", false)) {
                        val hasMusic = result?.fold(false) { r, i ->
                            r || (i.getObjectFieldAs<String?>("mUri")
                                ?.startsWith("bilibili://music")
                                ?: false)
                        } ?: false
                        if (!hasMusic) {
                            result?.add(
                                categoryClass.new()
                                    .setObjectField("mTypeName", "音頻")
                                    .setObjectField(
                                        "mCoverUrl",
                                        "http://i0.hdslb.com/bfs/archive/85d6dddbdc9746fed91c65c2c3eb3a0a453eadaf.png"
                                    )
                                    .setObjectField("mUri", "bilibili://music/home?from=category")
                                    .setIntField("mType", 1)
                                    .setIntField("mParentTid", 0)
                                    .setIntField("mTid", 65543)
                            )
                        }
                    }
            }
        }

        if (sPrefs.getBoolean("purify_city", false) &&
            sPrefs.getBoolean("hidden", false)
        ) {
            "com.bapis.bilibili.app.dynamic.v1.DynTabReply".hookAfterMethod(
                mClassLoader,
                "getDynTabList"
            ) { param ->
                param.result = (param.result as List<*>).filter {
                    it?.callMethodAs<Long>("getCityId") == 0L
                }
            }
        }
    }

    data class BottomItem(
        val name: String?,
        val uri: String?,
        val id: String?,
        var showing: Boolean
    )
}
