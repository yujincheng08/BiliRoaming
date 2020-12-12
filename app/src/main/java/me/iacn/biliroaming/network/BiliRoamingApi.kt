package me.iacn.biliroaming.network

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.BuildConfig
import me.iacn.biliroaming.Constant.HOST_REGEX
import me.iacn.biliroaming.R
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.hook.BangumiSeasonHook.Companion.lastSeasonInfo
import me.iacn.biliroaming.utils.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLStreamHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext


/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */
object BiliRoamingApi {
    private const val BILI_SEASON_URL = "api.bilibili.com/pgc/view/web/season"
    private const val BILI_HIDDEN_SEASON_URL = "bangumi.bilibili.com/view/web_api/season"
    private const val BILIPLUS_VIEW_URL = "www.biliplus.com/api/view"
    private const val BILI_REVIEW_URL = "api.bilibili.com/pgc/review/user"
    private const val BILI_USER_STATUS_URL = "api.bilibili.com/pgc/view/web/season/user/status"
    private const val BILI_MEDIA_URL = "bangumi.bilibili.com/view/web_api/media"
    private const val BILI_SECTION_URL = "api.bilibili.com/pgc/web/season/section"
    private const val BILI_MODULE_TEMPLATE = "{\"data\": {},\"id\": 0,\"module_style\": {\"hidden\": 0,\"line\": 1},\"more\": \"查看更多\",\"style\": \"positive\",\"title\": \"选集\"}"
    private const val BILI_RIGHT_TEMPLATE = "{\"allow_demand\":0,\"allow_dm\":1,\"allow_download\":0,\"area_limit\":0}"
    private const val BILI_VIP_BADGE_TEMPLATE = "{\"bg_color\":\"#FB7299\",\"bg_color_night\":\"#BB5B76\",\"text\":\"%s\"}"

    private const val PATH_PLAYURL = "/pgc/player/api/playurl"
    private const val THAILAND_PATH_PLAYURL = "/intl/gateway/v2/ogv/playurl"
    private const val THAILAND_PATH_SUBTITLES = "/intl/gateway/v2/app/subtitle"
    private const val THAILAND_PATH_SEARCH = "/intl/gateway/v2/app/search/type"
    var httpshandler: URLStreamHandler? = null

    @JvmStatic
    fun getSeason(info: Map<String, String?>, hidden: Boolean): String? {
        val builder = Uri.Builder()
        builder.scheme("https").encodedAuthority(if (hidden) BILI_HIDDEN_SEASON_URL else BILI_SEASON_URL)
        info.filter { !it.value.isNullOrEmpty() }.forEach { builder.appendQueryParameter(it.key, it.value) }
        val seasonJson = getContent(builder.toString())?.toJSONObject() ?: return null
        seasonJson.optJSONObject("result")?.also {
            if (hidden) fixHiddenSeason(it)
            fixEpisodes(it)
            if (hidden) fixSection(it)
            fixPrevueSection(it)
            reconstructModules(it)
            fixRight(it)
            if (hidden) getExtraInfo(it, instance.accessKey)
        }
        return seasonJson.toString()
    }

    @JvmStatic
    private fun fixSection(result: JSONObject) {
        val seasonId = result.optString("season_id")
        val uri = Uri.Builder()
                .scheme("https")
                .encodedAuthority(BILI_SECTION_URL)
                .appendQueryParameter("season_id", seasonId)
                .toString()
        val sectionJson = getContent(uri).toJSONObject().optJSONObject("result") ?: return
        val sections = sectionJson.optJSONArray("section") ?: return

        val episodeMap = result.optJSONArray("episodes")?.iterator()?.asSequence()?.map { it.optInt("ep_id") to it }?.toMap()
                ?: return
        for ((i, section) in sections.iterator().withIndex()) {
            section.put("episode_id", i)
            val newEpisodes = JSONArray()
            for (episode in section.optJSONArray("episodes").orEmpty()) {
                newEpisodes.put(episodeMap[episode.optInt("id")])
            }
            section.put("episodes", newEpisodes)
        }
        result.put("section", sections)
        result.optJSONObject("newest_ep")?.run {
            put("title", optString("index"))
            result.put("new_ep", this)
        }

        val newEpisodes = JSONArray()
        for (episode in sectionJson.optJSONObject("main_section")?.optJSONArray("episodes").orEmpty()) {
            newEpisodes.put(episodeMap[episode.optInt("id")])
        }
        result.put("episodes", newEpisodes)
    }

    @JvmStatic
    fun getThailandSubtitles(epId: String?): String? {
        epId ?: return null
        val thUrl = sPrefs.getString("th_server", null) ?: return null
        val uri = Uri.Builder()
                .scheme("https")
                .encodedAuthority(thUrl + THAILAND_PATH_SUBTITLES)
                .appendQueryParameter("ep_id", epId)
                .toString()
        return getContent(uri)
    }

    @JvmStatic
    fun getThailandSearchBangumi(queryString: String): String? {
        val thUrl = sPrefs.getString("th_server", null) ?: return null
        val uri = Uri.Builder()
                .scheme("https")
                .encodedAuthority(thUrl + THAILAND_PATH_SEARCH)
                .encodedQuery(signQuery(queryString, mapOf(
                        "type" to "7",
                        "appkey" to "7d089525d3611b1c",
                        "build" to "1001310",
                        "mobi_app" to "bstar_a",
                        "platform" to "android",
                        "s_locale" to "zh_SG",
                        "c_locale" to "zh_SG",
                        "lang" to "hans",
                )))
                .toString()
        return getContent(uri)?.replace("bstar://bangumi/season/", "https://bangumi.bilibili.com/anime/")
    }

    @JvmStatic
    private fun fixHiddenSeason(result: JSONObject) {
        for (episode in result.optJSONArray("episodes").orEmpty()) {
            episode.put("long_title", episode.optString("index_title"))
            episode.put("id", episode.optString("ep_id"))
            episode.put("title", episode.optString("index"))
            episode.put("rights", BILI_RIGHT_TEMPLATE.toJSONObject())
            episode.put("status", episode.optInt("episode_status"))
        }
    }

    @JvmStatic
    private fun fixPrevueSection(result: JSONObject) {
        result.put("prevueSection", result.optJSONObject("section"))
    }

    @JvmStatic
    private fun fixEpisodes(result: JSONObject) {
        val episodes = result.optJSONArray("episodes")
        for (episode in episodes.orEmpty()) {
            episode.optJSONObject("rights")?.run {
                put("area_limit", 0)
                put("allow_dm", 1)
            }
            if (episode.optInt("badge_type", -1) == 0)
                episode.remove("badge_info")
            if (episode.optString("badge") != "受限")
                episode.put("badge_info", JSONObject(BILI_VIP_BADGE_TEMPLATE.format(episode.optString("badge"))))
        }
        for (section in result.optJSONArray("section").orEmpty()) {
            fixEpisodes(section)
        }
    }

    @JvmStatic
    private fun reconstructModules(result: JSONObject) {
        var id = 0
        val module = BILI_MODULE_TEMPLATE.toJSONObject()
        val episodes = result.optJSONArray("episodes")
        module.optJSONObject("data")?.put("episodes", episodes)
        module.put("id", ++id)
        val modules = arrayListOf(module)

        if (result.has("section")) {
            val sections = result.optJSONArray("section")
            for (section in sections.orEmpty()) {
                val sectionModule = BILI_MODULE_TEMPLATE.toJSONObject()
                        .put("data", section)
                        .put("style", "section")
                        .put("title", section.optString("title"))
                        .put("id", ++id)
                modules.add(sectionModule)
            }
        }
        if (result.has("seasons")) {
            val seasons = result.optJSONArray("seasons")
            val seasonModule = BILI_MODULE_TEMPLATE.toJSONObject()
            seasonModule.put("data", JSONObject().put("seasons", seasons))
                    .put("style", "season")
                    .put("title", "")
                    .put("id", ++id)
                    .put("module_style", JSONObject("{\"line\": 1}"))
            modules.add(seasonModule)

        }
        // work around
        result.put("modules", JSONArray(modules))
    }

    @JvmStatic
    private fun fixRight(result: JSONObject) {
        result.optJSONObject("rights")?.run {
            put("area_limit", 0)
            put("allow_dm", 1)
        }
    }

    @JvmStatic
    @Throws(JSONException::class)
    private fun getExtraInfo(result: JSONObject, accessKey: String?) {
        val mediaId = result.optString("media_id")
        getMediaInfo(result, mediaId, accessKey)
        val seasonId = result.optString("season_id")
        getUserStatus(result, seasonId, mediaId, accessKey)
    }

    @JvmStatic
    private fun getMediaInfo(result: JSONObject, mediaId: String, accessKey: String?) {
        val uri = Uri.Builder()
                .scheme("https")
                .encodedAuthority(BILI_MEDIA_URL)
                .appendQueryParameter("media_id", mediaId)
                .appendQueryParameter("access_key", accessKey)
                .toString()
        val mediaJson = getContent(uri)?.toJSONObject()
        val mediaResult = mediaJson?.optJSONObject("result")
        val actors = mediaResult?.optString("actors")
        result.put("actor", "{\"info\": \"$actors\", \"title\": \"角色声优\"}".toJSONObject())
        val staff = mediaResult?.optString("staff")
        result.put("staff", "{\"info\": \"$staff\", \"title\": \"制作信息\"}".toJSONObject())
        for (field in listOf("alias", "areas", "origin_name", "style", "type_name")) {
            result.put(field, mediaResult?.opt(field))
        }
    }

    @JvmStatic
    private fun getReviewInfo(userStatus: JSONObject, mediaId: String, accessKey: String?) {
        val uri = Uri.Builder()
                .scheme("https")
                .encodedAuthority(BILI_REVIEW_URL)
                .appendQueryParameter("media_id", mediaId)
                .appendQueryParameter("access_key", accessKey)
                .toString()
        val reviewJson = getContent(uri)?.toJSONObject()
        val reviewResult = reviewJson?.optJSONObject("result")
        val review = reviewResult?.optJSONObject("review")
        review?.put("article_url", "https://member.bilibili.com/article-text/mobile?media_id=$mediaId")
        userStatus.put("review", review)
    }

    @JvmStatic
    private fun getUserStatus(result: JSONObject, seasonId: String, mediaId: String, accessKey: String?) {
        try {
            val uri = Uri.Builder()
                    .scheme("https")
                    .encodedAuthority(BILI_USER_STATUS_URL)
                    .appendQueryParameter("season_id", seasonId)
                    .appendQueryParameter("access_key", accessKey)
                    .toString()
            val statusJson = getContent(uri)?.toJSONObject()
            val statusResult = statusJson?.optJSONObject("result")
            val userStatus = JSONObject()
            for (field in listOf("follow", "follow_status", "pay", "progress", "sponsor", "paster")) {
                userStatus.put(field, statusResult?.opt(field))
            }
            if (statusResult?.optJSONObject("vip_info")?.optInt("status") == 1) {
                userStatus.put("vip", 1)
            }
            getReviewInfo(userStatus, mediaId, accessKey)
            result.put("user_status", userStatus)
        } catch (e: Throwable) {
            Log.e(e)
        }
    }

    @JvmStatic
    fun getPlayUrl(queryString: String?, priorityArea: Array<String>? = null): String? {
        return getFromCustomUrl(queryString, priorityArea)?.let {
            JSONObject(it).optJSONObject("result")?.toString() ?: it
        }?.replace(HOST_REGEX, "://${
            sPrefs.getString("upos_host", null)
                    ?: XposedInit.moduleRes.getString(R.string.wcs_host)
        }/")
    }

    @JvmStatic
    private fun getFromCustomUrl(queryString: String?, priorityArea: Array<String>?): String? {
        queryString ?: return null
        val hkUrl = sPrefs.getString("hk_server", null)
        val hkip = sPrefs.getString("hk_server_ip", null)
        val hkport = sPrefs.getString("hk_server_port", null)
        val twUrl = sPrefs.getString("tw_server", null)
        val twip = sPrefs.getString("tw_server_ip", null)
        val twport = sPrefs.getString("tw_server_port", null)
        val cnUrl = sPrefs.getString("cn_server", null)
        val cnip = sPrefs.getString("cn_server_ip", null)
        val cnport = sPrefs.getString("cn_server_port", null)
        val thUrl = sPrefs.getString("th_server", null)
        val thip = sPrefs.getString("th_server_ip", null)
        val thport = sPrefs.getString("th_server_port", null)

        val hostList = LinkedHashMap<String, Array<String?>>(4, 1f, true)

        if (hostList.isEmpty())
        // reversely
            linkedMapOf("th" to arrayOf(thUrl, thip, thport), "cn" to arrayOf(cnUrl, cnip, cnport), "tw" to arrayOf(twUrl, twip, twport), "hk" to arrayOf(hkUrl, hkip, hkport)).filterValues {
                it[0] != null
            }.mapValuesTo(hostList) {
                it.value
            }

        val epIdStartIdx = queryString.indexOf("ep_id=")
        val epIdEndIdx = queryString.indexOf("&", epIdStartIdx)
        val epId = queryString.substring(epIdStartIdx + 6, epIdEndIdx)

        if (!lastSeasonInfo.containsKey("ep_ids") || lastSeasonInfo["ep_ids"]?.contains(epId) != true)
            lastSeasonInfo.clear()

        lastSeasonInfo["title"]?.run {
            if (contains(Regex("[仅|僅].*[东南亚|其他]")) && thUrl != null) hostList["th"]
            if (contains(Regex("僅.*台")) && twUrl != null) hostList["tw"]
            if (contains(Regex("僅.*港")) && hkUrl != null) hostList["hk"]
        }

        priorityArea?.forEach { area ->
            if (hostList.containsKey(area)) hostList[area]
        }

        if (hostList.isEmpty()) return null

        val seasonId = lastSeasonInfo["season_id"] ?: if (epId.isEmpty()) null else "ep$epId"

        if (seasonId != null && sCaches.contains(seasonId)) {
            val cachedArea = sCaches.getString(seasonId, null)
            if (hostList.containsKey(cachedArea)) {
                Log.d("use cached area $cachedArea for $seasonId")
                hostList[cachedArea]
            }
        }

        for ((area, host) in hostList.toList().asReversed()) {
            val extraMap = if (area == "th") mapOf(
                    "area" to area,
                    "appkey" to "7d089525d3611b1c",
                    "build" to "1001310",
                    "mobi_app" to "bstar_a",
                    "platform" to "android",
            )
            else mapOf(
                    "area" to area
            )
            val path = if (area == "th") THAILAND_PATH_PLAYURL else PATH_PLAYURL
            val uri = Uri.Builder()
                    .scheme("https")
                    .encodedAuthority(host[0] + path)
                    .encodedQuery(signQuery(queryString, extraMap))
                    .toString()

            if (host[1] != null && host[2] != null) {
                Log.toast("trying "+host[0]+" with "+host[1]+":"+host[2])
            } else if (host[1] != null) {
                Log.toast("trying "+host[0]+" with "+host[1])
            } else if (host[2] != null) {
                Log.toast("trying "+host[0]+":"+host[2])
            } else {
                Log.toast("trying "+host[0])
            }

            getContent(uri, host[1], host[2])?.let {
                if (host[1] != null && host[2] != null) {
                    Log.d("use server $area "+host[0]+" with "+host[1]+":"+host[2]+" for playurl")
                } else if (host[1] != null) {
                    Log.d("use server $area "+host[0]+" with "+host[1]+" for playurl")
                } else if (host[2] != null) {
                    Log.d("use server $area "+host[0]+":"+host[2]+" for playurl")
                } else {
                    Log.d("use server $area "+host[0]+" for playurl")
                }

                if (it.contains("\"code\":0")) {
                    if (seasonId != null && !sCaches.contains(seasonId) || sCaches.getString(seasonId, null) != area) {
                        sCaches.edit().run {
                            putString(seasonId, area)
                            lastSeasonInfo["ep_ids"]?.split(";")?.forEach { epId -> putString("ep$epId", area) }
                            apply()
                        }
                    }
                    return if (area == "th") fixThailandPlayurl(it) else it
                }
            }
        }
        return null
    }

    @JvmStatic
    fun fixThailandPlayurl(result: String): String {
        val input = JSONObject(result)
        val videoInfo = result.toJSONObject().optJSONObject("data")?.optJSONObject("video_info")
        val streamList = videoInfo?.optJSONArray("stream_list")
        val dashAudio = videoInfo?.optJSONArray("dash_audio")

        val output = JSONObject().apply {
            put("format", "flv720")
            put("type", "DASH")
            put("result", "suee")
            put("video_codecid", 7)
            put("no_rexcode", 0)

            put("code", input.optInt("code"))
            put("message", input.optInt("message"))
            put("timelength", videoInfo?.optInt("timelength"))
            put("quality", videoInfo?.optInt("quality"))
            put("accept_format", "hdflv2,flv,flv720,flv480,mp4")
        }

        val acceptQuality = JSONArray()
        val acceptDescription = JSONArray()

        val dash = JSONObject().apply {
            put("duration", 0)
            put("minBufferTime", 0.0)
            put("min_buffer_time", 0.0)
        }
        val fixedAudio = JSONArray().apply {
            for (audio in dashAudio.orEmpty()) {
                put(audio)
            }
        }
        dash.put("audio", fixedAudio)

        val supportFormats = JSONArray()
        val dashVideo = JSONArray()
        for (stream in streamList.orEmpty()) {
            stream.optJSONObject("stream_info")?.let {
                supportFormats.put(it)
            }
            stream.optJSONObject("stream_info")?.let {
                acceptQuality.put(it.optInt("quality"))
            }
            stream.optJSONObject("stream_info")?.let {
                acceptDescription.put(it.optString("new_description"))
            }
            stream.optJSONObject("dash_video")?.let {
                it.put("id", stream.optJSONObject("stream_info")?.optInt("quality"))
                dashVideo.put(it)
            }
        }
        dash.put("video", dashVideo)

        output.put("accept_quality", acceptQuality)
        output.put("accept_description", acceptDescription)
        output.put("support_formats", supportFormats)
        output.put("dash", dash)

        return output.toString()
    }

    @JvmStatic
    fun getView(queryString: String?): String? {
        val builder = Uri.Builder()
        builder.scheme("https").encodedAuthority(BILIPLUS_VIEW_URL)
        builder.encodedQuery(queryString)
        builder.appendQueryParameter("module", "bangumi")
        builder.appendQueryParameter("otype", "json")
        builder.appendQueryParameter("platform", "android")
        return getContent(builder.toString())
    }


    @SuppressLint("SetJavaScriptEnabled")
    fun getContent(urlString: String, ip: String? = null, port: String? = null): String? {
        val timeout = 10000
        return try {
            // Work around for android 7
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.N &&
                    urlString.startsWith("https") &&
                    !urlString.contains("bilibili.com")) {
                Log.d("Found Android 7, try to bypass ssl issue")
                val handler = Handler(AndroidAppHelper.currentApplication().mainLooper)
                val listener = object : Any() {
                    val latch = CountDownLatch(1)
                    var result = ""

                    @JavascriptInterface
                    fun callback(r: String) {
                        result = r
                        latch.countDown()
                    }
                }
                handler.post {
                    val webView = WebView(currentContext, null)
                    webView.addJavascriptInterface(listener, "listener")
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            view?.settings?.javaScriptEnabled = true
                            view?.loadUrl("javascript:listener.callback(document.documentElement.innerText)")
                        }
                    }
                    webView.loadUrl(urlString, mapOf(
                            "x-from-biliroaming" to BuildConfig.VERSION_NAME,
                            "Build" to BuildConfig.VERSION_CODE.toString()
                    ))
                }
                try {
                    if (!listener.latch.await((timeout * 2).toLong(), TimeUnit.MILLISECONDS)) {
                        Log.toast("连接超时，请重试")
                        throw IOException("Timeout connection to server")
                    }
                } catch (e: InterruptedException) {
                    throw IOException("Connection to server was interrupted")
                }
                return listener.result
            } else {
                lateinit var connection: HttpsURLConnection
                if (ip != null || port != null){
                    val sslcontext = SSLContext.getDefault()
                    val sslsocketfactory = SSLSocketFactoryWrapper(sslcontext.socketFactory, ip, port)
                    val url = URL(null, urlString, httpshandler)
                    connection = url.openConnection() as HttpsURLConnection
                    connection.sslSocketFactory = sslsocketfactory
                } else {
                    val url = URL(urlString)
                    connection = url.openConnection() as HttpsURLConnection
                }

                connection.requestMethod = "GET"
                connection.setRequestProperty("Build", BuildConfig.VERSION_CODE.toString())
                connection.connectTimeout = timeout
                connection.readTimeout = timeout
                connection.setRequestProperty("x-from-biliroaming", BuildConfig.VERSION_NAME)

                connection.connect()
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    getStreamContent(inputStream)
                } else null
            }

        } catch (e: Throwable) {
            Log.e("getContent error: $e with url $urlString")
            Log.e(e)
            null
        }
    }

}

