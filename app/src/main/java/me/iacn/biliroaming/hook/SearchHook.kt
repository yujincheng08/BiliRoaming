package me.iacn.biliroaming.hook

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import me.iacn.biliroaming.API.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.CheckMoreKt
import me.iacn.biliroaming.EpisodeKt
import me.iacn.biliroaming.EpisodeNewKt
import me.iacn.biliroaming.FollowButtonKt
import me.iacn.biliroaming.ReasonStyleKt
import me.iacn.biliroaming.SearchBangumiCardKt
import me.iacn.biliroaming.SearchItemKt
import me.iacn.biliroaming.WatchButtonKt
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.checkMore
import me.iacn.biliroaming.episode
import me.iacn.biliroaming.episodeNew
import me.iacn.biliroaming.followButton
import me.iacn.biliroaming.hook.BangumiSeasonHook.Companion.AREA_TYPES
import me.iacn.biliroaming.network.BiliRoamingApi.getAreaSearchBangumi
import me.iacn.biliroaming.paginationReply
import me.iacn.biliroaming.reasonStyle
import me.iacn.biliroaming.searchBangumiCard
import me.iacn.biliroaming.searchByTypeResponse
import me.iacn.biliroaming.searchItem
import me.iacn.biliroaming.searchNav
import me.iacn.biliroaming.utils.Weak
import me.iacn.biliroaming.utils.callMethod
import me.iacn.biliroaming.utils.callMethodAs
import me.iacn.biliroaming.utils.callMethodOrNullAs
import me.iacn.biliroaming.utils.callStaticMethod
import me.iacn.biliroaming.utils.checkErrorToast
import me.iacn.biliroaming.utils.from
import me.iacn.biliroaming.utils.hookAfterMethod
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.iterator
import me.iacn.biliroaming.utils.orEmpty
import me.iacn.biliroaming.utils.sPrefs
import me.iacn.biliroaming.utils.toJSONObject
import me.iacn.biliroaming.watchButton
import org.json.JSONObject
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit

class SearchHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private val navClass by Weak { "com.bapis.bilibili.polymer.app.search.v1.Nav" from mClassLoader }
    private val searchByTypeRespClass by Weak { "com.bapis.bilibili.polymer.app.search.v1.SearchByTypeResponse" from mClassLoader }

    override fun startHook() {
        val hidden = sPrefs.getBoolean("hidden", false)
        val searchBangumi = sPrefs.getBoolean("search_area_bangumi", false)
        val searchMovie = sPrefs.getBoolean("search_area_movie", false)
        val removeAds = sPrefs.getBoolean("search_remove_ads", false)
        val purifySearch = sPrefs.getBoolean("purify_search", false)

        val searchMossClass =
            "com.bapis.bilibili.polymer.app.search.v1.SearchMoss".from(mClassLoader)
        if (hidden && (searchBangumi || searchMovie || removeAds)) {
            searchMossClass?.hookBeforeMethod(
                "searchAll",
                "com.bapis.bilibili.polymer.app.search.v1.SearchAllRequest",
                instance.mossResponseHandlerClass
            ) { param ->
                val handler = param.args[1]
                param.args[1] = Proxy.newProxyInstance(
                    handler.javaClass.classLoader,
                    arrayOf(instance.mossResponseHandlerClass)
                ) { _, m, args ->
                    if (m.name == "onNext") {
                        if (searchBangumi || searchMovie)
                            addAreaTags(args[0])
                        if (removeAds)
                            removeAds(args[0])
                        m(handler, *args)
                    } else if (args == null) {
                        m(handler)
                    } else {
                        m(handler, *args)
                    }
                }
            }
        }

        if (hidden && (searchBangumi || searchMovie)) {
            searchMossClass?.hookBeforeMethod(
                "searchByType",
                "com.bapis.bilibili.polymer.app.search.v1.SearchByTypeRequest",
                instance.mossResponseHandlerClass
            ) { param ->
                val searchByTypeRespClass = searchByTypeRespClass ?: return@hookBeforeMethod
                val key = param.args[0].callMethodOrNullAs<Int>("getType")
                    ?: return@hookBeforeMethod
                val areaType = AREA_TYPES[key] ?: return@hookBeforeMethod
                val request = SearchByTypeRequest.parseFrom(
                    param.args[0].callMethodAs<ByteArray>("toByteArray")
                )
                val type = areaType.type
                val area = areaType.area
                val handler = param.args[1]
                MainScope().launch(Dispatchers.IO) {
                    val result = retrieveAreaSearch(request, area, type)
                    if (result != null) {
                        val newRes = searchByTypeRespClass
                            .callStaticMethod("parseFrom", result.toByteArray())
                        handler.callMethod("onNext", newRes)
                        handler.callMethod("onCompleted")
                    } else {
                        handler.callMethod("onError", null)
                    }
                }
                param.result = null
            }
        }

        if (hidden && purifySearch) {
            searchMossClass?.hookAfterMethod(
                "defaultWords",
                "com.bapis.bilibili.app.interfaces.v1.DefaultWordsReq"
            ) { it.result = null }
        }
    }

    private fun addAreaTags(v: Any?) {
        v ?: return
        val navClass = navClass ?: return
        val navList = v.callMethodAs<List<Any>>("getNavList")
            .map { SearchNav.parseFrom(it.callMethodAs<ByteArray>("toByteArray")) }
            .toMutableList()
        val currentArea = runCatching {
            XposedInit.country.get(5L, TimeUnit.SECONDS)
        }.getOrNull()
        for (area in AREA_TYPES) {
            if (area.value.area == currentArea)
                continue
            if (!sPrefs.getString(area.value.area + "_server", null).isNullOrBlank() &&
                sPrefs.getBoolean("search_area_" + area.value.type_str, false)
            ) {
                val nav = searchNav {
                    name = area.value.text
                    total = 0
                    pages = 0
                    type = area.key
                }
                navList.add(1, nav)
            }
        }
        v.callMethod("clearNav")
        val newNavList = navList.map {
            navClass.callStaticMethod("parseFrom", it.toByteArray())
        }
        v.callMethod("addAllNav", newNavList)
    }

    private fun retrieveAreaSearch(
        request: SearchByTypeRequest,
        area: String,
        type: String
    ): SearchByTypeResponse? {
        val pn = request.pagination.next.ifEmpty { "1" }
        val ps = request.pagination.pageSize
        val keyword = request.keyword
        val query = mapOf(
            "access_key" to (instance.accessKey ?: ""),
            "fnval" to request.playerArgs.fnval,
            "fnver" to request.playerArgs.fnver,
            "qn" to request.playerArgs.qn,
            "pn" to pn,
            "ps" to ps,
            "keyword" to keyword,
        ).map { "${it.key}=${it.value}" }.joinToString("&")
        val jsonContent = getAreaSearchBangumi(query, area, type)?.toJSONObject()
            ?: return null
        checkErrorToast(jsonContent, true)
        val newData = jsonContent.optJSONObject("data") ?: return null

        fun ReasonStyleKt.Dsl.reconstructFrom(json: JSONObject) = json.run {
            text = optString("text")
            textColor = optString("text_color")
            textColorNight = optString("text_color_night")
            bgColor = optString("bg_color")
            bgColorNight = optString("bg_color_night")
            borderColor = optString("border_color")
            borderColorNight = optString("border_color_night")
            bgStyle = optInt("bg_style")
        }

        fun EpisodeKt.Dsl.reconstructFrom(json: JSONObject) = json.run {
            uri = optString("uri")
            param = optString("param")
            index = optString("index")
            for (badge in optJSONArray("badges").orEmpty())
                badges += reasonStyle { reconstructFrom(badge) }
            position = optInt("position")
        }

        fun EpisodeNewKt.Dsl.reconstructFrom(json: JSONObject) = json.run {
            title = optString("title")
            uri = optString("uri")
            param = optString("param")
            isNew = optInt("is_new")
            for (badge in optJSONArray("badges").orEmpty())
                badges += reasonStyle { reconstructFrom(badge) }
            this@reconstructFrom.type = optInt("type")
            position = optInt("position")
            cover = optString("cover")
            label = optString("label")
        }

        fun WatchButtonKt.Dsl.reconstructFrom(json: JSONObject) = json.run {
            title = optString("title")
            link = optString("link")
        }

        fun CheckMoreKt.Dsl.reconstructFrom(json: JSONObject) = json.run {
            content = optString("content")
            uri = optString("uri")
        }

        fun FollowButtonKt.Dsl.reconstructFrom(json: JSONObject) = json.run {
            icon = optString("icon")
            optJSONObject("texts")?.let { o ->
                o.keys().asSequence().associateWith { o.opt(it)?.toString() ?: "" }
            }?.let { texts.putAll(it) }
            statusReport = optString("status_report")
        }

        fun SearchBangumiCardKt.Dsl.reconstructFrom(json: JSONObject) = json.run {
            title = optString("title")
            cover = optString("cover")
            mediaType = optInt("media_type")
            playState = optInt("play_state")
            this@reconstructFrom.area = optString("area")
            style = optString("style")
            styles = optString("styles")
            cv = optString("cv")
            rating = optDouble("rating")
            vote = optInt("vote")
            target = optString("target")
            staff = optString("staff")
            prompt = optString("prompt")
            ptime = optLong("ptime")
            seasonTypeName = optString("season_type_name")
            for (episode in optJSONArray("episodes").orEmpty())
                episodes += episode { reconstructFrom(episode) }
            isSelection = optInt("is_selection")
            isAtten = optInt("is_atten")
            label = optString("label")
            seasonId = optLong("season_id")
            outName = optString("out_name")
            outIcon = optString("out_icon")
            outUrl = optString("out_url")
            for (badge in optJSONArray("badges").orEmpty())
                badges += reasonStyle { reconstructFrom(badge) }
            isOut = optInt("is_out")
            for (episodeNew in optJSONArray("episodes_new").orEmpty())
                episodesNew += episodeNew { reconstructFrom(episodeNew) }
            optJSONObject("watch_button")?.let {
                watchButton = watchButton { reconstructFrom(it) }
            }
            selectionStyle = optString("selection_style")
            optJSONObject("check_more")?.let {
                checkMore = checkMore { reconstructFrom(it) }
            }
            optJSONObject("follow_button")?.let {
                followButton = followButton { reconstructFrom(it) }
            }
            optJSONObject("style_label")?.let {
                styleLabel = reasonStyle { reconstructFrom(it) }
            }
            for (badgeV2 in optJSONArray("badges_v2").orEmpty())
                badgesV2 += reasonStyle { reconstructFrom(badgeV2) }
            stylesV2 = optString("styles_v2")
        }

        fun SearchItemKt.Dsl.reconstructFrom(json: JSONObject) = json.run {
            uri = optString("uri")
            param = optString("param")
            goto = optString("goto")
            linkType = optString("link_type")
            position = optInt("position")
            trackId = optString("track_id")
            bangumi = searchBangumiCard { reconstructFrom(json) }
        }

        val pages = newData.optInt("pages")
        var page = pn.toIntOrNull() ?: 1
        val response = searchByTypeResponse {
            this.pages = pages
            this.keyword = keyword
            for (item in newData.optJSONArray("items").orEmpty()) {
                if (item.optInt("Offset", -1) != -1)
                    item.remove("follow_button")
                items += searchItem { reconstructFrom(item) }
            }
            if (page < pages)
                pagination = paginationReply { next = (++page).toString() }
        }
        return response
    }

    private fun removeAds(v: Any?) = buildSet {
        v?.callMethodAs<List<Any>?>("getItemList")
            ?.onEachIndexed { idx, e ->
                if (e.callMethodAs("hasCm")) add(idx)
            }
    }.reversed().forEach {
        v?.callMethod("removeItem", it)
    }
}
