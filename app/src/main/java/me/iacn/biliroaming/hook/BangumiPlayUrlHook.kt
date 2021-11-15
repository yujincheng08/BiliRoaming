package me.iacn.biliroaming.hook

import android.net.Uri
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.Protos.*
import me.iacn.biliroaming.hook.BangumiSeasonHook.Companion.lastSeasonInfo
import me.iacn.biliroaming.network.BiliRoamingApi.getPlayUrl
import me.iacn.biliroaming.network.BiliRoamingApi.getThailandSubtitles
import me.iacn.biliroaming.utils.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * Created by iAcn on 2019/3/29
 * Email i@iacn.me
 */
class BangumiPlayUrlHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private var countDownLatch: CountDownLatch? = null

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
                    if (sPrefs.getBoolean("fix_download", false) && params["qn"] != "0") {
                        params["dl_fix"] = "1"
                        if (params["fnval"] == "0")
                            params["fnval"] = params["qn"]!!
                    } else {
                        params["dl_fix"] = "1"
                        params["fnval"] = "0"
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
                Log.d("Has replaced play url with proxy server $it")
                Log.toast("已从代理服务器获取播放地址")
                param.result = ByteArrayInputStream(it.toByteArray())
            } ?: run {
                Log.e("Failed to get play url")
                Log.toast("获播放地址失败")
            }
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
                    if (sPrefs.getBoolean("fix_download", false)
                        && request.callMethodAs<Long>("getQn") != 0L
                        && request.callMethodAs<Int>("getFnval") != 0
                    ) {
                        isDownload = true
                    } else {
                        request.callMethod("setFnval", 0)
                    }
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
                    val content = getPlayUrl(reconstructQuery(request))
                    countDownLatch?.countDown()
                    content?.let {
                        Log.d("Has replaced play url with proxy server $it")
                        Log.toast("已从代理服务器获取播放地址")
                        param.result = reconstructResponse(response, it, isDownload)
                    } ?: run {
                        Log.e("Failed to get play url")
                        Log.toast("获取播放地址失败")
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
                if (sPrefs.getBoolean("allow_download", false)
                    && request.callMethodAs<Int>("getDownload") >= 1
                ) {
                    if (sPrefs.getBoolean("fix_download", false)
                        && request.callMethodAs<Long>("getQn") != 0L
                        && request.callMethodAs<Int>("getFnval") != 0
                    ) {
                        isDownload = true
                    } else {
                        request.callMethod("setFnval", 0)
                    }
                    request.callMethod("setDownload", 0)
                }
            }
            hookAfterMethod(
                "playView",
                "com.bapis.bilibili.pgc.gateway.player.v2.PlayViewReq"
            ) { param ->
                val request = param.args[0]
                val response = param.result
                    ?: "com.bapis.bilibili.pgc.gateway.player.v2.PlayViewReply".findClass(
                        mClassLoader
                    ).new()
                if (!response.callMethodAs<Boolean>("hasVideoInfo") ||
                    (response.callMethodAs("hasViewInfo") &&
                            response.callMethod("getViewInfo")
                                ?.callMethodAs<Boolean>("hasDialog") == true) &&
                    response.callMethod("getViewInfo")?.callMethod("getDialog")
                        ?.callMethodAs<String>("getType") == "area_limit"
                ) {
                    val content = getPlayUrl(reconstructQuery(request))
                    countDownLatch?.countDown()
                    content?.let {
                        Log.d("Has replaced play url with proxy server $it")
                        Log.toast("已从代理服务器获取播放地址")
                        param.result = reconstructResponse(response, it, isDownload)
                    } ?: run {
                        Log.e("Failed to get play url")
                        Log.toast("获取播放地址失败")
                        if (response.callMethodAs("hasViewInfo")) {
                            response.callMethod("getViewInfo")?.callMethod("getDialog")?.run {
                                callMethod("setMsg", "获取播放地址失败")
                                callMethod("getTitle")?.callMethod(
                                    "setText",
                                    "获取播放地址失败。请检查哔哩漫游设置里的解析服务器设置。"
                                )
                                callMethod("getImage")?.callMethod(
                                    "setUrl",
                                    "https://i0.hdslb.com/bfs/album/08d5ce2fef8da8adf91024db4a69919b8d02fd5c.png"
                                )
                            }
                        }
                    }
                } else if (isDownload) {
                    param.result = fixDownloadProto(response)
                }
            }
        }

        "com.bapis.bilibili.community.service.dm.v1.DMMoss".findClassOrNull(mClassLoader)
            ?.hookAfterMethod(
                "dmView",
                "com.bapis.bilibili.community.service.dm.v1.DmViewReq"
            ) { param ->
                val oid = param.args[0].callMethod("getOid").toString()
                // TODO: For cached bangumi's, we don't know if they need to get subtitles from thailand api.
                //       Actually, when watch_platform==1, it should use subtitles,
                //       and if it does not contains any subtitle, it means it's from thailand.
                //       However, for cached bangumi's, we don't know watch_platform.
                //       One way is to get the information from entry.json and store that to
                //       lastSeasonInfo as online bangumi's.
                var tryThailand =
                    lastSeasonInfo.containsKey("watch_platform") && lastSeasonInfo["watch_platform"] == "1"
                            && lastSeasonInfo.containsKey(oid) &&
                            (param.result == null || param.result.callMethod("getSubtitle")
                                ?.callMethod("getSubtitlesCount") == 0)
                if (!tryThailand && !lastSeasonInfo.containsKey("area")) {
                    countDownLatch = CountDownLatch(1)
                    try {
                        countDownLatch?.await(5, TimeUnit.SECONDS)
                    } catch (ignored: Throwable) {
                    }
                    tryThailand =
                        lastSeasonInfo.containsKey("area") && lastSeasonInfo["area"] == "th"
                }
                if (tryThailand) {
                    Log.d("Getting thailand subtitles")
                    val subtitles = if (lastSeasonInfo.containsKey("sb$oid")) {
                        Log.d("Got from season")
                        JSONArray(lastSeasonInfo["sb$oid"])
                    } else {
                        val result = getThailandSubtitles(
                            lastSeasonInfo[oid] ?: lastSeasonInfo["epid"]
                        )?.toJSONObject() ?: return@hookAfterMethod
                        if (result.optInt("code") != 0) return@hookAfterMethod
                        val data = result.optJSONObject("data") ?: return@hookAfterMethod
                        Log.d("Got from subtitle api")
                        data.optJSONArray("subtitles").orEmpty()
                    }
                    if (subtitles.length() == 0) return@hookAfterMethod
                    val newResBuilder = param.result?.let {
                        DmViewReply.newBuilder(
                            DmViewReply.parseFrom(
                                param.result.callMethodAs<ByteArray>("toByteArray")
                            )
                        )
                    } ?: run {
                        DmViewReply.newBuilder()
                    }
                    newResBuilder.subtitle = VideoSubtitle.newBuilder().run {
                        for (subtitle in subtitles) {
                            addSubtitles(SubtitleItem.newBuilder().run {
                                id = subtitle.optLong("id")
                                idStr = subtitle.optLong("id").toString()
                                subtitleUrl = subtitle.optString("url")
                                lan = subtitle.optString("key")
                                lanDoc = subtitle.optString("title")
                                build()
                            })
                        }
                        build()
                    }
                    param.result = (param.method as Method).returnType.callStaticMethod(
                        "parseFrom",
                        newResBuilder.build().toByteArray()
                    )
                }
            }
    }

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

    private fun fixDownloadProto(response: Any): Any {
        val serializedRequest = response.callMethodAs<ByteArray>("toByteArray")
        val newRes =
            fixDownloadProto(PlayViewReply.newBuilder(PlayViewReply.parseFrom(serializedRequest)))
        val serializedResponse = newRes.toByteArray()
        return response.javaClass.callStaticMethod("parseFrom", serializedResponse)!!
    }

    private fun fixDownloadProto(builder: PlayViewReply.Builder) = builder.run {
        videoInfo = VideoInfo.newBuilder(builder.videoInfo).run {
            var audioId = 0
            var setted = false
            val streams = streamListList.map {
                if (it.streamInfo.quality != quality || setted) {
                    Stream.newBuilder(it).run {
                        clearContent()
                        build()
                    }
                } else {
                    audioId = it.dashVideo.audioId
                    setted = true
                    it
                }
            }
            val audio = dashAudioList.first {
                it.id != audioId
            }
            clearStreamList()
            clearDashAudio()
            addAllStreamList(streams)
            addDashAudio(audio)
            build()
        }
        build()
    }


    private fun isLimitWatchingArea(jsonText: String) = try {
        val json = JSONObject(jsonText)
        val code = json.optInt("code")
        code == -10403
    } catch (e: JSONException) {
        Log.e(e)
        false
    }

    private fun needForceProxy(response: Any): Boolean {
        if (sPrefs.getString("customize_accessKey", "").isNullOrBlank()) return false
        val serializedRequest = response.callMethodAs<ByteArray>("toByteArray")
        return PlayViewReply.parseFrom(serializedRequest).business.isPreview
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
            val builder = PlayViewReply.newBuilder()
            builder.playConf = PlayAbilityConf.newBuilder().run {
                dislikeDisable = true
                likeDisable = true
                elecDisable = true
                build()
            }
            val videoInfoBuilder = VideoInfo.newBuilder().apply {
                timelength = jsonContent.optLong("timelength")
                videoCodecid = jsonContent.optInt("video_codecid")
                quality = jsonContent.optInt("quality")
                format = jsonContent.optString("format")
            }
            val qualityMap = jsonContent.optJSONArray("accept_quality")?.let {
                (0 until it.length()).map { idx -> it.optInt(idx) }
            }
            val type = jsonContent.optString("type")
            val formatMap = HashMap<Int, JSONObject>()
            for (format in jsonContent.optJSONArray("support_formats").orEmpty()) {
                formatMap[format.optInt("quality")] = format
            }
            if (type == "DASH") {
                val audioIds = ArrayList<Int>()
                for (audio in jsonContent.optJSONObject("dash")?.optJSONArray("audio").orEmpty()) {
                    videoInfoBuilder.addDashAudio(DashItem.newBuilder().run {
                        audio.run {
                            baseUrl = optString("base_url")
                            id = optInt("id")
                            audioIds.add(id)
                            md5 = optString("md5")
                            size = optLong("size")
                            codecid = optInt("codecid")
                            bandwidth = optInt("bandwidth")
                            for (bk in optJSONArray("backup_url").orEmpty().asSequence<String>())
                                addBackupUrl(bk)
                        }
                        build()
                    })
                }
                for (video in jsonContent.optJSONObject("dash")?.optJSONArray("video").orEmpty()) {
                    if (video.optInt("codecid") != videoCodeCid) continue
                    videoInfoBuilder.addStreamList(Stream.newBuilder().run {
                        dashVideo = DashVideo.newBuilder().run {
                            video.run {
                                baseUrl = optString("base_url")
                                for (bk in optJSONArray("backup_url").orEmpty()
                                    .asSequence<String>())
                                    addBackupUrl(bk)
                                bandwidth = optInt("bandwidth")
                                codecid = optInt("codecid")
                                md5 = optString("md5")
                                size = optLong("size")
                            }
                            // Not knowing the extract matching,
                            // just use the largest id
                            audioId = audioIds.maxOrNull() ?: audioIds[0]
                            noRexcode = jsonContent.optInt("no_rexcode") != 0
                            build()
                        }
                        streamInfo = StreamInfo.newBuilder().run {
                            quality = video.optInt("id")
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
            } else if (type == "FLV" || type == "MP4") {
                qualityMap?.forEach { quality ->
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
                        if (quality == jsonContent.optInt("quality")) {
                            segmentVideo = SegmentVideo.newBuilder().run {
                                for (segment in jsonContent.optJSONArray("durl").orEmpty()) {
                                    addSegment(ResponseUrl.newBuilder().run {
                                        segment.run {
                                            length = optLong("length")
                                            for (bk in optJSONArray("backup_url").orEmpty()
                                                .asSequence<String>())
                                                addBackupUrl(bk)
                                            md5 = optString("md5")
                                            order = optInt("order")
                                            size = optLong("size")
                                            url = optString("url")
                                        }
                                        build()
                                    })
                                }
                                build()
                            }
                        }
                        build()
                    })
                }
            }

            builder.videoInfo = videoInfoBuilder.build()
            builder.business = BusinessInfo.newBuilder().run {
                isPreview = jsonContent.optInt("is_preview", 0) == 1
                build()
            }
            val serializedResponse =
                (if (isDownload) fixDownloadProto(builder) else builder.build()).toByteArray()
            return response.javaClass.callStaticMethod("parseFrom", serializedResponse)!!
        } catch (e: Throwable) {
            Log.e(e)
        }
        return response
    }

    private fun reconstructFormat(builder: StreamInfo.Builder, fmt: JSONObject) {
        builder.run {
            fmt.run {
                description = optString("description")
                format = optString("format")
                needVip = optBoolean("need_vip", false)
                needLogin = optBoolean("need_login", false)
                newDescription = optString("new_description")
                superscript = optString("superscript")
                displayDesc = optString("display_desc")
            }
        }
    }
}
