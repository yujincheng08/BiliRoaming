package me.iacn.biliroaming.hook

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.*
import me.iacn.biliroaming.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.Constant.TYPE_EPISODE_ID
import me.iacn.biliroaming.Constant.TYPE_SEASON_ID
import me.iacn.biliroaming.network.BiliRoamingApi
import me.iacn.biliroaming.network.BiliRoamingApi.getAreaSearchBangumi
import me.iacn.biliroaming.network.BiliRoamingApi.getContent
import me.iacn.biliroaming.network.BiliRoamingApi.getSeason
import me.iacn.biliroaming.utils.*
import org.json.JSONObject
import java.io.InputStream
import java.lang.reflect.Method
import java.net.URL
import java.net.URLDecoder
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import java.lang.reflect.Array as RArray


/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */

class BangumiSeasonHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        val lastSeasonInfo: MutableMap<String, String?> = HashMap()

        private val jsonNonStrict = lazy {
            instance.kotlinJsonClass?.getStaticObjectField("Companion")?.callMethod("getNonstrict")
        }
        const val FAIL_CODE = -404

        data class Area(val area: String, val text: String, val type: String, val type_str: String)

        private val AREA_TYPES =
            mapOf(
                931 to Area("cn", "陆(影)", "8", "movie"),
                364364 to Area("hk", "港(影)", "8", "movie"),
                889464 to Area("tw", "台(影)", "8", "movie"),
                114 to Area("th", "泰", "7", "bangumi"),
                514 to Area("cn", "陆", "7", "bangumi"),
                1919 to Area("hk", "港", "7", "bangumi"),
                810 to Area("tw", "台", "7", "bangumi")
            )
    }

    private val isSerializable by lazy {
        "com.bilibili.bangumi.data.page.detail.entity.BangumiUniformSeason\$\$serializer".findClassOrNull(
            mClassLoader
        ) != null
    }

    private val isGson by lazy {
        instance.bangumiUniformSeasonClass?.annotations?.fold(false) { last, it ->
            last || it.annotationClass.java.name.startsWith("gsonannotator")
        } ?: false && instance.gsonFromJson() != null && instance.gsonToJson() != null
    }

    private val gson by lazy {
        instance.gson()?.let { instance.gsonConverterClass?.getStaticObjectField(it) }
    }

    private val serializerFeatures = lazy {
        val serializerFeatureClass =
            "com.alibaba.fastjson.serializer.SerializerFeature".findClassOrNull(mClassLoader)
                ?: return@lazy null
        val keyAsString = serializerFeatureClass.getStaticObjectField("WriteNonStringKeyAsString")
        val noDefault = serializerFeatureClass.getStaticObjectField("NotWriteDefaultValue")
        val serializerFeatures = RArray.newInstance(serializerFeatureClass, 2)
        RArray.set(serializerFeatures, 0, keyAsString)
        RArray.set(serializerFeatures, 1, noDefault)
        serializerFeatures
    }

    private fun Any.toJson() = when {
        isSerializable -> jsonNonStrict.value?.callMethodAs<String>(
            "stringify",
            javaClass.getStaticObjectField("Companion")?.callMethod("serializer"),
            this
        ).toJSONObject()
        isGson -> gson?.callMethodAs<String>(instance.gsonToJson(), this)?.toJSONObject()
        else -> instance.fastJsonClass?.callStaticMethodAs<String>(
            "toJSONString",
            this,
            serializerFeatures.value
        ).toJSONObject()
    }

    private fun Class<*>.fromJson(json: String) = when {
        isSerializable -> jsonNonStrict.value?.callMethod(
            "parse",
            getStaticObjectField("Companion")?.callMethod("serializer"),
            json
        )
        isGson -> gson?.callMethod(instance.gsonFromJson(), json, this)
        else -> instance.fastJsonClass?.callStaticMethod(instance.fastJsonParse(), json, this)
    }

    private fun Class<*>.fromJson(json: JSONObject) = fromJson(json.toString())

    private fun updateSeasonInfo(args: Array<*>, paramMap: Map<String, String>) {
        lastSeasonInfo.clear()
        val seasonMode = when {
            args[1] is Int -> args[1] as Int
            args[3] != "0" && paramMap.containsKey("ep_id") -> TYPE_EPISODE_ID
            args[2] != "0" && paramMap.containsKey("season_id") -> TYPE_SEASON_ID
            else -> -1
        }
        when (seasonMode) {
            TYPE_SEASON_ID -> lastSeasonInfo["season_id"] = paramMap["season_id"]
            TYPE_EPISODE_ID -> lastSeasonInfo["ep_id"] = paramMap["ep_id"]
            else -> return
        }
    }

    private val searchAllResultClass by Weak {
        "com.bilibili.search.api.SearchResultAll".findClassOrNull(
            mClassLoader
        )
    }
    private val searchAllResultNavInfoClass by Weak {
        "com.bilibili.search.api.SearchResultAll\$NavInfo".findClassOrNull(
            mClassLoader
        )
    }
    private val bangumiSearchPageClass by Weak {
        "com.bilibili.bangumi.data.page.search.BangumiSearchPage".findClassOrNull(
            mClassLoader
        )
    }
    private val biliSearchOgvResultClass by Weak {
        "com.bilibili.search.ogv.BiliSearchOgvResult".findClassOrNull(
            mClassLoader
        )
    }
    private val baseSearchItemClass by Weak {
        "com.bilibili.search.api.BaseSearchItem".findClassOrNull(
            mClassLoader
        )
    }

    override fun startHook() {
        if (!sPrefs.getBoolean("main_func", false)) return
        Log.d("startHook: BangumiSeason")
        instance.seasonParamsMapClass?.hookAfterAllConstructors { param ->
            @Suppress("UNCHECKED_CAST")
            val paramMap = param.thisObject as Map<String, String>
            updateSeasonInfo(param.args, paramMap)
        }

        instance.seasonParamsClass?.hookAfterAllConstructors { param ->
            val paramMap =
                param.thisObject.callMethodAs<Map<String, String>>(instance.paramsToMap())
            updateSeasonInfo(param.args, paramMap)
        }

        "com.bilibili.bangumi.ui.page.detail.BangumiCommentInvalidFragmentV2".hookAfterMethod(
            mClassLoader,
            "onViewCreated",
            View::class.java,
            Bundle::class.java
        ) { param ->
            if (lastSeasonInfo["allow_comment"] != "0" ||
                sPrefs.getBoolean("force_th_comment", false)
            ) return@hookAfterMethod
            (param.args[0] as? View)?.run {
                @SuppressLint("SetTextI18n")
                findViewById<TextView>(getId("info"))?.text = "由于泰区番剧评论会串到其他正常视频中，\n因而禁用泰区评论，还望理解。"
                findViewById<ImageView>(getId("forbid_icon"))?.run {
                    MainScope().launch {
                        @Suppress("BlockingMethodInNonBlockingContext") val bitmap =
                            withContext(Dispatchers.IO) {
                                val connection =
                                    URL("https://i0.hdslb.com/bfs/album/08d5ce2fef8da8adf91024db4a69919b8d02fd5c.png").openConnection()
                                connection.connect()
                                val input: InputStream = connection.getInputStream()
                                BitmapFactory.decodeStream(input)
                            }
                        setImageBitmap(bitmap)
                    }
                }
            }
        }

        "com.bilibili.bangumi.ui.page.detail.BangumiDetailActivityV3".hookAfterMethod(
            mClassLoader, "onCreate", Bundle::class.java
        ) {
            setErrorMessage(it.thisObject as Activity)
        }
        "com.bilibili.bangumi.ui.page.detail.BangumiDetailActivityV3".hookAfterMethod(
            mClassLoader, "onConfigurationChanged", Configuration::class.java
        ) {
            setErrorMessage(it.thisObject as Activity)
        }

        if (isBuiltIn && is64 && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.e("Not support")
            Log.toast("Android O以下系统不支持64位Xpatch版，请使用32位版")
        } else {
            instance.retrofitResponseClass?.hookBeforeAllConstructors { param ->
                val url = getUrl(param.args[0])
                val body = param.args[1] ?: return@hookBeforeAllConstructors
                if (url?.startsWith("https://api.bilibili.com/pgc/view/v2/app/season") == true &&
                    body.javaClass == instance.okioWrapperClass
                ) {
                    val okioBuffer = body.getObjectField(instance.okio())
                    val json =
                        okioBuffer?.callMethodAs<InputStream?>(instance.okioInputStream())?.use {
                            it.readBytes().toString(Charsets.UTF_8).toJSONObject()
                        }?.apply {
                            put(
                                "data",
                                fixBangumi(optJSONObject("data"), optInt("code", FAIL_CODE), url)
                            )
                            put("code", 0)
                        }
                    val newStream = json?.toString()?.byteInputStream()
                    body.setObjectField(
                        instance.okio(),
                        okioBuffer?.javaClass?.newInstance()?.apply {
                            callMethod(instance.okioReadFrom(), newStream)
                        })
                    body.setLongField(instance.okioLength(), newStream?.available()?.toLong() ?: 0L)
                }
                // Filter non-bangumi responses
                // If it isn't bangumi, the type variable will not exist in this map
                if (instance.bangumiApiResponseClass?.isInstance(body) == true ||
                    // for new blue 6.3.7
                    instance.rxGeneralResponseClass?.isInstance(body) == true
                ) {
                    fixBangumi(body, url)
                }
                if (url != null && url.startsWith("https://appintl.biliapi.net/intl/gateway/app/search/type")
                ) {
                    val area = Uri.parse(url).getQueryParameter("type")?.toInt()
                    if (!AREA_TYPES.containsKey(area)) {
                        fixPlaySearchType(body, url)
                    }
                }
                if (instance.generalResponseClass?.isInstance(body) == true ||
                    instance.rxGeneralResponseClass?.isInstance(body) == true
                ) {
                    val dataField =
                        if (instance.generalResponseClass?.isInstance(body) == true) "data" else instance.responseDataField()
                    val data = body.getObjectField(dataField)
                    if (data?.javaClass == searchAllResultClass) {
                        addAreaTags(data)
                    }
                    url ?: return@hookBeforeAllConstructors
                    if (url.startsWith("https://app.bilibili.com/x/v2/search/type") || url.startsWith(
                            "https://appintl.biliapi.net/intl/gateway/app/search/type"
                        )
                    ) {
                        val area = Uri.parse(url).getQueryParameter("type")?.toInt()
                        if (AREA_TYPES.containsKey(area)) {
                            if (data?.javaClass == bangumiSearchPageClass) {
                                body.setObjectField(dataField, AREA_TYPES[area]?.let {
                                    retrieveAreaSearch(
                                        data, url, it.area, it.type
                                    )
                                })
                            } else if (data?.javaClass == biliSearchOgvResultClass) {
                                body.setObjectField(dataField, AREA_TYPES[area]?.let {
                                    retrieveAreaSearchV2(
                                        data, url, it.area, it.type
                                    )
                                })
                            }
                        }
                    } else if (url.startsWith("https://app.bilibili.com/x/v2/view?") ||
                        url.startsWith("https://app.bilibili.com/x/intl/view?") ||
                        url.startsWith("https://appintl.biliapi.net/intl/gateway/app/view?") &&
                        body.getIntField("code") == FAIL_CODE
                    ) {
                        fixView(data, url)?.let {
                            body.setObjectField(dataField, it)
                            body.setIntField("code", 0)
                        }
                    } else if (url.startsWith("https://app.bilibili.com/x/v2/space?")) {
                        val mid = Uri.parse(url).getQueryParameter("vmid")
                        var code = body.getIntFieldOrNull("code")
                        if (code != 0 && sPrefs.getBoolean("fix_space", false)) {
                            val content = getContent("https://account.bilibili.com/api/member/getCardByMid?callback=userinfo&mid=" + mid)
                            code = content?.toJSONObject()?.getIntFieldOrNull("code")
                            if (code != 0) {
                                body.setObjectField(dataField,
                                    mid?.let { fixSpace(data, it, content.toString()) })
                                body.setIntField("code", 0)
                            }
                        }
                    }
                }
            }
        }

        "com.bapis.bilibili.app.view.v1.ViewMoss".findClassOrNull(mClassLoader)
            ?.hookAfterMethod("view", "com.bapis.bilibili.app.view.v1.ViewReq") { param ->
                param.result?.let { return@hookAfterMethod }
                val serializedRequest = param.args[0].callMethodAs<ByteArray>("toByteArray")
                val req = API.ViewReq.parseFrom(serializedRequest)
                val reply = fixViewProto(req)
                val serializedReply = reply?.toByteArray() ?: return@hookAfterMethod
                param.result = (param.method as Method).returnType.callStaticMethod(
                    "parseFrom",
                    serializedReply
                )
            }

        val urlHook: Hooker = fun(param) {
            val redirectUrl = param.thisObject.getObjectFieldAs<String?>("redirectUrl")
            if (redirectUrl.isNullOrEmpty()) return
            param.result = param.thisObject.callMethod("getUrl", redirectUrl)
        }
        "com.bilibili.bplus.followingcard.api.entity.cardBean.VideoCard".findClassOrNull(
            mClassLoader
        )
            ?.run {
                hookAfterMethod("getJumpUrl", hooker = urlHook)
                hookAfterMethod("getCommentJumpUrl", hooker = urlHook)
            }

        if (sPrefs.getBoolean("hidden", false) &&
            (sPrefs.getBoolean(
                "search_area_bangumi",
                false
            ) || sPrefs.getBoolean("search_area_movie", false))
        ) {
            val searchResultFragment =
                "com.bilibili.bangumi.ui.page.search.BangumiSearchResultFragment".findClassOrNull(
                    mClassLoader
                ) ?: "com.bilibili.search.ogv.OgvSearchResultFragment".findClassOrNull(mClassLoader)
            searchResultFragment?.run {
                hookBeforeMethod(
                    "setUserVisibleCompat",
                    Boolean::class.javaPrimitiveType
                ) { param ->
                    param.thisObject.callMethodAs<Bundle>("getArguments").run {
                        val from = getString("from")
                        for (area in AREA_TYPES) {
                            if (from == area.value.area) {
                                declaredFields.filter {
                                    it.type == Int::class.javaPrimitiveType
                                }.forEach {
                                    it.isAccessible = true
                                    if (it.get(param.thisObject) == area.value.type.toInt()) it.set(
                                        param.thisObject,
                                        area.key
                                    )
                                }
                            }
                        }
                    }
                }
            }
            val pageTypesClass = Class.forName(
                "com.bilibili.search.result.pages.BiliMainSearchResultPage\$PageTypes",
                true,
                mClassLoader
            )
            val pageArrays = pageTypesClass.getStaticObjectFieldAs<Array<Any>>("\$VALUES")
            var counter = 0
            for (area in AREA_TYPES) {
                if (!sPrefs.getString(area.value.area + "_server", null).isNullOrBlank()) {
                    counter++
                }
            }
            val newPageArray = pageArrays.copyOf(pageArrays.size + counter)
            counter = 0
            for (area in AREA_TYPES) {
                if (!sPrefs.getString(area.value.area + "_server", null).isNullOrBlank()) {
                    newPageArray[pageArrays.size + counter++] = pageTypesClass.new(
                        "PAGE_" + area.value.type_str.uppercase(Locale.getDefault()),
                        4,
                        "bilibili://search-result/new-" + area.value.type_str + "?from=" + area.value.area,
                        area.key,
                        area.value.type_str
                    )
                }
            }
            pageTypesClass.setStaticObjectField("\$VALUES", newPageArray)
        }
    }

    private fun fixSpace(data: Any?, mid: String, content: String): Any? {
        instance.biliSpaceClass ?: return data
        val card = content.toJSONObject().optJSONObject("card") ?: return data
        val name = card.optString("name")
        val sex = card.optString("sex")
        val rank = card.optString("rank")
        val face = card.optString("face")
        var sign = card.optString("sign")
        if (sign != "") sign = "，原签名："+sign
        val fans = card.optInt("fans").toString()
        val friend = card.optInt("friend").toString()
        val attention = card.optInt("attention").toString()
        val current_level = card.optJSONObject("level_info")?.optInt("current_level").toString()
        val current_min = card.optJSONObject("level_info")?.optInt("current_min").toString()
        val current_exp = card.optJSONObject("level_info")?.optInt("current_exp").toString()
        val next_exp = card.optJSONObject("level_info")?.optInt("next_exp").toString()
        val official_verify_type = card.optJSONObject("official_verify")?.optInt("type").toString()
        val official_verify_desc = card.optJSONObject("official_verify")?.optString("desc")
        return instance.fastJsonClass?.callStaticMethod(
            instance.fastJsonParse(),
            """{"relation":-999,"guest_relation":-999,"default_tab":"video","is_params":true,"setting":{"fav_video":0,"coins_video":0,"likes_video":0,"bangumi":0,"played_game":0,"groups":0,"comic":0,"bbq":0,"dress_up":0,"disable_following":0,"live_playback":1,"close_space_medal":0,"only_show_wearing":0},"tab":{"archive":true,"article":true,"clip":true,"album":true,"favorite":false,"bangumi":false,"coin":false,"like":false,"community":false,"dynamic":true,"audios":true,"shop":false,"mall":false,"ugc_season":false,"comic":false,"cheese":false,"sub_comic":false,"activity":false,"series":false},"card":{"mid":"$mid","name":""""+name+"""","approve":false,"sex":""""+sex+"""","rank":""""+rank+"""","face":""""+face+"""","DisplayRank":"","regtime":0,"spacesta":0,"birthday":"","place":"","description":"该页面由哔哩漫游修复","article":0,"attentions":null,"fans":"""+fans+""","friend":"""+friend+""","attention":"""+attention+""","sign":"该页面由哔哩漫游修复"""+sign+"""","level_info":{"current_level":"""+current_level+""","current_min":"""+current_min+""","current_exp":"""+current_exp+""","next_exp":""""+next_exp+""""},"pendant":{"pid":0,"name":"","image":"","expire":0,"image_enhance":"","image_enhance_frame":""},"nameplate":{"nid":0,"name":"","image":"","image_small":"","level":"","condition":""},"official_verify":{"type":"""+official_verify_type+""","desc":""""+official_verify_desc+"""","role":3,"title":""""+official_verify_desc+""""},"vip":{"vipType":0,"vipDueDate":0,"dueRemark":"","accessStatus":0,"vipStatus":0,"vipStatusWarn":"","themeType":0,"label":{"path":"","text":"","label_theme":"","text_color":"","bg_style":0,"bg_color":"","border_color":""}},"silence":0,"end_time":0,"silence_url":"","likes":{"like_num":0,"skr_tip":"该页面由哔哩漫游修复"},"pr_info":{},"relation":{"status":1},"is_deleted":0,"honours":{"colour":{"dark":"#CE8620","normal":"#F0900B"},"tags":null},"profession":{}},"images":{"imgUrl":"","night_imgurl":"","has_garb":true,"goods_available":true},"live":{"roomStatus":0,"roundStatus":0,"liveStatus":0,"url":"","title":"","cover":"","online":0,"roomid":0,"broadcast_type":0,"online_hidden":0,"link":""},"archive":{"order":[{"title":"最新发布","value":"pubdate"},{"title":"最多播放","value":"click"}],"count":9999,"item":[]},"series":{"item":[]},"play_game":{"count":0,"item":[]},"article":{"count":0,"item":[],"lists_count":0,"lists":[]},"season":{"count":0,"item":[]},"coin_archive":{"count":0,"item":[]},"like_archive":{"count":0,"item":[]},"audios":{"count":0,"item":[]},"favourite2":{"count":0,"item":[]},"comic":{"count":0,"item":[]},"ugc_season":{"count":0,"item":[]},"cheese":{"count":0,"item":[]},"fans_effect":{},"tab2":[{"title":"动态","param":"dynamic"},{"title":"投稿","param":"contribute","items":[{"title":"视频","param":"video"}]}]}""",
            instance.biliSpaceClass
        ) ?: data
    }

    private fun retrieveAreaSearchV2(data: Any?, url: String, area: String, type: String): Any? {
        data ?: return data
        if (sPrefs.getBoolean("hidden", false) && (sPrefs.getBoolean(
                "search_area_bangumi", false
            ) || sPrefs.getBoolean("search_area_movie", false))
        ) {
            val content = getAreaSearchBangumi(
                URL(URLDecoder.decode(url, Charsets.UTF_8.name())).query, area, type
            ) ?: return data
            val jsonContent = content.toJSONObject()
            checkErrorToast(jsonContent, true)
            val newData = jsonContent.optJSONObject("data") ?: return data

            val newItems = mutableListOf<Any>()
            for (item in newData.getJSONArray("items")) {
                // 去除追番按钮
                if (item.optInt("Offset", -1) != -1) {
                    item.remove("follow_button")
                }
                val goto = baseSearchItemClass?.getStaticObjectFieldAs<Map<String, Any>>("sMap")
                    ?.get(item.optString("goto"))
                instance.fastJsonClass?.callStaticMethod(
                    instance.fastJsonParse(), item.toString(), goto?.callMethod("getImpl")
                )?.let {
                    it.setIntField("viewType", goto?.callMethodAs<String>("getLayout").hashCode())
                    newItems.add(it)
                }
            }
            return data.javaClass.new()
                .setObjectField("items", newItems)
                .setIntField("totalPages", newData.optInt("pages"))
        } else {
            return data
        }
    }

    private fun retrieveAreaSearch(data: Any?, url: String, area: String, type: String): Any? {
        data ?: return data
        if (sPrefs.getBoolean("hidden", false) &&
            (sPrefs.getBoolean(
                "search_area_bangumi",
                false
            ) || sPrefs.getBoolean("search_area_movie", false))
        ) {
            val content =
                getAreaSearchBangumi(
                    URL(URLDecoder.decode(url, Charsets.UTF_8.name())).query,
                    area,
                    type
                )
                    ?: return data
            val jsonContent = content.toJSONObject()
            checkErrorToast(jsonContent, true)
            val newData = jsonContent.optJSONObject("data") ?: return data

            // 去除追番按钮
            for (item in newData.getJSONArray("items")) {
                if (item.optInt("Offset", -1) != -1) {
                    item.remove("follow_button")
                }
            }

            return instance.fastJsonClass?.callStaticMethod(
                instance.fastJsonParse(),
                newData.toString(),
                data.javaClass
            ) ?: data
        } else {
            return data
        }
    }

    private fun addAreaTags(body: Any?) {
        body ?: return
        if (sPrefs.getBoolean("hidden", false) &&
            (sPrefs.getBoolean(
                "search_area_bangumi",
                false
            ) || sPrefs.getBoolean("search_area_movie", false))
        ) {
            for (area in AREA_TYPES) {
                if (runCatching {
                        XposedInit.country.get(
                            5L,
                            TimeUnit.SECONDS
                        )
                    }.getOrNull() == area.value.area) {
                    continue
                }
                if (!sPrefs.getString(area.value.area + "_server", null).isNullOrBlank() &&
                    sPrefs.getBoolean("search_area_" + area.value.type_str, false)
                ) {
                    searchAllResultNavInfoClass?.new()?.run {
                        setObjectField("name", area.value.text)
                        setIntField("pages", 0)
                        setIntField("total", 0)
                        setIntField("type", area.key)
                    }?.also {
                        body.getObjectFieldAs<MutableList<Any>>("nav").add(1, it)
                    }
                }
            }
        }
    }

    private fun fixPlaySearchType(body: Any, url: String) {
        val dataField =
            if (instance.generalResponseClass?.isInstance(body) == true) "data" else instance.responseDataField()
        val resultClass = body.getObjectField(dataField)?.javaClass ?: return
        if (!url.contains("type=7") && !url.contains("type=8")) return
        val newUrl = url.replace("appintl.biliapi.net/intl/gateway/app/", "app.bilibili.com/x/v2/")
        val content = getContent(newUrl)?.toJSONObject()?.optJSONObject("data") ?: return
        val newResult = resultClass.fromJson(content) ?: return
        body.setObjectField(dataField, newResult)
    }

    private fun fixBangumi(jsonResult: JSONObject?, code: Int, url: String?) =
        if (isBangumiWithWatchPermission(jsonResult, code)) {
            jsonResult?.also { allowDownload(it); fixEpisodesStatus(it) }
        } else {
            url?.let { Uri.parse(it) }?.run {
                getQueryParameter("ep_id")?.let {
                    lastSeasonInfo.clear()
                    lastSeasonInfo["ep_id"] = it
                }
                getQueryParameter("season_id")?.let {
                    lastSeasonInfo.clear()
                    lastSeasonInfo["season_id"] = it
                }
            }
            Log.toast("发现区域限制番剧，尝试解锁……")
            Log.d("Info: $lastSeasonInfo")
            val (newCode, newJsonResult) = getSeason(
                lastSeasonInfo,
                jsonResult == null
            )?.toJSONObject()?.let {
                it.optInt("code", FAIL_CODE) to it.optJSONObject("result")
            } ?: (FAIL_CODE to null)
            if (isBangumiWithWatchPermission(newJsonResult, newCode)) {
                lastSeasonInfo["title"] = newJsonResult?.optString("title")
                lastSeasonInfo["season_id"] = newJsonResult?.optString("season_id")
                lastSeasonInfo["watch_platform"] =
                    newJsonResult?.optJSONObject("rights")?.apply {
                        if (has("allow_comment") && getInt("allow_comment") == 0) {
                            remove("allow_comment")
                            put("area_limit", 1)
                            lastSeasonInfo["allow_comment"] = "0"
                        }
                    }?.optInt("watch_platform")?.toString()
                for (episode in newJsonResult?.optJSONArray("episodes").orEmpty()) {
                    if (episode.has("cid") && episode.has("id")) {
                        val cid = episode.optInt("cid").toString()
                        val epId = episode.optInt("id").toString()
                        lastSeasonInfo[cid] = epId
                        lastSeasonInfo["ep_ids"] = lastSeasonInfo["ep_ids"]?.let { "$it;$epId" }
                            ?: epId
                        episode.optJSONArray("subtitles")?.let {
                            if (it.length() > 0) {
                                lastSeasonInfo["sb$cid"] = it.toString()
                            }
                        }
                    }
                }
                allowDownload(newJsonResult, false)
                fixEpisodesStatus(newJsonResult)
            }
            jsonResult?.apply {
                // Replace only episodes and rights
                // Remain user information, such as follow status, watch progress, etc.
                put("rights", newJsonResult?.optJSONObject("rights"))
                put("episodes", newJsonResult?.optJSONArray("episodes"))
                remove("limit")
                put("modules", newJsonResult?.optJSONArray("modules"))
                put("section", newJsonResult?.optJSONArray("section"))
                put("prevueSection", newJsonResult?.optJSONArray("prevueSection"))
                remove("dialog")
            } ?: newJsonResult
        }

    private fun fixBangumi(body: Any, url: String?) {
        val fieldName = "_data"
        //if (isSerializable || isGson) instance.responseDataField().value else "result"
        val result = body.getObjectField(fieldName)
        val code = body.getIntField("code")
        if (instance.bangumiUniformSeasonClass?.isInstance(result) != true && code != FAIL_CODE) return
        if (url?.contains("cards?") == true) return
        val jsonResult = result?.toJson()
        // Filter normal bangumi and other responses
        fixBangumi(jsonResult, code, url)?.let {
            body.setIntField("code", 0)
                .setObjectField(fieldName, instance.bangumiUniformSeasonClass?.fromJson(it))
        } ?: run {
            lastSeasonInfo.clear()
        }
    }

    private fun fixViewProto(req: API.ViewReq): API.ViewReply? {
        val av = when {
            req.hasAid() -> req.aid.toString()
            req.hasBvid() -> bv2av(req.bvid).toString()
            else -> return null
        }
        val query = Uri.Builder().run {
            appendQueryParameter("id", av)
            appendQueryParameter("bvid", req.bvid.toString())
            appendQueryParameter("from", req.from.toString())
            appendQueryParameter("trackid", req.trackid.toString())
            appendQueryParameter("ad_extra", req.adExtra.toString())
            appendQueryParameter("qn", req.qn.toString())
            appendQueryParameter("fnver", req.fnver.toString())
            appendQueryParameter("fnval", req.fnval.toString())
            appendQueryParameter("force_host", req.forceHost.toString())
            appendQueryParameter("fourk", req.fourk.toString())
            appendQueryParameter("spmid", req.spmid.toString())
            appendQueryParameter("autoplay", req.autoplay.toString())
            build()
        }.query

        val content = BiliRoamingApi.getView(query)?.toJSONObject() ?: return null
        val result = content.optJSONObject("v2_app_api")
        if (result?.has("season") != true) return null

        return viewReply {
            arc = arc {
                author = author {
                    result.optJSONObject("owner")?.run {
                        mid = optLong("mid")
                        face = optString("face")
                        name = optString("name")
                    }
                }
                dimension = dimension {
                    result.optJSONObject("dimension")?.run {
                        width = optLong("width")
                        height = optLong("height")
                        rotate = optLong("rotate")
                    }
                }
                stat = stat {
                    result.optJSONObject("stat")?.run {
                        aid = optLong("aid")
                        view = optInt("view")
                        danmaku = optInt("danmaku")
                        reply = optInt("reply")
                        fav = optInt("favorite")
                        coin = optInt("coin")
                        share = optInt("share")
                        nowRank = optInt("now_rank")
                        hisRank = optInt("his_rank")
                        like = optInt("like")
                        dislike = optInt("dislike")
                    }
                }
                result.run {
                    aid = optLong("aid")
                    attribute = optInt("attribute")
                    copyright = optInt("copyright")
                    ctime = optLong("ctime")
                    desc = optString("desc")
                    duration = optLong("duration")
                    firstCid = optLong("cid")
                    pic = optString("pic")
                    pubdate = optLong("pubdate")
                    redirectUrl = optString("redirect_url")
                    state = optInt("state")
                    title = optString("title")
                    typeId = optInt("tid")
                    typeName = optString("tname")
                    videos = optLong("videos")
                }
            }
            bvid = result.optString("bvid")
            season = season {
                result.optJSONObject("season")?.run {
                    allowDownload = "1"
                    seasonId = optString("season_id").toLong()
                    title = optString("title")
                    cover = optString("cover")
                    isFinish = optString("is_finish").toInt()
                    newestEpIndex = optString("newest_ep_index")
                    newestEpid = optString("newest_ep_id").toInt()
                    totalCount = optString("total_count").toLong()
                    weekday = optString("weekday").toInt()
                    ovgPlayurl = optString("ogv_play_url")
                    isJump = optInt("is_jump")
                }
            }
            val pages = result.optJSONArray("pages")
            for (page in pages.orEmpty()) {
                this.pages += viewPage {
                    downloadSubtitle = page.optString("download_subtitle")
                    downloadTitle = page.optString("download_title")
                    this.page = page {
                        this.page = page.optInt("page")
                        page.run {
                            cid = optLong("cid")
                            from = optString("from")
                            part = optString("part")
                            duration = optLong("duration")
                            vid = optString("vid")
                            webLink = optString("weblink")
                        }
                        dimension = dimension {
                            page.optJSONObject("dimension")?.run {
                                width = optLong("width")
                                height = optLong("height")
                                rotate = optLong("rotate")
                            }
                        }
                    }
                }
            }
            shortLink = result.optString("short_link")
            result.optJSONObject("t_icon")?.let {
                for (key in it.keys()) {
                    this.tIcon[key] = tIcon {
                        icon = it.optJSONObject(key)?.optString("icon") ?: ""
                    }
                }
            }
            val tags = result.optJSONArray("tag")
            for (tag in tags.orEmpty()) {
                this.tag += tag {
                    tag.run {
                        id = optLong("tag_id")
                        name = optString("tag_name")
                        likes = optLong("likes")
                        liked = optInt("liked")
                        hates = optLong("hates")
                        hated = optInt("hated")
                        tagType = optString("tag_type")
                        uri = optString("uri")
                    }
                }
            }
        }
    }

    private fun fixView(data: Any?, urlString: String): Any? {
        val uri = Uri.parse(urlString)
        val av = uri.getQueryParameter("aid")?.takeIf {
            it != "0"
        } ?: uri.getQueryParameter("bvid")?.let {
            bv2av(it).toString()
        } ?: return null
        val queryString = uri.encodedQuery + "&id=$av"
        val content = BiliRoamingApi.getView(queryString) ?: return data
        val detailClass = instance.biliVideoDetailClass ?: return data
        val newJsonResult = content.toJSONObject().optJSONObject("v2_app_api") ?: return data
        newJsonResult.optJSONObject("season")?.optString("newest_ep_id")?.let {
            lastSeasonInfo["ep_id"] = it
        }
        return detailClass.fromJson(newJsonResult)
    }

    private fun isBangumiWithWatchPermission(result: JSONObject?, code: Int) = result?.let {
        result.optJSONObject("rights")
            ?.run { !optBoolean("area_limit", true) || optInt("area_limit", 1) == 0 }
    } ?: run { code != FAIL_CODE }


    private fun allowDownload(result: JSONObject?, toast: Boolean = true) {
        if (sPrefs.getBoolean("allow_download", false)) {
            val rights = result?.optJSONObject("rights")
            rights?.put("allow_download", 1)
            rights?.put("only_vip_download", 0)
            for (module in result?.optJSONArray("modules").orEmpty()) {
                val data = module.optJSONObject("data")
                val moduleEpisodes = data?.optJSONArray("episodes")
                for (episode in moduleEpisodes.orEmpty()) {
                    episode.optJSONObject("rights")?.put("allow_download", 1)
                }
            }
            for (section in result?.optJSONArray("prevueSection").orEmpty()) {
                for (episode in section.optJSONArray("episodes").orEmpty()) {
                    episode.optJSONObject("rights")?.put("allow_download", 1)
                }
            }
            for (episode in result?.optJSONArray("episodes").orEmpty()) {
                episode.optJSONObject("rights")?.put("allow_download", 1)
            }
            Log.d("Download allowed")
            if (toast) Log.toast("已允许下载")
        }
    }

    private fun fixEpisodesStatus(result: JSONObject?) {
        sPrefs.getString("cn_server_accessKey", null) ?: return
        if (result?.optInt("status") == 13) result.put("status", 2)
        for (module in result?.optJSONArray("modules").orEmpty()) {
            val data = module.optJSONObject("data")
            val moduleEpisodes = data?.optJSONArray("episodes")
            for (episode in moduleEpisodes.orEmpty()) {
                if (episode.optInt("status") == 13) episode.put("status", 2)
            }
        }
        for (section in result?.optJSONArray("prevueSection").orEmpty()) {
            for (episode in section.optJSONArray("episodes").orEmpty()) {
                if (episode.optInt("status") == 13) episode.put("status", 2)
            }
        }
        for (episode in result?.optJSONArray("episodes").orEmpty()) {
            if (episode.optInt("status") == 13) episode.put("status", 2)
        }
    }

    private fun getUrl(response: Any): String? {
        val requestField = instance.requestField() ?: return null
        val urlField = instance.urlField() ?: return null
        val request = response.getObjectField(requestField)
        return request?.getObjectField(urlField)?.toString()
    }

    private fun setErrorMessage(activity: Activity) {
        val job = MainScope().launch {
            val id = getId("tv_desc")
            while (true) {
                if (activity.isDestroyed) break
                val tvDesc = activity.findViewById<TextView>(id)
                if (tvDesc == null) {
                    delay(500)
                    continue
                }
                tvDesc.maxLines = Int.MAX_VALUE
                (tvDesc.parent as View).setOnClickListener {
                    val lines = tvDesc.text.lines()
                    val title = lines[0]
                    val message = lines.subList(1, lines.size)
                        .fold(StringBuilder()) { sb, line -> sb.appendLine(line) }
                    AlertDialog.Builder(tvDesc.context)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
                break
            }
        }
        MainScope().launch {
            delay(15_000)
            job.cancel()
        }
    }
}
