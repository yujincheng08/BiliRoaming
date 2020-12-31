package me.iacn.biliroaming.network

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import me.iacn.biliroaming.BuildConfig
import me.iacn.biliroaming.Constant.HOST_REGEX
import me.iacn.biliroaming.R
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */
object BiliRoamingApi {
    private const val BILI_SEASON_URL = "api.bilibili.com/pgc/view/web/season"
    private const val BILIPLUS_VIEW_URL = "www.biliplus.com/api/view"
    private const val BILI_REVIEW_URL = "api.bilibili.com/pgc/review/user"
    private const val BILI_USER_STATUS_URL = "api.bilibili.com/pgc/view/web/season/user/status"
    private const val BILI_MEDIA_URL = "bangumi.bilibili.com/view/web_api/media"
    private const val BILI_MODULE_TEMPLATE = "{\"data\": {},\"id\": 0,\"module_style\": {\"hidden\": 0,\"line\": 1},\"more\": \"查看更多\",\"style\": \"positive\",\"title\": \"选集\"}"

    private const val PATH_PLAYURL = "/pgc/player/api/playurl"
    private const val THAILAND_PATH_PLAYURL = "/intl/gateway/v2/ogv/playurl"

    @JvmStatic
    fun getSeason(info: Map<String, String?>, hidden: Boolean): String? {
        val builder = Uri.Builder()
        builder.scheme("https").encodedAuthority(BILI_SEASON_URL)
        info.filter { !it.value.isNullOrEmpty() }.forEach { builder.appendQueryParameter(it.key, it.value) }
        val seasonJson = getContent(builder.toString())?.toJSONObject() ?: return null
        seasonJson.optJSONObject("result")?.also {
            fixEpisodes(it)
            reconstructModules(it)
            fixRight(it)
            if (hidden) getExtraInfo(it, info["access_key"])
        }
        return seasonJson.toString()
    }

    @JvmStatic
    private fun fixEpisodes(result: JSONObject) {
        val episodes = result.optJSONArray("episodes")
        for (episode in episodes.orEmpty()) {
            episode.optJSONObject("rights")?.put("area_limit", 0)
            if (episode.optInt("badge_type", -1) == 0)
                episode.remove("badge_info")
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
        val rights = result.optJSONObject("rights")
        rights?.put("area_limit", 0)
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
    fun getPlayUrl(queryString: String?, info: Map<String, String?>): String? {
        return getFromCustomUrl(queryString, info)?.let {
            JSONObject(it).optJSONObject("result")?.toString() ?: it
        }?.replace(HOST_REGEX, "://${
            sPrefs.getString("upos_host", null)
                    ?: XposedInit.moduleRes.getString(R.string.uptx_host)
        }/")
    }

    @JvmStatic
    fun getFromCustomUrl(queryString: String?, info: Map<String, String?>): String? {
        Log.d("Title: ${info["title"]}")
        val twUrl = sPrefs.getString("tw_server", null)
        val hkUrl = sPrefs.getString("hk_server", null)
        val cnUrl = sPrefs.getString("cn_server", null)
        val sgUrl = sPrefs.getString("sg_server", null)
        val hostList = ArrayList<String>()
        info["title"]?.run {
            if (contains(Regex("僅.*台")) && twUrl != null) hostList += twUrl
            if (contains(Regex("僅.*港")) && hkUrl != null) hostList += hkUrl
            if (contains(Regex("[仅|僅].*[东南亚|其他]")) && sgUrl != null) hostList += sgUrl
        }
        if (hostList.isEmpty())
            arrayOf(cnUrl, twUrl, hkUrl, sgUrl).forEach { if (it != null) hostList += it }

        if (hostList.isEmpty()) {
            return null
        }

        for (host in hostList) {
            val uri = Uri.Builder()
                    .scheme("https")
                    .encodedAuthority(host + PATH_PLAYURL)
                    .encodedQuery(queryString)
                    .toString()
            getContent(uri)?.let {
                Log.d("use backup $host for playurl instead")
                if (it.contains("\"code\":0")) return it
            }
        }

        Log.d("try to ues Thailand api")
        val uri = Uri.Builder()
                .scheme("https")
                .encodedAuthority(sgUrl + THAILAND_PATH_PLAYURL)
                .encodedQuery(signQuery(queryString, Thailand = true))
                .toString()
        getContent(uri)?.let {
            Log.d("Thailand !!! use backup $sgUrl for playurl instead")
            if (it.contains("\"code\":0")) return fixThailandPlayurl(it)
        }

        return null
    }

    @JvmStatic
    fun fixThailandPlayurl(result: String): String? {
        val input = JSONObject(result)
        val data = input.optJSONObject("data")
        val video_info = data.optJSONObject("video_info")
        val stream_list = video_info.optJSONArray("stream_list")
        val dash_audio = video_info.optJSONArray("dash_audio")

        val output = JSONObject().apply {
            put("format", "flv720")
            put("type", "DASH")
            put("result", "suee")
            put("video_codecid", 7)
            put("no_rexcode", 0)

            put("code", input.optInt("code"))
            put("message", input.optInt("message"))
            put("timelength", video_info.optInt("timelength"))
            put("quality", video_info.optInt("quality"))
            put("accept_format", "hdflv2,flv,flv720,flv480,mp4")
        }

        val accept_quality = JSONArray()
        val accept_description = JSONArray()

        val dash = JSONObject().apply {
            put("duration", 0)
            put("minBufferTime", 0.0)
            put("min_buffer_time", 0.0)
        }
        val fixed_audio = JSONArray().apply {
            for (audio in dash_audio.orEmpty()) {
                put(audio)
            }
        }
        dash.put("audio", fixed_audio)

        val support_formats = JSONArray()
        val dash_video = JSONArray()
        for (stream in stream_list.orEmpty()) {
            stream.optJSONObject("stream_info")?.let {
                support_formats.put(it)
            }
            stream.optJSONObject("stream_info")?.let {
                accept_quality.put(it.optInt("quality"))
            }
            stream.optJSONObject("stream_info")?.let {
                accept_description.put(it.optString("new_description"))
            }
            stream.optJSONObject("dash_video")?.let {
                it.put("id", stream.optJSONObject("stream_info")?.optInt("quality"))
                dash_video.put(it)
            }
        }
        dash.put("video", dash_video)

        output.put("accept_quality", accept_quality)
        output.put("accept_description", accept_description)
        output.put("support_formats", support_formats)
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
    fun getContent(urlString: String): String? {
        val timeout = 3000
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
                    webView.loadUrl(urlString)
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
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
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

