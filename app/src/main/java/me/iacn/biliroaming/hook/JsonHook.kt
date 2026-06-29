package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.hook.hometab.BottomItem
import me.iacn.biliroaming.hook.json.JsonProcessor
import me.iacn.biliroaming.hook.json.HomeTabProcessor
import me.iacn.biliroaming.utils.*
import java.lang.reflect.Type

class JsonHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        val drawerItems = mutableListOf<BottomItem>()

        private val allProcessors = listOf<JsonProcessor>(HomeTabProcessor())

        /**
         * 在 onPackageReady（libxposed 最早的 app 级时机，早于 ContentProvider/Application.onCreate）
         * 提前注册 FastJSON parseObject hook，赶在磁盘缓存读取前拦到 TabResponse 反序列化。
         *
         * 精确 hook 可能产生 TabResponse 的 3 个重载：2参 (String,Class) 缓存、4参 (String,Type,int,Feature[]) 网络、
         * 3参 (String,Type,Feature[])。**不用 hookAllMethods**——那会挂上高频的 parseObject(String) 等通用重载，
         * 拦到后按 result.javaClass.name 分发给已注册的 [JsonProcessor]。
         */
        fun earlyHook(classLoader: ClassLoader) {
            val fastJson = "com.alibaba.fastjson.JSON".findClassOrNull(classLoader)
                ?: run {
                    Log.d("JsonHook earlyHook: fastJson 类未加载，跳过")
                    return
                }
            val enabledProcessors = allProcessors
                .filter { it.shouldEnable() }
                .mapNotNull { processor -> processor.takeIf { it.init(classLoader) } }
                .groupBy { it.targetClassName }
            val dispatch = { r: Any ->
                enabledProcessors[r.javaClass.name]?.forEach { p ->
                    runCatching { p.process(r, classLoader) }
                        .onFailure { Log.e(it) }
                }
            }
            listOf(
                arrayOf(String::class.java, Class::class.java),
                arrayOf(String::class.java, Type::class.java, Int::class.javaPrimitiveType, "com.alibaba.fastjson.parser.Feature[]"),
                arrayOf(String::class.java, Type::class.java, "com.alibaba.fastjson.parser.Feature[]"),
            ).forEach { params ->
                fastJson.hookMethod("parseObject", *params) { chain ->
                    val r = chain.proceed() ?: return@hookMethod null
                    dispatch(r)
                    r
                }
            }
        }

    }

    override fun startHook() {
        Log.d("startHook: Json")

        val hidden = sPrefs.getBoolean("hidden", false)
        val purifyLivePopups = sPrefs.getStringSet("purify_live_popups", null) ?: setOf()
        val unlockPlayLimit = sPrefs.getBoolean("play_arc_conf", false)

        val accountMineClass =
            "tv.danmaku.bili.ui.main2.api.AccountMine".findClassOrNull(mClassLoader)
        val garbEntranceClass =
            "tv.danmaku.bili.ui.main2.api.AccountMine\$GarbEntrance".from(mClassLoader)
        val splashClass = "tv.danmaku.bili.ui.splash.SplashData".findClassOrNull(mClassLoader)
            ?: "tv.danmaku.bili.ui.splash.ad.model.SplashData".findClassOrNull(mClassLoader)
        val splashShowClass = "tv.danmaku.bili.ui.splash.ad.model.SplashShowData".findClassOrNull(mClassLoader)
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
        val searchReferralV2Class =
            "com.bilibili.search2.api.SearchReferral".findClassOrNull(mClassLoader)
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
        val liveRecommendCardGoodsClass =
            "com.bilibili.bililive.room.biz.shopping.beans.LiveShoppingRecommendCardGoodsDetail"
                .from(mClassLoader)
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
        val liveShoppingGotoBuyInfoClass =
            "com.bilibili.bililive.room.biz.shopping.beans.LiveShoppingGotoBuyInfo"
                .from(mClassLoader)
        val shareChannelsClass = "com.bilibili.lib.sharewrapper.online.api.ShareChannels"
            .from(mClassLoader)
        val channelItemClass = "com.bilibili.lib.sharewrapper.online.api.ShareChannels\$ChannelItem"
            .from(mClassLoader)

        instance.fastJsonClass?.hookMethod(
            instance.fastJsonParse(),
            String::class.java,
            Type::class.java,
            Int::class.javaPrimitiveType,
            "com.alibaba.fastjson.parser.Feature[]"
        ) { chain ->
            val origResult = chain.proceed()
            var result = origResult ?: return@hookMethod null
            if (result.javaClass == instance.generalResponseClass) {
                result = result.getObjectField("data") ?: return@hookMethod origResult
            }

            when (result.javaClass) {
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

                        // liveTip
                        // 就是通常在 我的 -> 创做中心 下 但不属于创作中心的提示
                        result.getObjectFieldOrNull("liveTip")?.let { liveTip ->
                            val text = liveTip.getObjectFieldAs<String>("text")
                            val id = liveTip.getObjectField("id").toString()
                            val url = liveTip.getObjectFieldAs<String>("url")
                            val showing = id !in hides
                            drawerItems.add(BottomItem(
                                name = text, id = id, uri = url, showing = showing
                            ))
                            if (!showing) {
                                result.setObjectField("liveTip", null)
                            }
                        }

                    }
                    accountMineClass.findFieldOrNull("vipSectionRight")?.set(result, null)
                    if (sPrefs.getBoolean("custom_theme", false)) {
                        if (instance.clientVersionCode >= 7480200) {
                            garbEntranceClass?.new()?.apply {
                                setObjectField("uri", "activity://navigation/theme/")
                            }?.let { result.setObjectField("garbEntrance", it) }
                        } else {
                            result.setObjectField("garbEntrance", null)
                        }
                    }
                }
                splashClass, splashShowClass -> if (sPrefs.getBoolean("purify_splash", false) &&
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
                searchReferralV2Class -> if (sPrefs.getBoolean(
                        "purify_search",
                        false
                    ) &&
                    sPrefs.getBoolean("hidden", false)
                ) {
                    result.javaClass.declaredFields.forEach {
                        it.isAccessible = true
                        it.set(result, it.type.let { type ->
                            when (type) {
                                Int::class.javaPrimitiveType -> 0
                                else -> null
                            }
                        })
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
                    result.getObjectFieldOrNullAs<ArrayList<*>>("result")?.forEach {
                        it?.setIntField("isPlayable", 1)
                    }
                }

                ogvApiResponseV2Class -> if (sPrefs.getBoolean("allow_download", false)) {
                    result.getObjectFieldOrNull("data")
                        ?.getObjectFieldOrNullAs<MutableList<*>>("epPlayableParams")
                        ?.forEach { it?.setIntField("playableType", 0) }
                }

                dmAdvertClass -> if (hidden && sPrefs.getBoolean("block_up_rcmd_ads", false))
                    result.setObjectField("ads", null)

                shareChannelsClass -> if (unlockPlayLimit) runCatchingOrNull {
                    val belowChannels = result.getObjectFieldAs<ArrayList<Any>?>("belowChannels")
                        .takeUnless { it.isNullOrEmpty() } ?: return@hookMethod origResult
                    var alreadyHas = false
                    var toInsertIdx = -1
                    belowChannels.forEachIndexed { idx, item ->
                        val shareChannel = item.getObjectFieldAs<String?>("shareChannel")
                        if (shareChannel == "PLAY_BACKGROUND_OFF")
                            toInsertIdx = idx
                        if (shareChannel == "LISTEN")
                            alreadyHas = true
                    }
                    if (alreadyHas || toInsertIdx == -1)
                        return@hookMethod origResult
                    val listenChannel = channelItemClass?.new()
                        ?.setObjectField("name", "听视频")
                        ?.setObjectField("shareChannel", "LISTEN")
                        ?.setObjectField(
                            "picture",
                            "https://i0.hdslb.com/bfs/share/f88d8c420a59ff1ca5975b38722408056e7337b7.png"
                        ) ?: return@hookMethod origResult
                    belowChannels.add(toInsertIdx, listenChannel)
                }
            }
            origResult
        }

        val searchRankClass = "com.bilibili.search.api.SearchRank".findClassOrNull(mClassLoader)
        val searchGuessClass =
            "com.bilibili.search.api.SearchReferral\$Guess".findClassOrNull(mClassLoader)
        val searchRankV2Class = "com.bilibili.search2.api.SearchRank".findClassOrNull(mClassLoader)
        val searchGuessV2Class = "com.bilibili.search2.api.SearchReferral\$Guess".findClassOrNull(mClassLoader)

        instance.fastJsonClass?.hookMethod(
            "parseArray",
            String::class.java,
            Class::class.java
        ) { chain ->
            val result = chain.proceed()
            @Suppress("UNCHECKED_CAST")
            val list = result as? MutableList<Any>
            when (chain.args[1] as Class<*>) {
                searchRankClass, searchGuessClass, searchRankV2Class, searchGuessV2Class ->
                    if (sPrefs.getBoolean("purify_search", false) && sPrefs.getBoolean(
                            "hidden",
                            false
                        )
                    ) {
                        list?.clear()
                    }
            }
            result
        }

        instance.fastJsonClass?.hookMethod(
            instance.fastJsonParse(),
            String::class.java,
            Type::class.java,
            "com.alibaba.fastjson.parser.Feature[]"
        ) { chain ->
            val origResult = chain.proceed()
            var returnResult = origResult
            var result = origResult ?: return@hookMethod null
            if (result.javaClass == instance.generalResponseClass)
                result = result.getObjectField("data") ?: return@hookMethod origResult

            when (result.javaClass) {
                liveShoppingInfoClass -> {
                    if (hidden && purifyLivePopups.contains("shoppingCard")) {
                        result.setObjectField("shoppingCardDetail", null)
                        result.runCatchingOrNull {
                            setObjectField("recommendCardDetail", null)
                        }
                    }
                    if (hidden && purifyLivePopups.contains("shoppingSelected"))
                        result.runCatchingOrNull {
                            setObjectField("selectedGoods", null)
                        }
                }

                liveGoodsCardInfoClass, liveRecommendCardGoodsClass -> {
                    if (hidden && purifyLivePopups.contains("shoppingCard"))
                        returnResult = null
                }

                biliLiveRoomInfoClass -> {
                    if (hidden && purifyLivePopups.contains("follow"))
                        result.getObjectFieldOrNull("functionCard")
                            ?.setObjectField("followCard", null)
                    if (hidden && purifyLivePopups.contains("banner"))
                        result.setObjectField("bannerInfo", null)
                    if (hidden && sPrefs.getBoolean("no_live_mask", false))
                        result.setObjectField("areaMaskInfo", null)
                }

                liveRoomRecommendCardClass -> {
                    if (hidden && purifyLivePopups.contains("follow"))
                        returnResult = null
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

                liveShoppingGotoBuyInfoClass -> {
                    if (hidden && purifyLivePopups.contains("gotoBuy"))
                        returnResult = null
                }
            }
            returnResult
        }
    }
}

