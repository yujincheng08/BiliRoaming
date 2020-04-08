package me.iacn.biliroaming.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.XposedInit.Companion.toastMessage
import me.iacn.biliroaming.network.BiliRoamingApi.getPlayUrl
import me.iacn.biliroaming.network.StreamUtils.getContent
import me.iacn.biliroaming.utils.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection

/**
 * Created by iAcn on 2019/3/29
 * Email i@iacn.me
 */
class BangumiPlayUrlHook(classLoader: ClassLoader?) : BaseHook(classLoader!!) {
    override fun startHook() {
        Log.d("startHook: BangumiPlayUrl")
        findAndHookMethod("com.bilibili.nativelibrary.LibBili", mClassLoader, "a",
                MutableMap::class.java, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                @Suppress("UNCHECKED_CAST")
                val params = param.args[0] as MutableMap<String, String>
                if (XposedInit.sPrefs.getBoolean("allow_download", false) &&
                        params.containsKey("ep_id")) {
                    params.remove("dl")
                }
                if (XposedInit.sPrefs.getBoolean("simulate", false)) {
                    params["appkey"] = "1d8b6e7d45233436"
                    params["platform"] = "android"
                    params["mobi_app"] = "android"
                }
            }
        })
        if (!XposedInit.sPrefs.getBoolean("main_func", false)) return
        findAndHookMethod("com.bilibili.lib.okhttp.huc.OkHttpURLConnection", mClassLoader,
                "getInputStream", object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                // Found from "b.ecy" in version 5.39.1
                val connection = param.thisObject as HttpURLConnection
                val urlString = connection.url.toString()
                if (!urlString.startsWith("https://api.bilibili.com/pgc/player/api/playurl")) return
                val queryString = urlString.substring(urlString.indexOf("?") + 1)
                if (!queryString.contains("ep_id=") && !queryString.contains("module=bangumi")) return
                val inputStream = param.result as InputStream
                val encoding = connection.contentEncoding
                var content = getContent(inputStream, encoding)
                if (content == null || !isLimitWatchingArea(content)) {
                    param.result = ByteArrayInputStream(content?.toByteArray())
                    return
                }
                content = getPlayUrl(queryString)
                content?.let {
                    Log.d("Has replaced play url with proxy server $it")
                    toastMessage("已从代理服务器获取播放地址")
                    param.result = ByteArrayInputStream(it.toByteArray())
                } ?: run {
                    Log.d("Failed to get play url")
                    toastMessage("获取播放地址失败")
                }
            }
        })
    }

    private fun isLimitWatchingArea(jsonText: String): Boolean {
        return try {
            val json = JSONObject(jsonText)
            val code = json.optInt("code")
            code == -10403
        } catch (e: JSONException) {
            e.printStackTrace()
            false
        }
    }

}