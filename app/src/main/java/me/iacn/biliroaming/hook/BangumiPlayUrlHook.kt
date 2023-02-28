package me.iacn.biliroaming.hook

import android.net.Uri
import me.iacn.biliroaming.*
import me.iacn.biliroaming.API.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.hook.BangumiSeasonHook.Companion.lastSeasonInfo
import me.iacn.biliroaming.network.BiliRoamingApi.CustomServerException
import me.iacn.biliroaming.network.BiliRoamingApi.getPlayUrl
import me.iacn.biliroaming.utils.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.util.concurrent.CountDownLatch

/**
 * Created by iAcn on 2019/3/29
 * Email i@iacn.me
 */
class BangumiPlayUrlHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    companion object {
        // DASH, HDR, 4K, DOBLY AUDO, DOBLY VISION, 8K, AV1
        const val MAX_FNVAL = 16 or 64 or 128 or 256 or 512 or 1024 or 2048
        const val FAIL_CODE = -404
        var countDownLatch: CountDownLatch? = null
    }

    override fun startHook() {
        if (!sPrefs.getBoolean("main_func", false)) return
        Log.d("startHook: BangumiPlayUrl")
        instance.signQueryName()?.let {
            instance.libBiliClass?.hookBeforeMethod(it, Map::class.java) { param ->
                @Suppress("UNCHECKED_CAST")
                val params = param.args[0] as MutableMap<String, String>
                if (sPrefs.getBoolean("allow_download", false) &&
                    params.containsKey("ep_id") && params.containsKey("dl")
                ) {
                    if (sPrefs.getBoolean("fix_download", false)) {
                        params["dl_fix"] = "1"
                        params["qn"] = "0"
                        if (params["fnval"] == "0" || params["fnval"] == "1")
                            params["fnval"] = MAX_FNVAL.toString()
                        params["fourk"] = "1"
                    }
                    params.remove("dl")
                }
            }
        }
        instance.urlConnectionClass?.hookAfterMethod("getInputStream") { param ->
            // Found from "b.ecy" in version 5.39.1
            val connection = param.thisObject as HttpURLConnection
            val urlString = connection.url.toString()
            if (!urlString.startsWith("https://api.bilibili.com/pgc/player/api/playurl") &&
                !urlString.startsWith("https://apiintl.biliapi.net/intl/gateway/ogv/player/api/playurl")
            )
                return@hookAfterMethod
            if (urlString.contains("&test=true")) return@hookAfterMethod
            val queryString = urlString.substring(urlString.indexOf("?") + 1)
            if ((!queryString.contains("ep_id=") && !queryString.contains("module=bangumi"))
                || queryString.contains("ep_id=0") /*workaround*/) return@hookAfterMethod
            var content = getStreamContent(param.result as InputStream)
            if (content == null || !isLimitWatchingArea(content)) {
                if (urlString.contains("dl_fix=1") || urlString.contains("dl=1")) {
                    content = content?.let { fixDownload(it) }
                }
                param.result = ByteArrayInputStream(content?.toByteArray())
                return@hookAfterMethod
            }
            try {
                // Replace because in Android R, the sign query hook may not success.
                // As a workaround, the request will fallback to request from proxy server.
                // If biliplus is down, we can still get result from proxy server.
                // However, the speed may not be fast.
                content = getPlayUrl(queryString.replace("dl=1", "dl_fix=1"))
                countDownLatch?.countDown()
                content = content?.let {
                    if (urlString.contains("dl_fix=1") || urlString.contains("dl=1")) {
                        fixDownload(it)
                    } else content
                }
                content?.let {
                    Log.toast("已从代理服务器获取播放地址\n如加载缓慢或黑屏，可去漫游设置中测速并设置 UPOS")
                    param.result = ByteArrayInputStream(it.toByteArray())
                } ?: run {
                    Log.w("Failed to get play url")
                    Log.toast("获取播放地址失败")
                }
            } catch (e: CustomServerException) {
                var messages = ""
                for (error in e.errors) {
                    messages += "${error.key}: ${error.value}\n"
                }
                Log.w("请求解析服务器发生错误: ${messages.trim()}")
                Log.toast("请求解析服务器发生错误: ${messages.trim()}")
            }
        }

        instance.retrofitResponseClass?.hookBeforeAllConstructors { param ->
            val url = getRetrofitUrl(param.args[0]) ?: return@hookBeforeAllConstructors
            val body = param.args[1] ?: return@hookBeforeAllConstructors
            val dataField =
                if (instance.generalResponseClass?.isInstance(body) == true) "data" else instance.responseDataField()
            if (!url.startsWith("https://api.bilibili.com/x/tv/playurl") || !lastSeasonInfo.containsKey(
                    "area"
                ) || lastSeasonInfo["area"] == "th" || body.getIntField("code") != FAIL_CODE
            ) return@hookBeforeAllConstructors
            val parsed = Uri.parse(url)
            val cid = parsed.getQueryParameter("cid")
            val fnval = parsed.getQueryParameter("fnval")
            val objectId = parsed.getQueryParameter("object_id")
            val qn = parsed.getQueryParameter("qn")
            val params =
                "cid=$cid&ep_id=$objectId&fnval=$fnval&fnver=0&fourk=1&platform=android&qn=$qn"
            val json = try {
                lastSeasonInfo["area"]?.let { lastArea ->
                    getPlayUrl(params, arrayOf(lastArea))
                }
            } catch (e: CustomServerException) {
                var messages = ""
                for (error in e.errors) {
                    messages += "${error.key}: ${error.value}\n"
                }
                Log.w("请求解析服务器发生错误: ${messages.trim()}")
                Log.toast("请求解析服务器发生错误: ${messages.trim()}")
                return@hookBeforeAllConstructors
            } ?: run {
                Log.toast("获取播放地址失败")
                return@hookBeforeAllConstructors
            }
            Log.toast("已从代理服务器获取播放地址\n如加载缓慢或黑屏，可去漫游设置中测速并设置 UPOS")
            body.setObjectField(
                dataField, instance.fastJsonClass?.callStaticMethod(
                    instance.fastJsonParse(),
                    json,
                    instance.projectionPlayUrlClass
                )
            )
            body.setIntField("code", 0)
        }

        "com.bapis.bilibili.pgc.gateway.player.v1.PlayURLMoss".findClassOrNull(mClassLoader)?.run {
            var isDownload = false
            hookBeforeMethod(
                "playView",
                "com.bapis.bilibili.pgc.gateway.player.v1.PlayViewReq"
            ) { param ->
                val request = param.args[0]
                if (sPrefs.getBoolean("allow_download", false)
                    && request.callMethodAs<Int>("getDownload") >= 1
                ) {
                    if (!sPrefs.getBoolean("fix_download", false)
                        || request.callMethodAs<Int>("getFnval") <= 1
                    ) {
                        request.callMethod("setFnval", MAX_FNVAL)
                        request.callMethod("setFourk", true)
                    }
                    isDownload = true
                    request.callMethod("setDownload", 0)
                }
            }
            hookAfterMethod(
                "playView",
                "com.bapis.bilibili.pgc.gateway.player.v1.PlayViewReq"
            ) { param ->
                val request = param.args[0]
                val response = param.result
                if (!response.callMethodAs<Boolean>("hasVideoInfo")
                    || needForceProxy(response)
                ) {
                    try {
                        val content = getPlayUrl(reconstructQuery(request, response))
                        countDownLatch?.countDown()
                        content?.let {
                            Log.toast("已从代理服务器获取播放地址\n如加载缓慢或黑屏，可去漫游设置中测速并设置 UPOS")
                            param.result = reconstructResponse(response, it, isDownload)
                        } ?: run {
                            Log.w("Failed to get play url")
                            Log.toast("获取播放地址失败")
                        }
                    } catch (e: CustomServerException) {
                        var messages = ""
                        for (error in e.errors) {
                            messages += "${error.key}: ${error.value}\n"
                        }
                        param.result =
                            showPlayerError(response, "请求解析中服务器发生错误(点此查看更多)\n${messages.trim()}")
                        Log.w("请求解析服务器发生错误: ${messages.trim()}")
                        Log.toast("请求解析服务器发生错误: ${messages.trim()}")
                    }
                } else if (isDownload) {
                    param.result = fixDownloadProto(response)
                }
            }
        }
        "com.bapis.bilibili.pgc.gateway.player.v2.PlayURLMoss".findClassOrNull(mClassLoader)?.run {
            var isDownload = false
            hookBeforeMethod(
                "playView",
                "com.bapis.bilibili.pgc.gateway.player.v2.PlayViewReq"
            ) { param ->
                val request = param.args[0]
                // if getDownload == 1 -> flv download
                // if getDownload == 2 -> dash download
                // if qn == 0, we are querying available quality
                // else we are downloading
                // if fnval == 0 -> flv download
                // thus fix download will set qn = 0 and set fnval to max
                if (sPrefs.getBoolean("allow_download", false)
                    && request.callMethodAs<Int>("getDownload") >= 1
                ) {
                    if (!sPrefs.getBoolean("fix_download", false)
                        || request.callMethodAs<Int>("getFnval") <= 1
                    ) {
                        request.callMethod("setFnval", MAX_FNVAL)
                        request.callMethod("setFourk", true)
                    }
                    isDownload = true
                    request.callMethod("setDownload", 0)
                }
            }
            hookAfterMethod(
                "playView",
                "com.bapis.bilibili.pgc.gateway.player.v2.PlayViewReq"
            ) { param ->
                // th:
                // com.bilibili.lib.moss.api.BusinessException: 抱歉您所使用的平台不可观看！
                // com.bilibili.lib.moss.api.BusinessException: 啥都木有
                // connection err <- should skip because of cache:
                // throwable: com.bilibili.lib.moss.api.NetworkException
                if (instance.networkExceptionClass?.isInstance(param.throwable) == true)
                    return@hookAfterMethod
                val request = param.args[0]
                val response = param.result
                    ?: "com.bapis.bilibili.pgc.gateway.player.v2.PlayViewReply".findClass(
                        mClassLoader
                    ).new()
                if (needProxy(response)) {
                    try {
                        val content = getPlayUrl(reconstructQuery(request, response))
                        countDownLatch?.countDown()
                        content?.let {
                            Log.toast("已从代理服务器获取播放地址\n如加载缓慢或黑屏，可去漫游设置中测速并设置 UPOS")
                            param.result = reconstructResponse(response, it, isDownload)
                        } ?: throw CustomServerException(mapOf("未知错误" to "请检查哔哩漫游设置中解析服务器设置。"))
                    } catch (e: CustomServerException) {
                        var messages = ""
                        for (error in e.errors) {
                            messages += "${error.key}: ${error.value}\n"
                        }
                        param.result =
                            showPlayerError(response, "请求解析中服务器发生错误(点此查看更多)\n${messages.trim()}")
                        Log.w("请求解析服务器发生错误: ${messages.trim()}")
                        Log.toast("请求解析服务器发生错误: ${messages.trim()}")
                    }
                } else if (isDownload) {
                    param.result = fixDownloadProto(response)
                }
            }
        }
    }

    private fun needProxy(response: Any): Boolean {
        if (!response.callMethodAs<Boolean>("hasVideoInfo")) return true

        val viewInfo = response.callMethod("getViewInfo")

        if (viewInfo?.callMethod("getDialog")
                ?.callMethodAs<String>("getType") == "area_limit"
        ) return true

        if (viewInfo?.callMethod("getEndPage")?.callMethod("getDialog")
                ?.callMethodAs<String>("getType") == "area_limit"
        ) return true

        sPrefs.getString("cn_server_accessKey", null) ?: return false
        val business = response.callMethod("getBusiness")
        if (business?.callMethodAs<Boolean>("getIsPreview") == true) return true
        if (viewInfo?.callMethod("getDialog")
                ?.callMethodAs<String>("getType")?.let { it != "" } == true
        ) return true
        if (viewInfo?.callMethod("getEndPage")?.callMethod("getDialog")
                ?.callMethodAs<String>("getType")?.let { it != "" } == true
        ) return true
        return false
    }

    private fun showPlayerError(response: Any, message: String) = runCatchingOrNull {
        val serializedRequest = response.callMethodAs<ByteArray>("toByteArray")
        val newRes = PlayViewReply.parseFrom(serializedRequest).copy {
            viewInfo = viewInfo.copy {
                if (endpage.hasDialog()) {
                    dialog = endpage.dialog
                }
                dialog = dialog.copy {
                    msg = "获取播放地址失败"
                    title = title.copy {
                        text = message
                        if (!hasTextColor()) textColor = "#ffffff"
                    }
                    image = image.copy {
                        url =
                            "https://i0.hdslb.com/bfs/album/08d5ce2fef8da8adf91024db4a69919b8d02fd5c.png"
                    }
                    if (!hasCode()) code = 6002003
                    if (!hasStyle()) style = "horizontal_image"
                    if (!hasType()) type = "area_limit"
                }
                clearEndpage()
            }
            clearVideoInfo()
        }
        val serializedResponse = newRes.toByteArray()
        response.javaClass.callStaticMethod("parseFrom", serializedResponse)!!
    } ?: response

    private fun fixDownload(content: String): String {
        val json = JSONObject(content)
        if (json.optString("type") != "DASH" && !json.has("dash")) return content
        val quality = json.optInt("quality")
        val dash = json.optJSONObject("dash")
        val videos = dash?.optJSONArray("video")
        val audios = dash?.optJSONArray("audio")
        var preservedVideo: JSONObject? = null
        var audioId = 0
        for (video in videos.orEmpty()) {
            if (video.optInt("id") == quality
                && video.optInt("codecid") == json.optInt("video_codecid")
            ) {
                preservedVideo = video
            }
        }

        var preservedAudio: JSONObject? = null
        for (audio in audios.orEmpty()) {
            if (audio.optInt("id") > audioId) {
                audioId = audio.optInt("id")
                preservedAudio = audio
            }
        }

        dash?.put("video", JSONArray(arrayOf(preservedVideo)))
        dash?.put("audio", JSONArray(arrayOf(preservedAudio)))
        return json.toString()
    }

    private fun PlayViewReplyKt.Dsl.fixDownloadProto() {
        videoInfo = videoInfo.copy {
            var audioId = 0
            var setted = false
            val streams = streamList.map {
                if (it.streamInfo.quality != quality || setted) {
                    it.copy {
                        clearContent()
                    }
                } else {
                    audioId = it.dashVideo.audioId
                    setted = true
                    it
                }
            }
            val audio = dashAudio.first {
                it.id != audioId
            }
            streamList.clear()
            dashAudio.clear()
            streamList += streams
            dashAudio += audio
        }
    }

    private fun fixDownloadProto(response: Any) = runCatchingOrNull {
        val serializedRequest = response.callMethodAs<ByteArray>("toByteArray")
        val newRes = PlayViewReply.parseFrom(serializedRequest).copy {
            fixDownloadProto()
        }
        val serializedResponse = newRes.toByteArray()
        response.javaClass.callStaticMethod("parseFrom", serializedResponse)!!
    } ?: response

    private fun isLimitWatchingArea(jsonText: String) = try {
        val json = JSONObject(jsonText)
        val code = json.optInt("code")
        code == -10403
    } catch (e: JSONException) {
        Log.e(e)
        false
    }

    private fun needForceProxy(response: Any): Boolean {
        sPrefs.getString("cn_server_accessKey", null) ?: return false
        val serializedRequest = response.callMethodAs<ByteArray>("toByteArray")
        return PlayViewReply.parseFrom(serializedRequest).business.isPreview
    }

    private fun reconstructQuery(request: Any, response: Any): String? {
        val serializedRequest = request.callMethodAs<ByteArray>("toByteArray")
        val req = PlayViewReq.parseFrom(serializedRequest)
        val episodeInfo by lazy {
            response.callMethodOrNull("getBusiness")?.callMethodOrNull("getEpisodeInfo")
        }
        // CANNOT use reflection for compatibility with Xpatch
        return Uri.Builder().run {
            appendQueryParameter("ep_id", req.epId.let {
                if (it != 0L) it else episodeInfo?.callMethodOrNullAs<Int>("getEpId") ?: 0
            }.toString())
            appendQueryParameter("cid", req.cid.let {
                if (it != 0L) it else episodeInfo?.callMethodOrNullAs<Long>("getCid") ?: 0
            }.toString())
            appendQueryParameter("qn", req.qn.toString())
            appendQueryParameter("fnver", req.fnver.toString())
            appendQueryParameter("fnval", req.fnval.toString())
            appendQueryParameter("force_host", req.forceHost.toString())
            appendQueryParameter("fourk", req.fourk.toString())
            build()
        }.query
    }


    private fun reconstructResponse(response: Any, content: String, isDownload: Boolean): Any {
        try {
            var jsonContent = content.toJSONObject()
            if (jsonContent.has("result")) {
                // For kghost server
                val result = jsonContent.opt("result")
                if (result != null && result !is String) {
                    jsonContent = jsonContent.getJSONObject("result")
                }
            }
            val videoCodeCid = jsonContent.optInt("video_codecid")
            val serializedResponse = PlayViewReply.parseFrom(response.callMethodAs<ByteArray>("toByteArray")).copy {
                playConf = playAbilityConf {
                    dislikeDisable = true
                    likeDisable = true
                    elecDisable = true
                    freyaEnterDisable = true
                    freyaFullDisable = true
                }

                val qualityMap = jsonContent.optJSONArray("accept_quality")?.let {
                    (0 until it.length()).map { idx -> it.optInt(idx) }
                }
                val type = jsonContent.optString("type")
                val formatMap = HashMap<Int, JSONObject>()
                for (format in jsonContent.optJSONArray("support_formats").orEmpty()) {
                    formatMap[format.optInt("quality")] = format
                }

                videoInfo = videoInfo {
                    timelength = jsonContent.optLong("timelength")
                    videoCodecid = jsonContent.optInt("video_codecid")
                    quality = jsonContent.optInt("quality")
                    format = jsonContent.optString("format")

                    if (type == "DASH") {
                        val audioIds = ArrayList<Int>()
                        for (audio in jsonContent.optJSONObject("dash")?.optJSONArray("audio")
                            .orEmpty()) {
                            dashAudio += dashItem {
                                audio.run {
                                    baseUrl = optString("base_url")
                                    id = optInt("id")
                                    audioIds.add(id)
                                    md5 = optString("md5")
                                    size = optLong("size")
                                    codecid = optInt("codecid")
                                    bandwidth = optInt("bandwidth")
                                    for (bk in optJSONArray("backup_url").orEmpty()
                                        .asSequence<String>())
                                        backupUrl += bk
                                }
                            }
                        }
                        for (video in jsonContent.optJSONObject("dash")?.optJSONArray("video")
                            .orEmpty()) {
                            if (video.optInt("codecid") != videoCodeCid) continue
                            streamList += stream {
                                dashVideo = dashVideo {
                                    video.run {
                                        baseUrl = optString("base_url")
                                        backupUrl += optJSONArray("backup_url").orEmpty()
                                            .asSequence<String>().toList()
                                        bandwidth = optInt("bandwidth")
                                        codecid = optInt("codecid")
                                        md5 = optString("md5")
                                        size = optLong("size")
                                    }
                                    // Not knowing the extract matching,
                                    // just use the largest id
                                    audioId = audioIds.maxOrNull() ?: audioIds[0]
                                    noRexcode = jsonContent.optInt("no_rexcode") != 0
                                }
                                streamInfo = streamInfo {
                                    quality = video.optInt("id")
                                    intact = true
                                    attribute = 0
                                    formatMap[quality]?.let { fmt ->
                                        reconstructFormat(fmt)
                                    }
                                }
                            }
                        }
                    } else if (type == "FLV" || type == "MP4") {
                        qualityMap?.forEach { quality ->
                            streamList += stream {
                                streamInfo = streamInfo {
                                    this.quality = quality
                                    intact = true
                                    attribute = 0
                                    formatMap[quality]?.let { fmt ->
                                        reconstructFormat(fmt)
                                    }
                                }

                                if (quality == jsonContent.optInt("quality")) {
                                    segmentVideo = segmentVideo {
                                        for (seg in jsonContent.optJSONArray("durl")
                                            .orEmpty()) {
                                            segment += responseUrl {
                                                seg.run {
                                                    length = optLong("length")
                                                    backupUrl += optJSONArray("backup_url").orEmpty()
                                                        .asSequence<String>().toList()
                                                    md5 = optString("md5")
                                                    order = optInt("order")
                                                    size = optLong("size")
                                                    url = optString("url")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                business = business.copy {
                    isPreview = jsonContent.optInt("is_preview", 0) == 1
                    episodeInfo = episodeInfo.copy {
                        seasonInfo = seasonInfo.copy {
                            rights = seasonRights {
                                canWatch = 1
                            }
                        }
                    }
                }

                viewInfo = viewInfo {

                }

                if (isDownload) {
                    fixDownloadProto()
                }
            }.toByteArray()
            return response.javaClass.callStaticMethod("parseFrom", serializedResponse)!!
        } catch (e: Throwable) {
            Log.e(e)
        }
        return response
    }

    private fun StreamInfoKt.Dsl.reconstructFormat(fmt: JSONObject) = fmt.run {
        description = optString("description")
        format = optString("format")
        needVip = optBoolean("need_vip", false)
        needLogin = optBoolean("need_login", false)
        newDescription = optString("new_description")
        superscript = optString("superscript")
        displayDesc = optString("display_desc")
    }
}
