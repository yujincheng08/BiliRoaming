package me.iacn.biliroaming.network

import android.net.Uri
import me.iacn.biliroaming.BuildConfig
import me.iacn.biliroaming.XposedInit
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
    private const val BILI_SEASON_URL = "api.bilibili.com/pgc/view/web/season"
    private const val BILIPLUS_PLAYURL_URL = "www.biliplus.com/BPplayurl.php"
    private const val BILIPLUS_VIEW_URL = "www.biliplus.com/api/view"
    private const val BILI_REVIEW_URL = "api.bilibili.com/pgc/review/user"
    private const val BILI_USER_STATUS_URL = "api.bilibili.com/pgc/view/web/season/user/status"
    private const val BILI_MEDIA_URL = "bangumi.bilibili.com/view/web_api/media"
    private const val BILIMODULE_TEMPLATE = "{\"data\": {},\"id\": 0,\"module_style\": {\"hidden\": 0,\"line\": 1},\"more\": \"查看更多\",\"style\": \"positive\",\"title\": \"选集\"}"

    @JvmStatic
    @Throws(IOException::class)
    fun getSeason(info: Map<String, String?>, hidden: Boolean): String? {
        val builder = Uri.Builder()
        builder.scheme("https").encodedAuthority(BILI_SEASON_URL)
        info.filter { !it.value.isNullOrEmpty() }.forEach { builder.appendQueryParameter(it.key, it.value) }
        val seasonContent = getContent(builder.toString()) ?: return null
        val seasonJson = JSONObject(seasonContent)
        val result = seasonJson.getJSONObject("result")
        reconstructModules(result)
        fixRight(result)
        if (hidden) getExtraInfo(result, info["access_key"])
        return seasonJson.toString()
    }

    @JvmStatic
    private fun reconstructModules(result: JSONObject) {
        val module = JSONObject(BILIMODULE_TEMPLATE)
        val episodes = result.getJSONArray("episodes")
        module.getJSONObject("data").put("episodes", episodes)
        // work around
        result.put("modules", JSONArray(arrayOf(module)))
    }

    @JvmStatic
    private fun fixRight(result: JSONObject) {
        val rights = result.getJSONObject("rights")
        rights.put("area_limit", 0)

        if (XposedInit.sPrefs.getBoolean("allow_download", false)) {
            rights.put("allow_download", 1)
        }
    }

    @JvmStatic
    @Throws(JSONException::class, IOException::class)
    private fun getExtraInfo(result: JSONObject, accessKey: String?) {
        val mediaId = result.getString("media_id")
        getMediaInfo(result, mediaId, accessKey)
        val seasonId = result.getString("season_id")
        getUserStatus(result, seasonId, mediaId, accessKey)
    }

    @JvmStatic
    private fun getMediaInfo(result: JSONObject, mediaId: String, accessKey: String?) {
        try {
            val uri = Uri.Builder()
                    .scheme("https")
                    .encodedAuthority(BILI_MEDIA_URL)
                    .appendQueryParameter("media_id", mediaId)
                    .appendQueryParameter("access_key", accessKey)
                    .toString()
            val mediaContent = getContent(uri)
            val mediaJson = JSONObject(mediaContent!!)
            val mediaResult = mediaJson.getJSONObject("result")
            val actors = mediaResult.getString("actors")
            result.put("actor", JSONObject("{\"info\": \"$actors\", \"title\": \"角色声优\"}"))
            val staff = mediaResult.getString("staff")
            result.put("staff", JSONObject("{\"info\": \"$staff\", \"title\": \"制作信息\"}"))

            for (field in listOf("alias", "area", "origin_name", "style", "type_name")) {
                if (mediaResult.has(field))
                    result.put(field, mediaResult.get(field))
            }
        } catch (e: Throwable) {
        }
    }

    @JvmStatic
    private fun getReviewInfo(userStatus: JSONObject, mediaId: String, accessKey: String?) {
        try {
            val uri = Uri.Builder()
                    .scheme("https")
                    .encodedAuthority(BILI_REVIEW_URL)
                    .appendQueryParameter("media_id", mediaId)
                    .appendQueryParameter("access_key", accessKey)
                    .toString()
            val reviewContent = getContent(uri)
            val reviewJson = JSONObject(reviewContent!!)
            val reviewResult = reviewJson.getJSONObject("result")
            val review = reviewResult.getJSONObject("review")
            review.put("article_url", "https://member.bilibili.com/article-text/mobile?media_id=$mediaId")
            userStatus.put("review", review)
        } catch (e: Throwable) {

        }
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
            val statusContent = getContent(uri)
            val reviewJson = JSONObject(statusContent!!)
            val statusResult = reviewJson.getJSONObject("result")
            val userStatus = JSONObject()
            for (field in listOf("follow", "follow_status", "pay", "progress", "sponsor", "paster")) {
                if (statusResult.has(field))
                    userStatus.put(field, statusResult.get(field))
            }
            if (statusResult.has("vip_info") &&
                    statusResult.getJSONObject("vip_info").getInt("status") == 1) {
                userStatus.put("vip", 1)
            }
            getReviewInfo(userStatus, mediaId, accessKey)
            result.put("user_status", userStatus)
        } catch (e: Throwable) {

        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun getPlayUrl(queryString: String?): String? {
        val builder = Uri.Builder()
        builder.scheme("https").encodedAuthority(BILIPLUS_PLAYURL_URL)
        builder.encodedQuery(queryString)
        builder.appendQueryParameter("module", "pgc")
        builder.appendQueryParameter("otype", "json")
        builder.appendQueryParameter("platform", "android")
//        var content = getContent(builder.toString())
//        if (content != null && !content.contains("\"code\":0")) {
//            // Workaround for moive
//            builder.appendQueryParameter("module", "movie")
//            builder.appendQueryParameter("update", "1")
//            content = getContent(builder.toString())
//        }
//        return content
        val content = getContent(builder.toString())
        return if(content != null && content.contains("\"code\":0")) content else null
    }

    @JvmStatic
    @Throws(IOException::class)
    fun getView(queryString: String?): String? {
        val builder = Uri.Builder()
        builder.scheme("https").encodedAuthority(BILIPLUS_VIEW_URL)
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
}

