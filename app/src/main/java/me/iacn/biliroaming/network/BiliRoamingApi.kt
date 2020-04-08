package me.iacn.biliroaming.network

import android.net.Uri
import android.text.TextUtils
import me.iacn.biliroaming.BuildConfig
import me.iacn.biliroaming.network.StreamUtils.getContent
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */
object BiliRoamingApi {
    private const val BILIROAMING_SEASON_URL = "api.iacn.me/biliroaming/season"
    private const val BILIROAMING_PLAYURL_URL = "api.iacn.me/biliroaming/playurl"
    private const val BILIPLUS_SEASON_URL = "www.biliplus.com/api/bangumi"
    private const val BILIPLUS_PLAYURL_URL = "www.biliplus.com/BPplayurl.php"
    private const val BILIEPISODE_TEMPLATE = "{\"aid\":0,\"badge\":\"\",\"badge_type\":0,\"cid\":0,\"cover\":\"\",\"dimension\":{\"height\":1080,\"rotate\":0,\"width\":1920},\"from\":\"bangumi\",\"id\":0,\"long_title\":\"\",\"release_date\":\"\",\"rights\":{\"allow_dm\":1},\"share_copy\":\"\",\"share_url\":\"\",\"short_link\":\"\",\"stat\":{\"coin\":0,\"danmakus\":0,\"play\":0,\"reply\":0},\"status\":0,\"subtitle\":\"\",\"title\":\"\",\"vid\":\"\"}"
    private const val BILIEPRIGHT_TEMPLATE = "{\"allow_bp\":0,\"allow_bp_rank\":0,\"allow_download\":1,\"allow_review\":1,\"area_limit\":0,\"ban_area_show\":1,\"can_watch\":1,\"copyright\":\"bilibili\",\"is_cover_show\":0,\"is_preview\":0,\"watch_platform\":0}"
    private const val BILIMODULE_TEMPLATE = "{\"data\": {},\"id\": 0,\"module_style\": {\"hidden\": 0,\"line\": 1},\"more\": \"查看更多\",\"style\": \"positive\",\"title\": \"选集\"}"

    @JvmStatic
    @Throws(IOException::class)
    fun getSeason(id: String?, accessKey: String?, useCache: Boolean): String? {
        val builder = Uri.Builder()
        builder.scheme("https").encodedAuthority(BILIROAMING_SEASON_URL).appendPath(id)
        if (!TextUtils.isEmpty(accessKey)) {
            builder.appendQueryParameter("access_key", accessKey)
        }
        builder.appendQueryParameter("use_cache", if (useCache) "1" else "0")
        return getContent(builder.toString())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun getPlayUrl(queryString: String): String? {
        return getContent("https://$BILIROAMING_PLAYURL_URL?$queryString")
    }

    @JvmStatic
    @Throws(IOException::class)
    fun playurlBp(queryString: String?): String? {
        val builder = Uri.Builder()
        builder.scheme("https").encodedAuthority(BILIPLUS_PLAYURL_URL)
        builder.encodedQuery(queryString)
        builder.appendQueryParameter("module", "bangumi")
        builder.appendQueryParameter("otype", "json")
        builder.appendQueryParameter("platform", "android")
        return getContent(builder.toString())
    }

    @Throws(IOException::class)
    private fun getContent(urlString: String): String? {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Build", BuildConfig.VERSION_CODE.toString())
        connection.connectTimeout = 4000
        connection.connect()
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val inputStream = connection.inputStream
            val encoding = connection.contentEncoding
            return getContent(inputStream, encoding)
        }
        return null
    }

    @JvmStatic
    @Throws(IOException::class)
    fun seasonBp(id: String?, accessKey: String?, seasonType: Int?): String {
        /*
        This won't work in android 7.0
        The ciper suite that BiliPlus used is not supported in android 7.0.

        A known bug:
        https://stackoverflow.com/questions/39133437/sslhandshakeexception-handshake-failed-on-android-n-7-0
        https://code.google.com/p/android/issues/detail?id=224438
        */
        val builder = Uri.Builder()
        builder.scheme("https").encodedAuthority(BILIPLUS_SEASON_URL)
        builder.appendQueryParameter("season", id)
        builder.appendQueryParameter("access_key", accessKey)
        builder.appendQueryParameter("season_type", "$seasonType")
        val ret = getContent(builder.toString())
        return try {
            val seasonBpt = JSONObject(ret)
            val seasonBp = seasonBpt.getJSONObject("result").getJSONArray("episodes")
            val seasonRet = JSONArray()
            for (i in 0 until seasonBp.length ()) {
                val ep = seasonBp.getJSONObject(i)
                val nep = JSONObject(BILIEPISODE_TEMPLATE)
                nep.put("aid", ep.getString("aid"))
                nep.put("cid", ep.getString("cid"))
                nep.put("cover", ep.getString("cover"))
                nep.put("id", ep.getString("ep_id"))
                nep.put("long_title", ep.getString("index_title"))
                nep.put("status", ep.getString("episode_status"))
                if (ep.has("badge")) {
                    nep.put("badge", ep.getString("badge"))
                }
                nep.put("title", ep.getString("index"))
                seasonRet.put(nep)
            }
            val module = JSONObject(BILIMODULE_TEMPLATE);
            module.getJSONObject("data").put("episodes", seasonRet);
            val epRet = JSONObject()
            val epRetResult = JSONObject()
            epRet.put("code", 0)
            epRet.put("message", "success")
            epRetResult.put("episodes", seasonRet)
            // work around
            epRetResult.put("modules", JSONArray(arrayOf(module)));
            val rpRetResultRights = JSONObject(BILIEPRIGHT_TEMPLATE)
            epRetResult.put("rights", rpRetResultRights)
            epRet.put("result", epRetResult)
            epRet.toString()
        } catch (e: JSONException) {
            e.printStackTrace()
            "{code:1}"
        }
    }
}