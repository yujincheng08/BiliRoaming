package me.iacn.biliroaming.hook

import android.net.Uri
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.Protos.*
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.XposedInit.Companion.toastMessage
import me.iacn.biliroaming.network.BiliRoamingApi.getPlayUrl
import me.iacn.biliroaming.network.StreamUtils.getContent
import me.iacn.biliroaming.utils.*
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection

/**
 * Created by iAcn on 2019/3/29
 * Email i@iacn.me
 */
class BangumiPlayUrlHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("main_func", false)) return
        Log.d("startHook: BangumiPlayUrl")
        instance.signQueryName()?.let {
            "com.bilibili.nativelibrary.LibBili".hookBeforeMethod(mClassLoader, it, MutableMap::class.java) { param ->
                @Suppress("UNCHECKED_CAST")
                val params = param.args[0] as MutableMap<String, String>
                if (XposedInit.sPrefs.getBoolean("allow_download", false) &&
                        params.containsKey("ep_id")) {
                    params.remove("dl")
                    params["fnval"] = "0"
                }
            }
        }
        instance.urlConnectionClass?.hookAfterMethod("getInputStream") { param ->
            // Found from "b.ecy" in version 5.39.1
            val connection = param.thisObject as HttpURLConnection
            val urlString = connection.url.toString()
            if (XposedInit.sPrefs.getBoolean("add_4k", false) && (urlString.startsWith("https://app.bilibili.com/x/v2/param")
                            || urlString.startsWith("https://appintl.biliapi.net/intl/gateway/app/param"))) {
                var content = getContent(param.result as InputStream, connection.contentEncoding)
                if (content != null) {
                    val jsonContent = JSONObject(content)
                    val newContent = JSONObject("{\"code\":0,\"data\":{\"player_pgc_vip_qn\":\"74,112,116,120,125\",\"player_ugc_vip_qn\":\"74,112,116,120,125\"},\"message\":\"0\"}")
                    if (jsonContent.getInt("code") == -304) {
                        newContent.put("ver", jsonContent.getLong("ver"))
                        content = newContent.toString()
                    } else if (jsonContent.getInt("code") == 0) {
                        jsonContent.getJSONObject("data").run {
                            put("player_pgc_vip_qn", newContent.getJSONObject("data").getString("player_pgc_vip_qn"))
                            put("player_ugc_vip_qn", newContent.getJSONObject("data").getString("player_ugc_vip_qn"))
                        }
                        content = jsonContent.toString()
                    }
                }
                param.result = ByteArrayInputStream(content?.toByteArray())
            }
            if (!urlString.startsWith("https://api.bilibili.com/pgc/player/api/playurl") &&
                    !urlString.startsWith("https://apiintl.biliapi.net/intl/gateway/ogv/player/api/playurl"))
                return@hookAfterMethod
            val queryString = urlString.substring(urlString.indexOf("?") + 1)
            if ((!queryString.contains("ep_id=") && !queryString.contains("module=bangumi"))
                    || queryString.contains("ep_id=0") /*workaround*/) return@hookAfterMethod
            var content = getContent(param.result as InputStream, connection.contentEncoding)
            if (content == null || !isLimitWatchingArea(content)) {
                param.result = ByteArrayInputStream(content?.toByteArray())
                return@hookAfterMethod
            }
            content = getPlayUrl(queryString, BangumiSeasonHook.lastSeasonInfo)
            content?.let {
                Log.d("Has replaced play url with proxy server $it")
                toastMessage("已从代理服务器获取播放地址")
                param.result = ByteArrayInputStream(it.toByteArray())
            } ?: run {
                Log.e("Failed to get play url")
                toastMessage("获播放地址失败")
            }
        }

        "com.bapis.bilibili.pgc.gateway.player.v1.PlayURLMoss".findClass(mClassLoader)?.run {
            hookBeforeMethod("playView",
                    "com.bapis.bilibili.pgc.gateway.player.v1.PlayViewReq") { param ->
                val request = param.args[0]
                if (XposedInit.sPrefs.getBoolean("allow_download", false)) {
                    request.callMethod("setDownload", 0)
                    request.callMethod("setFnval", 0)
                }
            }
            hookAfterMethod("playView",
                    "com.bapis.bilibili.pgc.gateway.player.v1.PlayViewReq") { param ->
                val request = param.args[0]
                val response = param.result
                if (!response.callMethodAs<Boolean>("hasVideoInfo")) {
                    val content = getPlayUrl(reconstructQuery(request), BangumiSeasonHook.lastSeasonInfo)
                    content?.let {
                        Log.d("Has replaced play url with proxy server $it")
                        toastMessage("已从代理服务器获取播放地址")
                        param.result = reconstructResponse(response, it)
                    } ?: run {
                        Log.e("Failed to get play url")
                        toastMessage("获取播放地址失败")
                    }
                }
            }
        }
    }


    private fun isLimitWatchingArea(jsonText: String): Boolean {
        return try {
            val json = JSONObject(jsonText)
            val code = json.optInt("code")
            code == -10403
        } catch (e: JSONException) {
            Log.e(e)
            false
        }
    }

    private fun reconstructQuery(request: Any): String? {
        val serializedRequest = request.callMethodAs<ByteArray>("toByteArray")
        val req = PlayViewReq.parseFrom(serializedRequest)
        // CANNOT use reflection for compatibility with Xpatch
        return Uri.Builder().run {
            appendQueryParameter("ep_id", req.epId.toString())
            appendQueryParameter("cid", req.cid.toString())
            appendQueryParameter("qn", req.qn.toString())
            appendQueryParameter("fnver", req.fnver.toString())
            appendQueryParameter("fnval", req.fnval.toString())
            appendQueryParameter("download", req.download.toString())
            appendQueryParameter("force_host", req.forceHost.toString())
            appendQueryParameter("fourk", req.fourk.toString())
            appendQueryParameter("spmid", req.spmid.toString())
            appendQueryParameter("from_spmid", req.fromSpmid.toString())
            appendQueryParameter("teenagers_mode", req.teenagersMode.toString())
            appendQueryParameter("prefer_codec_type", req.preferCodecType.toString())
            appendQueryParameter("is_preview", req.isPreview.toString())
            BangumiSeasonHook.lastSeasonInfo["access_key"]?.let {
                appendQueryParameter("access_key", it)
            }
            build()
        }.query
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
            val videoCodeCid = jsonContent.getInt("video_codecid")
            val builder = PlayViewReply.newBuilder()
            builder.playConf = PlayAbilityConf.newBuilder().run {
                dislikeDisable = true
                likeDisable = true
                elecDisable = true
                build()
            }
            val videoInfoBuilder = VideoInfo.newBuilder().run {
                timelength = jsonContent.getLong("timelength")
                videoCodecid = jsonContent.getInt("video_codecid")
                quality = jsonContent.getInt("quality")
                format = jsonContent.getString("format")
                this
            }
            val qualityMap = jsonContent.getJSONArray("accept_quality").let {
                (0 until it.length()).map { idx -> it.getInt(idx) }
            }
            val type = jsonContent.getString("type")
            val formatMap = HashMap<Int, JSONObject>()
            jsonContent.getJSONArray("support_formats").run {
                for (i in 0 until length()) {
                    val format = getJSONObject(i)
                    formatMap[format.getInt("quality")] = format
                }
            }
            if (type == "DASH") {
                val audioIds = jsonContent.getJSONObject("dash").getJSONArray("audio").let {
                    val ids = IntArray(it.length())
                    for (i in 0 until it.length()) {
                        val audio = it.getJSONObject(i)
                        videoInfoBuilder.addDashAudio(DashItem.newBuilder().run {
                            baseUrl = audio.getString("base_url")
                            id = audio.getInt("id")
                            ids[i] = id
                            md5 = audio.getString("md5")
                            size = audio.getLong("size")
                            codecid = audio.getInt("codecid")
                            bandwidth = audio.getInt("bandwidth")
                            audio.getJSONArray("backup_url").let { bk ->
                                for (j in 0 until bk.length()) {
                                    addBackupUrl(bk.getString(j))
                                }
                            }
                            build()
                        })
                    }
                    ids
                }
                jsonContent.getJSONObject("dash").getJSONArray("video").let {
                    for (i in 0 until it.length()) {
                        val video = it.getJSONObject(i)
                        if (video.getInt("codecid") != videoCodeCid) continue
                        videoInfoBuilder.addStreamList(Stream.newBuilder().run {
                            dashVideo = DashVideo.newBuilder().run {
                                baseUrl = video.getString("base_url")
                                video.getJSONArray("backup_url").let { bk ->
                                    for (j in 0 until bk.length()) {
                                        addBackupUrl(bk.getString(j))
                                    }
                                }
                                bandwidth = video.getInt("bandwidth")
                                codecid = video.getInt("codecid")
                                md5 = video.getString("md5")
                                size = video.getLong("size")
                                audioId = audioIds[0]
                                noRexcode = jsonContent.getInt("no_rexcode") != 0
                                build()
                            }
                            streamInfo = StreamInfo.newBuilder().run {
                                quality = video.getInt("id")
                                intact = true
                                attribute = 0
                                formatMap[quality]?.let { fmt ->
                                    reconstructFormat(this, fmt)
                                }
                                build()
                            }
                            build()
                        })
                    }

                }
            } else if (type == "FLV") {
                qualityMap.forEach { quality ->
                    videoInfoBuilder.addStreamList(Stream.newBuilder().run {
                        streamInfo = StreamInfo.newBuilder().run {
                            this.quality = quality
                            intact = true
                            attribute = 0
                            formatMap[quality]?.let { fmt ->
                                reconstructFormat(this, fmt)
                            }
                            build()
                        }
                        if (quality == jsonContent.getInt("quality")) {
                            segmentVideo = SegmentVideo.newBuilder().run {
                                jsonContent.getJSONArray("durl").let { durl ->
                                    for (i in 0 until durl.length()) {
                                        val segment = durl.getJSONObject(i)
                                        addSegment(ResponseUrl.newBuilder().run {
                                            length = segment.getLong("length")
                                            segment.getJSONArray("backup_url").let { bk ->
                                                for (j in 0 until bk.length()) {
                                                    addBackupUrl(bk.getString(i))
                                                }
                                            }
                                            md5 = segment.getString("md5")
                                            order = segment.getInt("order")
                                            size = segment.getLong("size")
                                            url = segment.getString("url")
                                            build()
                                        })
                                    }
                                }
                                build()
                            }
                        }
                        build()
                    })
                }
            }

            builder.videoInfo = videoInfoBuilder.build()
            builder.business = BusinessInfo.newBuilder().build()
            val serializedResponse = builder.build().toByteArray()
            return response.javaClass.callStaticMethod("parseFrom", serializedResponse)!!
        } catch (e: Throwable) {
            Log.e(e)
        }
        return response
    }

    private fun reconstructFormat(builder: StreamInfo.Builder, fmt: JSONObject) {
        builder.run {
            description = fmt.getString("description")
            format = fmt.getString("format")
            needVip = if (fmt.has("need_vip")) fmt.getBoolean("need_vip") else false
            needLogin = if (fmt.has("need_login")) fmt.getBoolean("need_login") else false
            newDescription = fmt.getString("new_description")
            superscript = fmt.getString("superscript")
            displayDesc = fmt.getString("display_desc")
        }
    }
}