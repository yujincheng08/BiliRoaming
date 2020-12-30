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
import java.net.URLStreamHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters


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
    private const val BILI_MODULE_TEMPLATE = "{\"data\": {},\"id\": 0,\"module_style\": {\"hidden\": 0,\"line\": 1},\"more\": \"查看更多\",\"style\": \"positive\",\"title\": \"选集\"}"

    private const val PATH_PLAYURL = "/pgc/player/api/playurl"
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
            reconstructModules(it)
            fixRight(it)
            if (hidden) getExtraInfo(it, info["access_key"])
        }
        return seasonJson.toString()
    }

    @JvmStatic
    private fun fixHiddenSeason(result: JSONObject) {
        for (episode in result.optJSONArray("episodes").orEmpty()) {
            episode.put("long_title", episode.optString("index_title"))
            episode.put("id", episode.optString("ep_id"))
            episode.put("title", episode.optString("index"))
        }
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
                    ?: XposedInit.moduleRes.getString(R.string.wcs_host)
        }/")
    }

    @JvmStatic
    fun getFromCustomUrl(queryString: String?, info: Map<String, String?>): String? {
        Log.d("Title: ${info["title"]}")

        val hkUrl = sPrefs.getString("hk_server", null)
        val hkip = sPrefs.getString("hk_server_ip", null)
        val twUrl = sPrefs.getString("tw_server", null)
        val twip = sPrefs.getString("tw_server_ip", null)
        val cnUrl = sPrefs.getString("cn_server", null)
        val cnip = sPrefs.getString("cn_server_ip", null)
        val sgUrl = sPrefs.getString("sg_server", null)
        val sgip = sPrefs.getString("sg_server_ip", null)
        val hostList = LinkedHashMap<String, Array<String?>>()
        info["title"]?.run {
            if (contains(Regex("僅.*港")) && hkUrl != null) hostList += "hk" to arrayOf(hkUrl, hkip)
            if (contains(Regex("僅.*台")) && twUrl != null) hostList += "tw" to arrayOf(twUrl, twip)
            if (contains(Regex("[仅|僅].*[东南亚|其他]")) && sgUrl != null) hostList += "sg" to arrayOf(sgUrl, sgip)

        }

        if (hostList.isEmpty()) {
            linkedMapOf("hk" to arrayOf(hkUrl, hkip), "tw" to arrayOf(twUrl, twip), "sg" to arrayOf(sgUrl, sgip), "cn" to arrayOf(cnUrl, cnip)).filterValues {
                it[0] != null
            }.mapValuesTo(hostList) {
                it.value
            }
        }


        if (hostList.isEmpty()) return null

        for (host in hostList) {
            val uri = Uri.Builder()
                    .scheme("https")
                    .encodedAuthority(host.value[0] + PATH_PLAYURL)
                    .encodedQuery(signQuery(queryString, mapOf("area" to host.key)))
                    .toString()
            if (host.value[1] != null) {
                Log.toast("trying "+host.value[0]+" with "+host.value[1])
            } else {
                Log.toast("trying "+host.value[0])
            }

            getContent(uri,host.value[1])?.let {
                if (host.value[1] != null) {
                    Log.d("use backup "+host.value[0]+" with "+host.value[1]+" for playurl instead")
                } else {
                    Log.d("use backup "+host.value[0]+" for playurl instead")
                }
                if (it.contains("\"code\":0")) return it
            }
        }
        return null
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
    fun getContent(urlString: String, ip: String? = null): String? {
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
                lateinit var connection: HttpsURLConnection
                if (ip != null){
                    val sslparameters = SSLParameters()
                    val sslcontext = SSLContext.getDefault()
                    val sslsocketfactory = SSLSocketFactoryWrapper(sslcontext.getSocketFactory(), sslparameters, ip)
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

