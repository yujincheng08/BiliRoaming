package me.iacn.biliroaming.hook

import android.util.ArrayMap
import de.robv.android.xposed.XC_MethodHook
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.Constant.TYPE_EPISODE_ID
import me.iacn.biliroaming.Constant.TYPE_SEASON_ID
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.XposedInit.Companion.toastMessage
import me.iacn.biliroaming.network.BiliRoamingApi
import me.iacn.biliroaming.network.BiliRoamingApi.getSeason
import me.iacn.biliroaming.utils.*
import org.json.JSONObject
import java.net.URL

/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */

data class Result(val code: Int?, val result: Any?)

class BangumiSeasonHook(classLoader: ClassLoader?) : BaseHook(classLoader!!) {
    companion object {
        val lastSeasonInfo: MutableMap<String, String?> = ArrayMap()
    }

    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("main_func", false)) return
        Log.d("startHook: BangumiSeason")
        "com.bilibili.bangumi.data.page.detail.BangumiDetailApiService\$UniformSeasonParamsMap".findClass(mClassLoader)?.hookAfterAllConstructors { param ->
            @Suppress("UNCHECKED_CAST")
            val paramMap: Map<String, String> = param.thisObject as Map<String, String>
            lastSeasonInfo.clear()
            when (param.args[1] as Int) {
                TYPE_SEASON_ID -> lastSeasonInfo["season_id"] = paramMap["season_id"]
                TYPE_EPISODE_ID -> lastSeasonInfo["ep_id"] = paramMap["ep_id"]
                else -> return@hookAfterAllConstructors
            }
            lastSeasonInfo["access_key"] = paramMap["access_key"]
        }

        instance.retrofitResponseClass?.hookBeforeAllConstructors { param ->
            val url = getUrl(param.args[0])
            val body = param.args[1] ?: return@hookBeforeAllConstructors
            // Filter non-bangumi responses
            // If it isn't bangumi, the type variable will not exist in this map
            if (instance.bangumiApiResponseClass?.isInstance(body) == true ||
                    // for new blue 6.3.7
                    instance.rxGeneralResponseClass?.isInstance(body) == true) {
                fixBangumi(body)
            } else if (url != null && url.startsWith("https://app.bilibili.com/x/v2/view") &&
                    body.getIntField("code") == -404) {
                fixView(body, url)
            }
        }

        val urlHook = object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                val redirectUrl = param.thisObject.getObjectFieldAs<String?>("redirectUrl")
                if (redirectUrl.isNullOrEmpty()) return
                param.result = param.thisObject.callMethod("getUrl", redirectUrl)
            }
        }
        "com.bilibili.bplus.followingcard.api.entity.cardBean.VideoCard".findClass(mClassLoader)?.hookMethod("getJumpUrl", urlHook)
        "com.bilibili.bplus.followingcard.api.entity.cardBean.VideoCard".findClass(mClassLoader)?.hookMethod("getCommentJumpUrl", urlHook)
    }

    private fun fixBangumi(body: Any) {
        val result = body.getObjectField("result")
        // Filter normal bangumi and other responses
        if (isBangumiWithWatchPermission(body.getIntField("code"), result)) {
            result?.let {
                if (instance.bangumiUniformSeasonClass?.isInstance(it) == true) {
                    allowDownload(it)
                    lastSeasonInfo.clear()
                }
            }
            return
        }
        toastMessage("发现版权番剧，尝试解锁……")
        Log.d("Info: $lastSeasonInfo")
        val hidden = result == null
        val content = getSeason(lastSeasonInfo, hidden)
        val (code, newResult) = instance.bangumiUniformSeasonClass?.let { getNewResult(content, "result", it) }
                ?: return

        if (newResult != null && code != null && isBangumiWithWatchPermission(code, newResult)) {
            Log.d("Got new season information from proxy server: $content")
            lastSeasonInfo["title"] = newResult.getObjectFieldAs<String>("title").toString()
            toastMessage("已从代理服务器获取番剧信息")
        } else {
            Log.d("Failed to get new season information from proxy server")
            toastMessage("解锁失败，请重试")
            lastSeasonInfo.clear()
            return
        }
        val newRights = newResult.getObjectField("rights") ?: return
        if (XposedInit.sPrefs.getBoolean("allow_download", false))
            newRights.setBooleanField("allowDownload", true)
        if (!hidden) {
            // Replace only episodes and rights
            // Remain user information, such as follow status, watch progress, etc.
            if (!newRights.getBooleanField("areaLimit")) {
                val newEpisodes = newResult.getObjectField("episodes")
                var newModules: Any? = null
                newResult.javaClass.findFieldOrNull("modules")?.let {
                    newModules = newResult.getObjectField("modules")
                }
                result.setObjectField("rights", newRights)
                        .setObjectField("episodes", newEpisodes)
                        .setObjectField("seasonLimit", null)
                result!!.javaClass.findFieldOrNull("modules")?.let {
                    newModules?.let { it2 ->
                        result.setObjectField(it.name, it2)
                    }
                }
            }
        } else {
            body.setIntField("code", 0)
                    .setObjectField("result", newResult)
        }
    }

    private fun fixView(body: Any, urlString: String) {
        var queryString = urlString.substring(urlString.indexOf("?") + 1)
        queryString = queryString.replace("aid=", "id=")
        val content = BiliRoamingApi.getView(queryString)
        Log.d("Got view information from proxy server: $content")
        val detailClass = "tv.danmaku.bili.ui.video.api.BiliVideoDetail".findClass(mClassLoader)
                ?: return
        val (_, newResult) = getNewResult(content, "v2_app_api", detailClass)
        body.setIntField("code", 0).setObjectField("data", newResult)
        val bangumiInfo = newResult?.getObjectField("mBangumiInfo")
        bangumiInfo?.let {
            val episodeId = bangumiInfo.getObjectField("mEpId") as String?
            episodeId?.let {
                lastSeasonInfo["ep_id"] = it
                val url = URL(urlString)
                url.query.split("&").filter { it2 ->
                    it2.startsWith("access_key=")
                }.forEach { it2 ->
                    lastSeasonInfo["access_key"] = it2.substring(it2.indexOf("=") + 1)
                }
            }
        }
    }

    private fun isBangumiWithWatchPermission(code: Int, result: Any?): Boolean {
        if (result != null) {
            if (instance.bangumiUniformSeasonClass?.isInstance(result) == true) {
                val rights = result.getObjectField("rights")
                val areaLimit = rights?.getBooleanField("areaLimit")
                return areaLimit == null || !areaLimit
            }
        }
        return code != -404
    }

    private fun getNewResult(content: String?, fieldName: String, beanClass: Class<*>): Result {
        if (content == null) {
            return Result(null, null)
        }
        val contentJson = JSONObject(content)
        val resultJson = contentJson.optJSONObject(fieldName)
        val code = contentJson.optInt("code")
        val newResult = instance.fastJsonClass?.callStaticMethod(
                instance.fastJsonParse(), resultJson!!.toString(), beanClass)
        return Result(code, newResult)
    }

    private fun allowDownload(result: Any?) {
        if (XposedInit.sPrefs.getBoolean("allow_download", false)) {
            try {
                val rights = result?.getObjectField("rights")
                rights?.setBooleanField("allowDownload", true)
                val modules = result?.getObjectField("modules") as MutableList<*>? ?: return
                for (it in modules) {
                    val data = it?.getObjectField("data")
                    val moduleEpisodes = data?.callMethod("getJSONArray", "episodes") ?: continue
                    val size = moduleEpisodes.callMethodAs<Int>("size")
                    for (i in 0 until size) {
                        val episode = moduleEpisodes.callMethod("getJSONObject", i)
                        val episodeRights = episode?.callMethod("getJSONObject", "rights")
                        episodeRights?.callMethod("put", "allow_download", 1)
                    }
                }
            } catch (e: Throwable) {
                Log.e(e)
            }
            Log.d("Download allowed")
            toastMessage("已允许下载")
        }
    }

    private fun getUrl(response: Any): String? {
        val requestField = instance.requestField()
        val urlMethod = instance.urlMethod()
        if (requestField == null || urlMethod == null) {
            return null
        }
        val request = response.getObjectField(requestField)
        return try {
            request?.callMethod(urlMethod)?.toString()
        } catch (e: Throwable) {
            null
        }
    }

}