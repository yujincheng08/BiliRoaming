package me.iacn.biliroaming.hook

import android.util.ArrayMap
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.Constant.TYPE_EPISODE_ID
import me.iacn.biliroaming.Constant.TYPE_SEASON_ID
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.XposedInit.Companion.toastMessage
import me.iacn.biliroaming.network.BiliRoamingApi.getSeason
import me.iacn.biliroaming.utils.Log
import org.json.JSONObject

/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */

data class Result(val code: Int?, val result: Any?)

class BangumiSeasonHook(classLoader: ClassLoader?) : BaseHook(classLoader!!) {
    private val lastSeasonInfo: MutableMap<String, String?>
    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("main_func", false)) return
        Log.d("startHook: BangumiSeason")
        val paramsMapClass = findClass("com.bilibili.bangumi.data.page.detail." +
                "BangumiDetailApiService\$UniformSeasonParamsMap", mClassLoader)
        XposedBridge.hookAllConstructors(paramsMapClass, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                val paramMap: Map<String, String> = param.thisObject as Map<String, String>
                when (param.args[1] as Int) {
                    TYPE_SEASON_ID -> lastSeasonInfo["season_id"] = paramMap["season_id"]
                    TYPE_EPISODE_ID -> lastSeasonInfo["ep_id"] = paramMap["ep_id"]
                    else -> return
                }
                lastSeasonInfo["access_key"] = paramMap["access_key"]
            }
        })

        val responseClass = findClass(instance!!.retrofitResponse(), mClassLoader)

        XposedBridge.hookAllConstructors(responseClass, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                val body = param.args[1]
                val bangumiApiResponse = instance!!.bangumiApiResponse()
                // Filter non-bangumi responses
                // If it isn't bangumi, the type variable will not exist in this map
                if (!bangumiApiResponse!!.isInstance(body)) return
                val result = getObjectField(body, "result")
                // Filter normal bangumi and other responses
                if (isBangumiWithWatchPermission(getIntField(body, "code"), result)) {
                    result?.let {
                        allowDownload(it)
                    }
                    lastSeasonInfo.clear()
                    return
                }
                toastMessage("发现版权番剧，尝试解锁……")
                Log.d(lastSeasonInfo)
                val hidden = result == null
                val content = getSeason(lastSeasonInfo, hidden)
                val (code, newResult) = getNewResult(content)

                if (newResult != null && code != null && isBangumiWithWatchPermission(code, newResult)) {
                    Log.d("Got new season information from proxy server: $content")
                    toastMessage("已从代理服务器获取番剧信息")
                } else {
                    Log.d("Failed to get new season information from proxy server")
                    toastMessage("解锁失败，请重试")
                    lastSeasonInfo.clear()
                    return
                }
                val newRights = getObjectField(newResult, "rights")
                if (XposedInit.sPrefs.getBoolean("allow_download", false))
                    setBooleanField(newRights, "allowDownload", true)
                if (!hidden) {
                    // Replace only episodes and rights
                    // Remain user information, such as follow status, watch progress, etc.
                    if (!getBooleanField(newRights, "areaLimit")) {
                        val newEpisodes = getObjectField(newResult, "episodes")
                        var newModules: Any? = null
                        findFieldIfExists(newResult.javaClass, "modules")?.let {
                            newModules = getObjectField(newResult, "modules")
                        }
                        setObjectField(result, "rights", newRights)
                        setObjectField(result, "episodes", newEpisodes)
                        setObjectField(result, "seasonLimit", null)
                        findFieldIfExists(result.javaClass, "modules")?.let {
                            newModules?.let { it2 ->
                                setObjectField(result, it.name, it2)
                            }
                        }
                    }
                } else {
                    setIntField(body, "code", 0)
                    setObjectField(body, "result", newResult)
                }
                lastSeasonInfo.clear()
            }
        })
    }

    private fun isBangumiWithWatchPermission(code: Int, result: Any?): Boolean {
        if (result != null) {
            val bangumiSeasonClass = instance!!.bangumiUniformSeason()
            if (bangumiSeasonClass!!.isInstance(result)) {
                val rights = getObjectField(result, "rights")
                val areaLimit = getBooleanField(rights, "areaLimit")
                return !areaLimit
            }
        }
        return code != -404
    }

    private fun getNewResult(content: String?): Result {
        val fastJsonClass = instance!!.fastJson()
        val beanClass = instance!!.bangumiUniformSeason()
        if (content == null) {
            return Result(null, null)
        }
        val contentJson = JSONObject(content)
        val resultJson = contentJson.optJSONObject("result")
        val code = contentJson.optInt("code")
        val newResult = callStaticMethod(fastJsonClass,
                instance!!.fastJsonParse(), resultJson!!.toString(), beanClass)
        return Result(code, newResult)
    }

    private fun allowDownload(result: Any?) {
        val bangumiSeasonClass = instance!!.bangumiUniformSeason()
        if (bangumiSeasonClass!!.isInstance(result) &&
                XposedInit.sPrefs.getBoolean("allow_download", false)) {
            val rights = getObjectField(result, "rights")
            setBooleanField(rights, "allowDownload", true)
            Log.d("Download allowed")
            toastMessage("已允许下载")
        }
    }


    init {
        lastSeasonInfo = ArrayMap()
    }
}