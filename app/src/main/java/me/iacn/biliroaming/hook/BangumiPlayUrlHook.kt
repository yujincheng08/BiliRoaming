package me.iacn.biliroaming.hook

import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import me.iacn.biliroaming.Constant.TAG
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.network.BiliRoamingApi.getPlayUrl
import me.iacn.biliroaming.network.BiliRoamingApi.playurlBp
import me.iacn.biliroaming.network.StreamUtils.getContent
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
        if (!XposedInit.sPrefs.getBoolean("main_func", false)) return
        Log.d(TAG, "startHook: BangumiPlayUrl")
        XposedHelpers.findAndHookMethod("com.bilibili.nativelibrary.LibBili", mClassLoader, "a",
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
        XposedHelpers.findAndHookMethod("com.bilibili.lib.okhttp.huc.OkHttpURLConnection", mClassLoader,
                "getInputStream", object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                // Found from "b.ecy" in version 5.39.1
                val connection = param.thisObject as HttpURLConnection
                val urlString = connection.url.toString()
                if (urlString.startsWith("https://api.bilibili.com/pgc/player/api/playurl")) {
                    val queryString = urlString.substring(urlString.indexOf("?") + 1)
                    if (queryString.contains("ep_id=") || queryString.contains("module=bangumi")) {
                        val inputStream = param.result as InputStream
                        val encoding = connection.contentEncoding
                        var content = getContent(inputStream, encoding)
                        content?.let {
                            if (isLimitWatchingArea(it)) {
                                content = if (XposedInit.sPrefs.getBoolean("use_biliplus", false))
                                    playurlBp(queryString) else getPlayUrl(queryString)
                                Log.d(TAG, "Has replaced play url with proxy server")
                            }
                        }
                        param.result = ByteArrayInputStream(content!!.toByteArray())
                    }
                }
            }
        })
    }

    private fun isLimitWatchingArea(jsonText: String): Boolean {
        return try {
            val json = JSONObject(jsonText)
            val code = json.optInt("code")
            Log.d(TAG, "PlayUrlInformation: code = $code")
            code == -10403
        } catch (e: JSONException) {
            e.printStackTrace()
            false
        }
    }

}