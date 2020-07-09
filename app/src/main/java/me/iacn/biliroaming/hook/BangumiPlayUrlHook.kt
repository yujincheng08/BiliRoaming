package me.iacn.biliroaming.hook

import android.net.Uri
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.*
import me.iacn.biliroaming.Protos.PlayViewReply
import me.iacn.biliroaming.Protos.PlayViewReq
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
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance

/**
 * Created by iAcn on 2019/3/29
 * Email i@iacn.me
 */
class BangumiPlayUrlHook(classLoader: ClassLoader?) : BaseHook(classLoader!!) {
    override fun startHook() {
        if (!XposedInit.sPrefs!!.getBoolean("main_func", false)) return
        Log.d("startHook: BangumiPlayUrl")
        instance?.signQueryName()?.let {
            findAndHookMethod("com.bilibili.nativelibrary.LibBili", mClassLoader, it,
                    MutableMap::class.java, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    @Suppress("UNCHECKED_CAST")
                    val params = param.args[0] as MutableMap<String, String>
                    if (XposedInit.sPrefs!!.getBoolean("allow_download", false) &&
                            params.containsKey("ep_id")) {
                        params.remove("dl")
                    }
                }
            })
        }
        findAndHookMethod("com.bilibili.lib.okhttp.huc.OkHttpURLConnection", mClassLoader,
                "getInputStream", object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                // Found from "b.ecy" in version 5.39.1
                val connection = param.thisObject as HttpURLConnection
                val urlString = connection.url.toString()
                if (!urlString.startsWith("https://api.bilibili.com/pgc/player/api/playurl")) return
                val queryString = urlString.substring(urlString.indexOf("?") + 1)
                if ((!queryString.contains("ep_id=") && !queryString.contains("module=bangumi"))
                        || queryString.contains("ep_id=0") /*workaround*/) return
                val inputStream = param.result as InputStream
                val encoding = connection.contentEncoding
                var content = getContent(inputStream, encoding)
                if (content == null || !isLimitWatchingArea(content)) {
                    param.result = ByteArrayInputStream(content?.toByteArray())
                    return
                }
                content = getPlayUrl(queryString, BangumiSeasonHook.lastSeasonInfo)
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
        try {
            findClass("com.bapis.bilibili.pgc.gateway.player.v1.PlayURLMoss", mClassLoader)
        } catch (e: ClassNotFoundError) {
            null
        }?.let {c ->
            findAndHookMethod(c, "playView",
                    "com.bapis.bilibili.pgc.gateway.player.v1.PlayViewReq", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val request = param.args[0]
                    if (XposedInit.sPrefs!!.getBoolean("allow_download", false)) {
                        callMethod(request, "setDownload", 0)
                    }
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    val request = param.args[0]
                    val response = param.result
                    if (!(callMethod(response, "hasVideoInfo") as Boolean)) {
                        val content = getPlayUrl(reconstructQuery(request), BangumiSeasonHook.lastSeasonInfo)
                        content?.let {
                            Log.d("Has replaced play url with proxy server $it")
                            toastMessage("已从代理服务器获取播放地址")
                            param.result = reconstructResponse(response, it)
                        } ?: run {
                            Log.d("Failed to get play url")
                            toastMessage("获取播放地址失败")
                        }
                    }
                }
            })
        }
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

    private fun reconstructQuery(request: Any): String? {
        val serializedRequest = callMethod(request, "toByteArray") as ByteArray
        val req = PlayViewReq.parseFrom(serializedRequest)
        val builder = Uri.Builder()
        for (field in req.allFields) {
            builder.appendQueryParameter(field.key.name, field.value.toString())
        }
        BangumiSeasonHook.lastSeasonInfo["access_key"]?.let {
            builder.appendQueryParameter("access_key", it)
        }
        return builder.build().query
    }


    private fun reconstructResponse(response: Any, content: String): Any {
        try {
            var jsonContent = JSONObject(content)
            if (jsonContent.has("result")) {
                // For kghost server
                val result = jsonContent.get("result")
                if (result !is String) {
                    jsonContent = jsonContent.getJSONObject("result")
                }
            }
            val builder = PlayViewReply.newBuilder()
            builder.playConfBuilder.let {
                it.dislikeDisable = true
                it.likeDisable = true
                it.elecDisable = true
                it.build()
            }
            builder.videoInfoBuilder.let {
                it.timelength = jsonContent.getLong("timelength")
                it.videoCodecid = jsonContent.getInt("video_codecid")
                it.quality = jsonContent.getInt("quality")
                it.format = jsonContent.getString("format")
            }
            val qualityMap = jsonContent.getJSONArray("accept_quality").let {
                (0 until it.length()).map { idx -> it.getInt(idx) }
            }
            val descMap = qualityMap.zip(jsonContent.getJSONArray("accept_description").let {
                (0 until it.length()).map { idx -> it.getString(idx) }
            }).toMap()
            val formatMap = qualityMap.zip(jsonContent.getString("accept_format").split(",")).toMap()
            val type = jsonContent.getString("type")
            if (type == "DASH") {
                val audioIds = jsonContent.getJSONObject("dash").getJSONArray("audio").let {
                    val ids = IntArray(it.length())
                    for (i in 0 until it.length()) {
                        val audio = it.getJSONObject(i)
                        builder.videoInfoBuilder.addDashAudioBuilder().let { dash ->
                            dash.baseUrl = audio.getString("base_url")
                            dash.id = audio.getInt("id")
                            ids[i] = dash.id
                            dash.md5 = audio.getString("md5")
                            dash.size = audio.getLong("size")
                            dash.codecid = audio.getInt("codecid")
                            dash.bandwidth = audio.getInt("bandwidth")
                            audio.getJSONArray("backup_url").let { bk ->
                                for (j in 0 until bk.length()) {
                                    dash.addBackupUrl(bk.getString(j))
                                }
                            }
                            dash.build()
                        }
                    }
                    ids
                }
                jsonContent.getJSONObject("dash").getJSONArray("video").let {
                    for (i in 0 until it.length()) {
                        val video = it.getJSONObject(i)
                        builder.videoInfoBuilder.addStreamListBuilder().let { stream ->
                            stream.dashVideoBuilder.let { dash ->
                                dash.baseUrl = video.getString("base_url")
                                video.getJSONArray("backup_url").let { bk ->
                                    for (j in 0 until bk.length()) {
                                        dash.addBackupUrl(bk.getString(j))
                                    }
                                }
                                dash.bandwidth = video.getInt("bandwidth")
                                dash.codecid = video.getInt("codecid")
                                dash.md5 = video.getString("md5")
                                dash.size = video.getLong("size")
                                dash.audioId = audioIds[0]
                                dash.noRexcode = jsonContent.getInt("no_rexcode") != 0
                                dash.build()
                            }
                            stream.streamInfoBuilder.let { info ->
                                info.quality = video.getInt("id")
                                info.format = if (formatMap.containsKey(info.quality))
                                    formatMap[info.quality] else jsonContent.getString("format")
                                info.description = descMap[info.quality]
                                info.intact = true
                                info.needLogin = needLoginMap[info.quality] ?: false
                                info.needVip = needVipMap[info.quality] ?: false
                                info.build()
                            }
                            stream.build()
                        }
                    }

                }
            } else if (type == "FLV") {
                builder.videoInfoBuilder.addStreamListBuilder().let { stream ->
                    jsonContent.getJSONArray("durl").let { durl ->
                        for (i in 0 until durl.length()) {
                            val segment = durl.getJSONObject(i)
                            stream.segmentVideoBuilder.addSegmentBuilder().let { url ->
                                url.length = segment.getLong("length")
                                segment.getJSONArray("backup_url").let { bk ->
                                    for (j in 0 until bk.length()) {
                                        url.addBackupUrl(bk.getString(i))
                                    }
                                }
                                // TODO: backup
                                url.md5 = segment.getString("md5")
                                url.order = segment.getInt("order")
                                url.size = segment.getLong("size")
                                url.url = segment.getString("url")
                                url.build()
                            }
                        }
                        stream.streamInfoBuilder.let { stream ->
                            stream.quality = jsonContent.getInt("quality")
                            stream.description = descMap[stream.quality]
                            stream.format = formatMap[stream.quality]
                            stream.intact = true
                            stream.needVip = needVipMap[stream.quality] ?: false
                            stream.needLogin = needLoginMap[stream.quality] ?: false
                        }
                        stream.build()
                    }
                }
                qualityMap.forEach { quality ->
                    if (quality != jsonContent.getInt("quality")) {
                        builder.videoInfoBuilder.addStreamListBuilder().let { stream ->
                            stream.streamInfoBuilder
                                    .setDescription(descMap[quality])
                                    .setFormat(formatMap[quality])
                                    .setNeedLogin(needLoginMap[quality] ?: false)
                                    .setNeedVip(needVipMap[quality] ?: false)
                                    .setQuality(quality).build()
                            stream.build()
                        }
                    }
                }
            }

            builder.businessBuilder.build()
            builder.videoInfoBuilder.build()
            val serializedResponse = builder.build().toByteArray()
            return callStaticMethod(response.javaClass, "parseFrom", serializedResponse)
        } catch (e: Throwable) {
            Log.d("ERR: " + e.message)
        }
        return response
    }

    companion object {
        private val needVipMap = hashMapOf(
                Pair(16, false),
                Pair(32, false),
                Pair(64, false),
                Pair(74, false),
                Pair(80, false),
                Pair(112, true), // 1080P+ needs VIP
                Pair(116, true),
                Pair(120, true)
        )
        private val needLoginMap = hashMapOf(
                Pair(16, false),
                Pair(32, true), // 480P needs login
                Pair(64, true),
                Pair(74, true),
                Pair(80, true),
                Pair(112, true),
                Pair(116, true),
                Pair(120, true)
        )
    }
}