package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import java.lang.reflect.Type

class JsonHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        val bottomItems = mutableListOf<BottomItem>()
        val drawerItems = mutableListOf<BottomItem>()
    }

    override fun startHook() {
        Log.d("startHook: Json")

        val hidden = sPrefs.getBoolean("hidden", false)
        val purifyLivePopups = sPrefs.getStringSet("purify_live_popups", null) ?: setOf()

        val tabResponseClass =
            "tv.danmaku.bili.ui.main2.resource.MainResourceManager\$TabResponse".findClassOrNull(
                mClassLoader
            )
        val accountMineClass =
            "tv.danmaku.bili.ui.main2.api.AccountMine".findClassOrNull(mClassLoader)
        val splashClass = "tv.danmaku.bili.ui.splash.SplashData".findClassOrNull(mClassLoader)
            ?: "tv.danmaku.bili.ui.splash.ad.model.SplashData".findClassOrNull(mClassLoader)
        val tabClass =
            "tv.danmaku.bili.ui.main2.resource.MainResourceManager\$Tab".findClassOrNull(
                mClassLoader
            )
        val defaultWordClass =
            "tv.danmaku.bili.ui.main2.api.SearchDefaultWord".findClassOrNull(mClassLoader)
        val defaultKeywordClass =
            "com.bilibili.search.api.DefaultKeyword".findClassOrNull(mClassLoader)
        val brandSplashDataClass =
            "tv.danmaku.bili.ui.splash.brand.BrandSplashData".findClassOrNull(mClassLoader)
                ?: "tv.danmaku.bili.ui.splash.brand.model.BrandSplashData".findClassOrNull(mClassLoader)
        val eventEntranceClass =
            "tv.danmaku.bili.ui.main.event.model.EventEntranceModel".findClassOrNull(mClassLoader)
        val searchRanksClass = "com.bilibili.search.api.SearchRanks".findClassOrNull(mClassLoader)
        val searchReferralClass =
            "com.bilibili.search.api.SearchReferral".findClassOrNull(mClassLoader)
        val followingcardSearchRanksClass =
            "com.bilibili.bplus.followingcard.net.entity.b".findClassOrNull(mClassLoader)
        val spaceClass =
            "com.bilibili.app.authorspace.api.BiliSpace".findClassOrNull(mClassLoader)
        val ogvApiResponseClass =
            "tv.danmaku.bili.ui.offline.api.OgvApiResponse".findClassOrNull(mClassLoader)
        val ogvApiResponseV2Class =
            "tv.danmaku.bili.ui.offline.api.OgvApiResponseV2".findClassOrNull(mClassLoader)
        val dmAdvertClass =
            "com.bilibili.ad.adview.videodetail.danmakuv2.model.DmAdvert".from(mClassLoader)
        val liveShoppingInfoClass =
            "com.bilibili.bililive.room.biz.shopping.beans.LiveShoppingInfo".from(mClassLoader)
        val liveGoodsCardInfoClass =
            "com.bilibili.bililive.room.biz.shopping.beans.LiveGoodsCardInfo".from(mClassLoader)
        val biliLiveRoomInfoClass =
            "com.bilibili.bililive.videoliveplayer.net.beans.gateway.roominfo.BiliLiveRoomInfo"
                .from(mClassLoader)
        val liveRoomReserveInfoClass =
            "com.bilibili.bililive.room.biz.reverse.bean.LiveRoomReserveInfo".from(mClassLoader)
        val biliLiveRoomUserInfoClass =
            "com.bilibili.bililive.videoliveplayer.net.beans.gateway.userinfo.BiliLiveRoomUserInfo"
                .from(mClassLoader)
        val liveRoomRecommendCardClass =
            "com.bilibili.bililive.videoliveplayer.net.beans.attentioncard.LiveRoomRecommendCard"
                .from(mClassLoader)

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

                    if (sPrefs.getBoolean("drawer", false) && !sPrefs.getBoolean("hidden", false)) {
                        data?.getObjectFieldAs<MutableList<*>?>("bottom")?.removeAll {
                            it?.getObjectFieldAs<String?>("uri")
                                ?.startsWith("bilibili://user_center/mine")
                                ?: false
                        }
                    }

                    configTab(data, tabClass)

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
                    drawerItems.clear()
                    val hides = sPrefs.getStringSet("hided_drawer_items", mutableSetOf())!!
                    if (platform == "android_hd") {
                        listOf(result.getObjectFieldOrNullAs<MutableList<*>?>("padSectionList"),
                               result.getObjectFieldOrNullAs<MutableList<*>?>("recommendSectionList"),
                               result.getObjectFieldOrNullAs<MutableList<*>?>("moreSectionList")
                        ).forEach {
                            it?.removeAll { items ->
                                // 分析内容
                                val title = items?.getObjectFieldAs<String>("title")
                                val uri = items?.getObjectFieldAs<String>("uri")
                                val id = items?.getObjectField("id").toString()

                                // 修改成自定义按钮
                                if (sPrefs.getBoolean("add_custom_button", false) && id == sPrefs.getString("custom_button_id", "")){
                                    val icon = items?.getObjectFieldAs<String>("icon").toString()
                                    items?.setObjectField("title", sPrefs.getString("custom_button_title", title))
                                        ?.setObjectField("uri", sPrefs.getString("custom_button_uri", uri))
                                        ?.setObjectField("icon", sPrefs.getString("custom_button_icon", icon))
                                    return@removeAll false
                                }

                                val showing = id !in hides
                                // 将结果写入 drawerItems
                                drawerItems.add(BottomItem(title, uri, id, showing))
                                // 去除红点
                                if (sPrefs.getBoolean("purify_drawer_reddot", false)) items?.setIntField("redDot",0)
                                showing.not()
                            }
                        }
                    } else {
                        result.getObjectFieldOrNullAs<MutableList<*>?>("sectionListV2")?.forEach { sections ->
                            try {
                                // 将标题写入 drawerItems
                                val bigTitle = sections?.getObjectFieldOrNull("title").toString()
                                if (bigTitle != "null") drawerItems.add(BottomItem("【标题项目】", null, bigTitle, bigTitle !in hides))
                                // 去除项目
                                sections?.getObjectFieldOrNullAs<MutableList<*>?>("itemList")
                                    ?.removeAll { items ->
                                        // 分析内容
                                        val title = try {
                                            items?.getObjectFieldAs<String>("title")
                                        } catch (thr: Throwable) {
                                            return@removeAll false
                                        }
                                        if (title == "null") return@removeAll false
                                        val uri = items?.getObjectFieldAs<String>("uri")
                                        val id = items?.getObjectField("id").toString()

                                        // 修改成自定义按钮
                                        if (sPrefs.getBoolean("add_custom_button", false) && id == sPrefs.getString("custom_button_id", "")){
                                            val icon = items?.getObjectFieldAs<String>("icon").toString()
                                            items?.setObjectField("title", sPrefs.getString("custom_button_title", title))
                                                ?.setObjectField("uri", sPrefs.getString("custom_button_uri", uri))
                                                ?.setObjectField("icon", sPrefs.getString("custom_button_icon", icon))
                                            return@removeAll false
                                        }

                                        val showing = id !in hides
                                        // 将结果写入 drawerItems
                                        drawerItems.add(BottomItem(title, uri, id, showing))
                                        // 去除红点
                                        if (sPrefs.getBoolean("purify_drawer_reddot", false)) items?.setIntField("redDot", 0)
                                        showing.not()
                                    }
                                // 去除按钮
                                val button = sections?.getObjectFieldOrNull("button")
                                if (button != null) {
                                    val buttonText = button.getObjectField("text").toString()
                                    val showing = buttonText !in hides
                                    if (buttonText != "null") {
                                        val uri = button.getObjectFieldAs<String>("jumpUrl")
                                        drawerItems.add(BottomItem("按钮：", uri, buttonText, showing))
                                        if (!showing) sections.setObjectField("button", null)
                                    }
                                }
                                // 改变样式
                                if (sPrefs.getBoolean("drawer_style_switch", false)) {
                                    sections?.setIntField(
                                        "style",
                                        when {
                                            sPrefs.getBoolean("drawer_style", false) -> 2
                                            else -> 1
                                        }
                                    )
                                }
                            } catch (e: Exception) {
                                Log.d(e)
                            }
                        }
                        // 删除标题组
                        var deleteTitle = true
                        result.getObjectFieldOrNullAs<MutableList<*>?>("sectionListV2")?.removeAll { sections ->
                            val title = sections?.getObjectFieldOrNull("title").toString()
                            if (title !in hides) {
                                if (title == "创作中心" || title == "游戏中心" || title == "創作中心" || title == "遊戲中心")
                                    deleteTitle = false // 标记
                                else if (title == "推荐服务" || title == "推薦服務")
                                    deleteTitle = true // 取消标记
                            } else {
                                if ((title == "更多服务" || title == "更多服務") && !deleteTitle) {
                                    Log.toast("自定义我的页面，【标题项目】不能只保留【创作中心】或【游戏中心】，因此不删除【更多服务】，请修改你的漫游设置", true)
                                    return@removeAll false
                                }
                            }
                            title in hides
                        }
                    }
                    accountMineClass.findFieldOrNull("vipSectionRight")?.set(result, null)
                    if (sPrefs.getBoolean("custom_theme", false)) {
                        result.setObjectField("garbEntrance", null)
                    }
                }
                splashClass -> if (sPrefs.getBoolean("purify_splash", false) &&
                    sPrefs.getBoolean("hidden", false)
                ) {
                    result.getObjectFieldOrNullAs<MutableList<*>>("splashList")?.clear()
                    result.getObjectFieldOrNullAs<MutableList<*>>("strategyList")?.clear()
                }
                defaultWordClass, defaultKeywordClass, searchRanksClass, searchReferralClass, followingcardSearchRanksClass -> if (sPrefs.getBoolean(
                        "purify_search",
                        false
                    ) &&
                    sPrefs.getBoolean("hidden", false)
                ) {
                    result.javaClass.fields.forEach {
                        if (!it.type.isPrimitive)
                            result.setObjectField(it.name, null)
                    }
                }
                brandSplashDataClass -> if (sPrefs.getBoolean("custom_splash", false) ||
                    sPrefs.getBoolean("custom_splash_logo", false)
                ) {
                    result.getObjectFieldOrNullAs<MutableList<Any>>("brandList")?.clear()
                    result.getObjectFieldOrNullAs<MutableList<Any>>("showList")?.clear()
                }
                eventEntranceClass -> if (sPrefs.getBoolean("purify_game", false) &&
                    sPrefs.getBoolean("hidden", false)
                ) {
                    result.setObjectField("online", null)
                    result.setObjectField("hash", "")
                }
                spaceClass -> {
                    val purifySpaceSet =
                        sPrefs.getStringSet("customize_space", emptySet()).orEmpty()
                    if (purifySpaceSet.isNotEmpty()) {
                        purifySpaceSet.forEach {
                            if (!it.contains(".")) result.setObjectField(
                                it,
                                null
                            )
                        }
                        // Exceptions (adV2 -> ad + adV2)
                        if (purifySpaceSet.contains("adV2")) result.setObjectField("ad", null)

                        result.getObjectFieldAs<MutableList<*>?>("tab")?.removeAll {
                            when (it?.getObjectFieldAs<String?>("param")) {
                                "home" -> purifySpaceSet.contains("tab.home")
                                "dynamic" -> purifySpaceSet.contains("tab.dynamic")
                                "contribute" -> purifySpaceSet.contains("tab.contribute")
                                "shop" -> purifySpaceSet.contains("tab.shop")
                                "bangumi" -> purifySpaceSet.contains("tab.bangumi")
                                "cheese" -> purifySpaceSet.contains("tab.cheese")
                                else -> false
                            }
                        }
                    }
                }

                ogvApiResponseClass -> if (sPrefs.getBoolean("allow_download", false)) {
                    val resultObj = result.getObjectFieldAs<ArrayList<Any>>("result")
                    for (i in resultObj) {
                        i.setIntField("isPlayable", 1)
                    }
                }

                ogvApiResponseV2Class -> if (sPrefs.getBoolean("allow_download", false)) {
                    val epPlayableParams = result.getObjectFieldOrNull("data")
                        ?.getObjectFieldOrNullAs<MutableList<*>>("epPlayableParams")
                    epPlayableParams?.forEach { playableParam ->
                        playableParam.setIntField("playableType", 0)
                    }
                }

                dmAdvertClass -> if (sPrefs.getBoolean("hidden", false)
                    && sPrefs.getBoolean("block_up_rcmd_ads", false)
                ) result.setObjectField("ads", null)
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
            val result = param.result as? MutableList<Any>
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

        instance.fastJsonClass?.hookAfterMethod(
            instance.fastJsonParse(),
            String::class.java,
            Type::class.java,
            "com.alibaba.fastjson.parser.Feature[]"
        ) { param ->
            var result = param.result ?: return@hookAfterMethod
            if (result.javaClass == instance.generalResponseClass)
                result = result.getObjectField("data") ?: return@hookAfterMethod

            when (result.javaClass) {
                liveShoppingInfoClass -> {
                    if (hidden && purifyLivePopups.contains("shoppingCard"))
                        result.setObjectField("shoppingCardDetail", null)
                    if (hidden && purifyLivePopups.contains("shoppingSelected"))
                        result.runCatchingOrNull {
                            setObjectField("selectedGoods", null)
                        }
                }

                liveGoodsCardInfoClass -> {
                    if (hidden && purifyLivePopups.contains("shoppingCard"))
                        param.result = null
                }

                biliLiveRoomInfoClass -> {
                    if (hidden && purifyLivePopups.contains("follow"))
                        result.getObjectFieldOrNull("functionCard")
                            ?.setObjectField("followCard", null)
                    if (hidden && purifyLivePopups.contains("banner"))
                        result.setObjectField("bannerInfo", null)
                }

                liveRoomRecommendCardClass -> {
                    if (hidden && purifyLivePopups.contains("follow"))
                        param.result = null
                }

                liveRoomReserveInfoClass -> {
                    if (hidden && purifyLivePopups.contains("reserve"))
                        result.setBooleanField("showReserveDetail", false)
                }

                biliLiveRoomUserInfoClass -> {
                    if (hidden && purifyLivePopups.contains("gift"))
                        result.getObjectFieldOrNull("functionCard")
                            ?.setObjectField("sengGiftCard", null)
                    if (hidden && purifyLivePopups.contains("task"))
                        result.runCatchingOrNull {
                            setObjectField("taskInfo", null)
                        }
                }
            }
        }
    }

    private fun configTab(data: Any?, tabClass: Class<*>?) {
        val tab = data?.getObjectFieldAs<MutableList<Any>>("tab") ?: return
        if (tabClass == null) return

        var hasBangumiCN = false
        var hasBangumiTW = false
        var hasMovieCN = false
        var hasMovieTW = false
        var hasKoreaHK = false
        var hasKoreaTW = false
        tab.forEach {
            when (it.getObjectFieldAs<String>("uri")) {
                "bilibili://pgc/home" -> hasBangumiCN = true
                "bilibili://following/home_activity_tab/6544" -> hasBangumiTW = true
                "bilibili://pgc/home?home_flow_type=2" -> hasMovieCN = true
                "bilibili://following/home_activity_tab/168644" -> hasMovieTW = true
                "bilibili://following/home_activity_tab/163541" -> hasKoreaHK = true
                "bilibili://following/home_activity_tab/95636" -> hasKoreaTW = true
            }
        }

        if (sPrefs.getBoolean("add_bangumi", false)) {
            if (!hasBangumiCN) {
                val bangumiCN = tabClass.new()
                    .setObjectField("tabId", "50")
                    .setObjectField("name", "追番（大陸）")
                    .setObjectField("uri", "bilibili://pgc/home")
                    .setObjectField("reportId", "bangumi")
                    .setIntField("pos", 50)
                tab.add(bangumiCN)
            }
            if (!hasBangumiTW) {
                val bangumiTW = tabClass.new()
                    .setObjectField("tabId", "60")
                    .setObjectField("name", "追番（港澳台）")
                    .setObjectField("uri", "bilibili://following/home_activity_tab/6544")
                    .setObjectField("reportId", "bangumi")
                    .setIntField("pos", 60)
                tab.add(bangumiTW)
            }
        }

        if (sPrefs.getBoolean("add_movie", false)) {
            if (!hasMovieCN) {
                val movieCN = tabClass.new()
                    .setObjectField("tabId", "70")
                    .setObjectField("name", "影視（大陸）")
                    .setObjectField("uri", "bilibili://pgc/home?home_flow_type=2")
                    .setObjectField("reportId", "film")
                    .setIntField("pos", 70)
                tab.add(movieCN)
            }
            if (!hasMovieTW) {
                val movieTW = tabClass.new()
                    .setObjectField("tabId", "80")
                    .setObjectField("name", "戏剧（港澳台）")
                    .setObjectField("uri", "bilibili://following/home_activity_tab/168644")
                    .setObjectField("reportId", "jptv")
                    .setIntField("pos", 80)
                tab.add(movieTW)
            }
        }

        if (sPrefs.getBoolean("add_korea", false)) {
            if (!hasKoreaHK) {
                val koreaHK = tabClass.new()
                    .setObjectField("tabId", "803")
                    .setObjectField("name", "韩综（港澳）")
                    .setObjectField("uri", "bilibili://following/home_activity_tab/163541")
                    .setObjectField("reportId", "koreavhk")
                    .setIntField("pos", 803)
                tab.add(koreaHK)
            }
            if (!hasKoreaTW) {
                val koreaTW = tabClass.new()
                    .setObjectField("tabId", "804")
                    .setObjectField("name", "韩综（台湾）")
                    .setObjectField("uri", "bilibili://following/home_activity_tab/95636")
                    .setObjectField("reportId", "koreavtw")
                    .setIntField("pos", 804)
                tab.add(koreaTW)
            }
        }

        val purifytabset = sPrefs.getStringSet("customize_home_tab", emptySet())!!
        if (purifytabset.isEmpty()) return
        tab.removeAll {
            when (it.getObjectFieldAs<String>("uri")) {
                "bilibili://live/home"
                -> purifytabset.contains("live")

                "bilibili://pegasus/promo"
                -> purifytabset.contains("promo")

                "bilibili://pegasus/hottopic"
                -> purifytabset.contains("hottopic")

                "bilibili://pgc/home",
                "bilibili://following/home_activity_tab/6544"
                -> purifytabset.contains("bangumi")

                "bilibili://pgc/home?home_flow_type=2",
                "bilibili://following/home_activity_tab/168644"
                -> purifytabset.contains("movie")

                "bilibili://following/home_activity_tab/95636",
                "bilibili://following/home_activity_tab/163541"
                -> purifytabset.contains("korea")

                else -> purifytabset.contains("other_tabs")
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
