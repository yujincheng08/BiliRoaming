package me.iacn.biliroaming.hook

import android.net.Uri
import com.google.protobuf.any
import me.iacn.biliroaming.*
import me.iacn.biliroaming.API.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.hook.BangumiSeasonHook.Companion.lastSeasonInfo
import me.iacn.biliroaming.network.BiliRoamingApi.CustomServerException
import me.iacn.biliroaming.network.BiliRoamingApi.getPlayUrl
import me.iacn.biliroaming.network.BiliRoamingApi.getSeason
import me.iacn.biliroaming.utils.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

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
        val qnApplied = AtomicBoolean(false)
        private const val PGC_ANY_MODEL_TYPE_URL =
            "type.googleapis.com/bilibili.app.playerunite.pgcanymodel.PGCAnyModel"
        private val codecMap =
            mapOf(CodeType.CODE264 to 7, CodeType.CODE265 to 12, CodeType.CODEAV1 to 13)
    }

    private val defaultQn: Int?
        get() = instance.playerSettingHelperClass?.callStaticMethodAs<Int>(instance.getDefaultQn())

    override fun startHook() {
        if (!sPrefs.getBoolean("main_func", false)) return
        Log.d("startHook: BangumiPlayUrl")
        val blockBangumiPageAds = sPrefs.getBoolean("block_bangumi_page_ads", false)
        val halfScreenQuality = sPrefs.getString("half_screen_quality", "0")?.toInt() ?: 0
        val fullScreenQuality = sPrefs.getString("full_screen_quality", "0")?.toInt() ?: 0

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
                Log.toast("请求解析服务器发生错误: ${e.message}", alsoLog = true)
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
                Log.toast("请求解析服务器发生错误: ${e.message}", alsoLog = true)
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
                isDownload = sPrefs.getBoolean("allow_download", false)
                        && request.callMethodAs<Int>("getDownload") >= 1
                if (isDownload) {
                    if (!sPrefs.getBoolean("fix_download", false)
                        || request.callMethodAs<Int>("getFnval") <= 1
                    ) {
                        request.callMethod("setFnval", MAX_FNVAL)
                        request.callMethod("setFourk", true)
                    }
                    request.callMethod("setDownload", 0)
                } else if (halfScreenQuality == 1 || fullScreenQuality != 0) {
                    request.callMethod("setFnval", MAX_FNVAL)
                    request.callMethod("setFourk", true)
                    if (halfScreenQuality == 1 && qnApplied.compareAndSet(false, true)) {
                        defaultQn?.let { request.callMethod("setQn", it) }
                    }
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
                        val serializedRequest = request.callMethodAs<ByteArray>("toByteArray")
                        val req = PlayViewReq.parseFrom(serializedRequest)
                        val thaiSeason = lazy {
                            val seasonId = req.seasonId.toString().takeIf { it != "0" }
                                ?: lastSeasonInfo["season_id"] ?: "0"
                            getSeason(
                                mapOf("season_id" to seasonId, "ep_id" to req.epId.toString()),
                                true
                            )?.toJSONObject()?.optJSONObject("result")
                        }
                        val content = getPlayUrl(reconstructQuery(req, response, thaiSeason))
                        countDownLatch?.countDown()
                        content?.let {
                            Log.toast("已从代理服务器获取播放地址\n如加载缓慢或黑屏，可去漫游设置中测速并设置 UPOS")
                            param.result =
                                reconstructResponse(req, response, it, isDownload, thaiSeason)
                        } ?: run {
                            Log.toast("获取播放地址失败", alsoLog = true)
                        }
                    } catch (e: CustomServerException) {
                        param.result = showPlayerError(
                            response,
                            "请求解析中服务器发生错误(点此查看更多)\n${e.message}"
                        )
                        Log.toast("请求解析服务器发生错误: ${e.message}", alsoLog = true)
                    }
                } else if (isDownload) {
                    param.result = fixDownloadProto(response)
                } else if (blockBangumiPageAds) {
                    param.result = purifyViewInfo(response)
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
                isDownload = sPrefs.getBoolean("allow_download", false)
                        && request.callMethodAs<Int>("getDownload") >= 1
                if (isDownload) {
                    if (!sPrefs.getBoolean("fix_download", false)
                        || request.callMethodAs<Int>("getFnval") <= 1
                    ) {
                        request.callMethod("setFnval", MAX_FNVAL)
                        request.callMethod("setFourk", true)
                    }
                    request.callMethod("setDownload", 0)
                } else if (halfScreenQuality == 1 || fullScreenQuality != 0) {
                    request.callMethod("setFnval", MAX_FNVAL)
                    request.callMethod("setFourk", true)
                    if (halfScreenQuality == 1 && qnApplied.compareAndSet(false, true)) {
                        defaultQn?.let { request.callMethod("setQn", it) }
                    }
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
                val response =
                    param.result ?: "com.bapis.bilibili.pgc.gateway.player.v2.PlayViewReply"
                        .on(mClassLoader).new()
                if (needProxy(response)) {
                    try {
                        val serializedRequest = request.callMethodAs<ByteArray>("toByteArray")
                        val req = PlayViewReq.parseFrom(serializedRequest)
                        val thaiSeason = lazy {
                            val seasonId = req.seasonId.toString().takeIf { it != "0" }
                                ?: lastSeasonInfo["season_id"] ?: "0"
                            getSeason(
                                mapOf("season_id" to seasonId, "ep_id" to req.epId.toString()),
                                true
                            )?.toJSONObject()?.optJSONObject("result")
                        }
                        val content = getPlayUrl(reconstructQuery(req, response, thaiSeason))
                        countDownLatch?.countDown()
                        content?.let {
                            Log.toast("已从代理服务器获取播放地址\n如加载缓慢或黑屏，可去漫游设置中测速并设置 UPOS")
                            param.result =
                                reconstructResponse(req, response, it, isDownload, thaiSeason)
                        }
                            ?: throw CustomServerException(mapOf("未知错误" to "请检查哔哩漫游设置中解析服务器设置。"))
                    } catch (e: CustomServerException) {
                        param.result = showPlayerError(
                            response,
                            "请求解析中服务器发生错误(点此查看更多)\n${e.message}"
                        )
                        Log.toast("请求解析服务器发生错误: ${e.message}", alsoLog = true)
                    }
                } else if (isDownload) {
                    param.result = fixDownloadProto(response)
                } else if (blockBangumiPageAds) {
                    param.result = purifyViewInfo(response)
                }
            }
        }
        instance.playerMossClass?.run {
            var isDownload = false
            hookBeforeMethod("playViewUnite", instance.playViewUniteReqClass) { param ->
                val request = param.args[0]
                val vod = request.callMethod("getVod") ?: return@hookBeforeMethod
                isDownload = sPrefs.getBoolean("allow_download", false)
                        && vod.callMethodAs<Int>("getDownload") >= 1
                if (isDownload) {
                    if (!sPrefs.getBoolean("fix_download", false)
                        || vod.callMethodAs<Int>("getFnval") <= 1
                    ) {
                        vod.callMethod("setFnval", MAX_FNVAL)
                        vod.callMethod("setFourk", true)
                    }
                    vod.callMethod("setDownload", 0)
                } else if (halfScreenQuality != 0 || fullScreenQuality != 0) {
                    // unlock available quality limit, allow quality up to 8K
                    vod.callMethod("setFnval", MAX_FNVAL)
                    vod.callMethod("setFourk", true)
                    if (halfScreenQuality != 0 && qnApplied.compareAndSet(false, true)) {
                        if (halfScreenQuality != 1) {
                            vod.callMethod("setQn", halfScreenQuality)
                        } else {
                            // follow full screen quality
                            defaultQn?.let { vod.callMethod("setQn", it) }
                        }
                    }
                }
            }
            hookAfterMethod("playViewUnite", instance.playViewUniteReqClass) { param ->
                if (instance.networkExceptionClass?.isInstance(param.throwable) == true)
                    return@hookAfterMethod
                val request = param.args[0]
                val response =
                    param.result ?: "com.bapis.bilibili.app.playerunite.v1.PlayViewUniteReply"
                        .on(mClassLoader).new()
                val supplementAny = response.callMethod("getSupplement")
                val typeUrl = supplementAny?.callMethodAs<String>("getTypeUrl")
                // Only handle pgc video
                if (!typeUrl.isNullOrEmpty() && typeUrl != PGC_ANY_MODEL_TYPE_URL)
                    return@hookAfterMethod
                val supplement = supplementAny?.callMethod("getValue")
                    ?.callMethodAs<ByteArray>("toByteArray")
                    ?.let { PlayViewReply.parseFrom(it) } ?: playViewReply {}
                if (needProxyUnite(response, supplement)) {
                    try {
                        val serializedRequest = request.callMethodAs<ByteArray>("toByteArray")
                        val req = PlayViewUniteReq.parseFrom(serializedRequest)
                        val thaiSeason = lazy {
                            val seasonId = req.extraContentMap["season_id"].takeIf { it != "0" }
                                ?: lastSeasonInfo["season_id"] ?: "0"
                            val epId = req.extraContentMap["ep_id"]
                            getSeason(
                                mapOf("season_id" to seasonId, "ep_id" to epId),
                                true
                            )?.toJSONObject()?.optJSONObject("result")
                        }
                        val content = getPlayUrl(reconstructQueryUnite(req, supplement, thaiSeason))
                        countDownLatch?.countDown()
                        content?.let {
                            Log.toast("已从代理服务器获取播放地址\n如加载缓慢或黑屏，可去漫游设置中测速并设置 UPOS")
                            param.result = reconstructResponseUnite(
                                req, response, supplement, it, isDownload, thaiSeason
                            )
                        }
                            ?: throw CustomServerException(mapOf("未知错误" to "请检查哔哩漫游设置中解析服务器设置。"))
                    } catch (e: CustomServerException) {
                        param.result = showPlayerErrorUnite(
                            response, supplement,
                            "请求解析中服务器发生错误(点此查看更多)\n${e.message}"
                        )
                        Log.toast("请求解析服务器发生错误: ${e.message}", alsoLog = true)
                    }
                } else if (isDownload) {
                    param.result = fixDownloadProtoUnite(response)
                } else if (blockBangumiPageAds) {
                    param.result = purifyViewInfo(response, supplement)
                }
            }
        }
        instance.playURLMossClass?.hookBeforeMethod(
            "playView", instance.playViewReqClass
        ) { param ->
            val request = param.args[0]
            val isDownload = request.callMethodAs<Int>("getDownload") >= 1
            if (isDownload) return@hookBeforeMethod
            if (halfScreenQuality != 0 || fullScreenQuality != 0) {
                request.callMethod("setFnval", MAX_FNVAL)
                request.callMethod("setFourk", true)
                if (halfScreenQuality != 0 && qnApplied.compareAndSet(false, true)) {
                    if (halfScreenQuality != 1) {
                        request.callMethod("setQn", halfScreenQuality)
                    } else {
                        defaultQn?.let { request.callMethod("setQn", it) }
                    }
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

    private fun needProxyUnite(response: Any, supplement: PlayViewReply): Boolean {
        if (!response.callMethodAs<Boolean>("hasVodInfo")) return true

        val viewInfo = supplement.viewInfo
        if (viewInfo.dialog.type == "area_limit") return true
        if (viewInfo.endPage.dialog.type == "area_limit") return true

        sPrefs.getString("cn_server_accessKey", null) ?: return false
        if (supplement.business.isPreview) return true
        if (viewInfo.dialog.type.isNotEmpty()) return true
        if (viewInfo.endPage.dialog.type.isNotEmpty()) return true
        return false
    }

    private fun PlayViewReply.toErrorReply(message: String) = copy {
        viewInfo = viewInfo.copy {
            if (endPage.hasDialog()) {
                dialog = endPage.dialog
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
            clearEndPage()
        }
        clearVideoInfo()
    }

    private fun showPlayerError(response: Any, message: String) = runCatchingOrNull {
        val serializedResponse = response.callMethodAs<ByteArray>("toByteArray")
        val newRes = PlayViewReply.parseFrom(serializedResponse).toErrorReply(message)
        response.javaClass.callStaticMethod("parseFrom", newRes.toByteArray())
    } ?: response

    private fun showPlayerErrorUnite(response: Any, supplement: PlayViewReply, message: String) =
        runCatchingOrNull {
            val serializedResponse = response.callMethodAs<ByteArray>("toByteArray")
            val newRes = PlayViewUniteReply.parseFrom(serializedResponse).copy {
                this.supplement = any {
                    typeUrl = PGC_ANY_MODEL_TYPE_URL
                    value = supplement.toErrorReply(message).toByteString()
                }
                clearVodInfo()
            }.toByteArray()
            response.javaClass.callStaticMethod("parseFrom", newRes)
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

    private fun VideoInfoKt.Dsl.fixDownloadProto(checkBaseUrl: Boolean = false) {
        var audioId = 0
        var setted = false
        val checkConnection = fun(url: String) = runCatchingOrNull {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 1000
            connection.readTimeout = 1000
            connection.connect()
            connection.responseCode == HttpURLConnection.HTTP_OK
        } ?: false
        val streams = streamList.map { s ->
            if (s.streamInfo.quality != quality || setted) {
                s.copy { clearContent() }
            } else {
                audioId = s.dashVideo.audioId
                setted = true
                if (checkBaseUrl) {
                    s.copy {
                        dashVideo = dashVideo.copy {
                            if (!checkConnection(baseUrl))
                                backupUrl.find { checkConnection(it) }?.let {
                                    baseUrl = it
                                }
                        }
                    }
                } else s
            }
        }
        val audio = (dashAudio.find {
            it.id == audioId
        } ?: dashAudio.first()).let { a ->
            if (checkBaseUrl) {
                a.copy {
                    if (!checkConnection(baseUrl))
                        backupUrl.find { checkConnection(it) }?.let {
                            baseUrl = it
                        }
                }
            } else a
        }
        streamList.clear()
        dashAudio.clear()
        streamList += streams
        dashAudio += audio
    }

    private fun fixDownloadProto(response: Any) = runCatchingOrNull {
        val serializedResponse = response.callMethodAs<ByteArray>("toByteArray")
        val newRes = PlayViewReply.parseFrom(serializedResponse).copy {
            videoInfo = videoInfo.copy { fixDownloadProto() }
        }.toByteArray()
        response.javaClass.callStaticMethod("parseFrom", newRes)
    } ?: response

    private fun fixDownloadProtoUnite(response: Any) = runCatchingOrNull {
        val serializedResponse = response.callMethodAs<ByteArray>("toByteArray")
        val newRes = PlayViewUniteReply.parseFrom(serializedResponse).copy {
            vodInfo = vodInfo.copy { fixDownloadProto() }
        }.toByteArray()
        response.javaClass.callStaticMethod("parseFrom", newRes)
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
        val serializedResponse = response.callMethodAs<ByteArray>("toByteArray")
        return PlayViewReply.parseFrom(serializedResponse).business.isPreview
    }

    private fun reconstructQuery(
        req: PlayViewReq,
        response: Any,
        thaiSeason: Lazy<JSONObject?>
    ): String? {
        val episodeInfo by lazy {
            response.callMethodOrNull("getBusiness")?.callMethodOrNull("getEpisodeInfo")
        }
        val thaiEpisodeId by lazy {
            thaiSeason.value?.let {
                it.optJSONArray("modules").orEmpty().asSequence<JSONObject>()
                    .firstOrNull()
                    ?.optJSONObject("data")?.optJSONArray("episodes").orEmpty()
                    .asSequence<JSONObject>()
                    .firstOrNull()
                    ?.optLong("id") ?: it.optJSONObject("new_ep")?.optLong("id")
            } ?: 0L
        }
        // CANNOT use reflection for compatibility with Xpatch
        return Uri.Builder().run {
            appendQueryParameter("ep_id", req.epId.let {
                if (it != 0L) it else episodeInfo?.callMethodOrNullAs<Int>("getEpId") ?: 0
            }.let {
                if (it != 0) it else thaiEpisodeId.toInt()
            }.toString())
            appendQueryParameter("cid", req.cid.let {
                if (it != 0L) it else episodeInfo?.callMethodOrNullAs<Long>("getCid") ?: 0
            }.let {
                if (it != 0L) it else thaiEpisodeId.toInt()
            }.toString())
            appendQueryParameter("qn", req.qn.toString())
            appendQueryParameter("fnver", req.fnver.toString())
            appendQueryParameter("fnval", req.fnval.toString())
            appendQueryParameter("force_host", req.forceHost.toString())
            appendQueryParameter("fourk", if (req.fourk) "1" else "0")
            build()
        }.query
    }

    private fun reconstructQueryUnite(
        req: PlayViewUniteReq,
        supplement: PlayViewReply,
        thaiSeason: Lazy<JSONObject?>
    ): String? {
        val episodeInfo = supplement.business.episodeInfo
        val thaiEpisodeId by lazy {
            thaiSeason.value?.optJSONArray("modules").orEmpty().asSequence<JSONObject>()
                .firstOrNull()
                ?.optJSONObject("data")?.optJSONArray("episodes").orEmpty().asSequence<JSONObject>()
                .firstOrNull()
                ?.optLong("id") ?: 0L
        }
        // CANNOT use reflection for compatibility with Xpatch
        return Uri.Builder().run {
            appendQueryParameter("ep_id", req.extraContentMap["ep_id"].let {
                if (!it.isNullOrEmpty() && it != "0") it.toInt() else episodeInfo.epId
            }.let {
                if (it != 0) it else thaiEpisodeId.toInt()
            }.toString())
            appendQueryParameter("cid", req.vod.cid.let {
                if (it != 0L) it else episodeInfo.cid
            }.let {
                if (it != 0L) it else thaiEpisodeId.toInt()
            }.toString())
            appendQueryParameter("qn", req.vod.qn.toString())
            appendQueryParameter("fnver", req.vod.fnver.toString())
            appendQueryParameter("fnval", req.vod.fnval.toString())
            appendQueryParameter("force_host", req.vod.forceHost.toString())
            appendQueryParameter("fourk", if (req.vod.fourk) "1" else "0")
            build()
        }.query
    }

    private fun reconstructResponse(
        req: PlayViewReq,
        response: Any,
        content: String,
        isDownload: Boolean,
        thaiSeason: Lazy<JSONObject?>
    ) = runCatching {
        var jsonContent = content.toJSONObject()
        if (jsonContent.has("result")) {
            // For kghost server
            val result = jsonContent.opt("result")
            if (result != null && result !is String) {
                jsonContent = jsonContent.getJSONObject("result")
            }
        }
        val serializedResponse = response.callMethodAs<ByteArray>("toByteArray")
        val newRes = PlayViewReply.parseFrom(serializedResponse).copy {
            playConf = playAbilityConf {
                dislikeDisable = true
                likeDisable = true
                elecDisable = true
                freyaEnterDisable = true
                freyaFullDisable = true
            }
            videoInfo = jsonContent.toVideoInfo(req.preferCodecType, isDownload)
            fixBusinessProto(thaiSeason, jsonContent)
            viewInfo = viewInfo {}
        }.toByteArray()
        response.javaClass.callStaticMethod("parseFrom", newRes)
    }.onFailure { Log.e(it) }.getOrDefault(response)

    private fun reconstructResponseUnite(
        req: PlayViewUniteReq,
        response: Any,
        supplement: PlayViewReply,
        content: String,
        isDownload: Boolean,
        thaiSeason: Lazy<JSONObject?>
    ) = runCatching {
        var jsonContent = content.toJSONObject()
        if (jsonContent.has("result")) {
            // For kghost server
            val result = jsonContent.opt("result")
            if (result != null && result !is String) {
                jsonContent = jsonContent.getJSONObject("result")
            }
        }
        val serializedResponse = response.callMethodAs<ByteArray>("toByteArray")
        val newRes = PlayViewUniteReply.parseFrom(serializedResponse).copy {
            vodInfo = jsonContent.toVideoInfo(req.vod.preferCodeType, isDownload)
            val newSupplement = supplement.copy {
                fixBusinessProto(thaiSeason, jsonContent)
                viewInfo = viewInfo {}
            }
            this.supplement = any {
                typeUrl = PGC_ANY_MODEL_TYPE_URL
                value = newSupplement.toByteString()
            }
            playArcConf = playArcConf {
                arcConf[1] = arcConf { isSupport = true } //FLIPCONF
                arcConf[2] = arcConf { isSupport = true } //CASTCONF
                arcConf[3] = arcConf { isSupport = true } //FEEDBACK
                arcConf[4] = arcConf { isSupport = true } //SUBTITLE
                arcConf[5] = arcConf { isSupport = true } //PLAYBACKRATE
                arcConf[6] = arcConf { isSupport = true } //TIMEUP
                arcConf[7] = arcConf { isSupport = true } //PLAYBACKMODE
                arcConf[8] = arcConf { isSupport = true } //SCALEMODE
                arcConf[9] = arcConf { isSupport = true } //BACKGROUNDPLAY
                arcConf[10] = arcConf { isSupport = true } //LIKE
                arcConf[12] = arcConf { isSupport = true } //COIN
                arcConf[14] = arcConf { isSupport = true } //SHARE
                arcConf[15] = arcConf { isSupport = true } //SCREENSHOT
                arcConf[16] = arcConf { isSupport = true } //LOCKSCREEN
                arcConf[17] = arcConf { isSupport = true } //RECOMMEND
                arcConf[18] = arcConf { isSupport = true } //PLAYBACKSPEED
                arcConf[19] = arcConf { isSupport = true } //DEFINITION
                arcConf[20] = arcConf { isSupport = true } //SELECTIONS
                arcConf[21] = arcConf { isSupport = true } //NEXT
                arcConf[22] = arcConf { isSupport = true } //EDITDM
                arcConf[23] = arcConf { isSupport = true } //SMALLWINDOW
                arcConf[25] = arcConf { isSupport = true } //OUTERDM
                arcConf[26] = arcConf { isSupport = true } //INNERDM
                arcConf[29] = arcConf { isSupport = true } //COLORFILTER
                arcConf[31] = arcConf { disabled = true } //FREYAENTER
                arcConf[32] = arcConf { disabled = true } //FREYAFULLENTER
                arcConf[34] = arcConf { isSupport = true } //RECORDSCREEN
            }
        }.toByteArray()
        response.javaClass.callStaticMethod("parseFrom", newRes)
    }.onFailure { Log.e(it) }.getOrDefault(response)

    private fun PlayViewReplyKt.Dsl.fixBusinessProto(
        thaiSeason: Lazy<JSONObject?>,
        jsonContent: JSONObject
    ) {
        if (hasBusiness()) {
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
        } else {
            // thai
            business = businessInfo {
                val season = thaiSeason.value
                val episode = season?.let {
                    it.optJSONArray("modules").orEmpty()
                        .asSequence<JSONObject>().firstOrNull()
                        ?.optJSONObject("data")?.optJSONArray("episodes").orEmpty()
                        .asSequence<JSONObject>().firstOrNull()
                        ?: it.optJSONObject("new_ep")?.apply { put("status", 2L) }
                } ?: throw CustomServerException(mapOf("解析服务器错误" to "无法获取剧集信息"))
                isPreview = jsonContent.optInt("is_preview", 0) == 1
                episodeInfo = episodeInfo {
                    epId = episode.optInt("id")
                    cid = episode.optLong("id")
                    aid = season.optLong("season_id")
                    epStatus = episode.optLong("status")
                    cover = episode.optString("cover")
                    title = episode.optString("title")
                    seasonInfo = seasonInfo {
                        seasonId = season.optInt("season_id")
                        seasonType = season.optInt("type")
                        seasonStatus = season.optInt("status")
                        cover = season.optString("cover")
                        title = season.optString("title")
                        rights = seasonRights {
                            canWatch = 1
                        }
                    }
                }
            }
        }
    }

    private fun JSONObject.toVideoInfo(preferCodec: CodeType, isDownload: Boolean) = videoInfo {
        val qualityList = optJSONArray("accept_quality")
            ?.asSequence<Int>()?.toList().orEmpty()
        val type = optString("type")
        val videoCodecId = optInt("video_codecid")
        val formatMap = HashMap<Int, JSONObject>()
        for (format in optJSONArray("support_formats").orEmpty()) {
            formatMap[format.optInt("quality")] = format
        }

        timelength = optLong("timelength")
        videoCodecid = videoCodecId
        quality = optInt("quality")
        format = optString("format")

        if (type == "DASH") {
            val audioIds = ArrayList<Int>()
            for (audio in optJSONObject("dash")?.optJSONArray("audio").orEmpty()) {
                dashAudio += dashItem {
                    audio.run {
                        baseUrl = optString("base_url")
                        id = optInt("id")
                        audioIds.add(id)
                        md5 = optString("md5")
                        size = optLong("size")
                        codecid = optInt("codecid")
                        bandwidth = optInt("bandwidth")
                        for (bk in optJSONArray("backup_url").orEmpty().asSequence<String>())
                            backupUrl += bk
                    }
                }
            }
            var bestMatchQn = quality
            var minDeltaQn = Int.MAX_VALUE
            val preferCodecId = codecMap[preferCodec] ?: videoCodecId
            val videos = optJSONObject("dash")?.optJSONArray("video")
                ?.asSequence<JSONObject>()?.toList().orEmpty()
            val availableQns = videos.map { it.optInt("id") }.toSet()
            val preferVideos = videos.filter { it.optInt("codecid") == preferCodecId }
                .takeIf { l -> l.map { it.optInt("id") }.containsAll(availableQns) }
                ?: videos.filter { it.optInt("codecid") == videoCodecId }
            preferVideos.forEach { video ->
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
                        noRexcode = optInt("no_rexcode") != 0
                    }
                    streamInfo = streamInfo {
                        quality = video.optInt("id")
                        val deltaQn = abs(quality - this@videoInfo.quality)
                        if (deltaQn < minDeltaQn) {
                            bestMatchQn = quality
                            minDeltaQn = deltaQn
                        }
                        intact = true
                        attribute = 0
                        formatMap[quality]?.let { fmt ->
                            reconstructFormat(fmt)
                        }
                    }
                }
            }
            quality = bestMatchQn
        } else if (type == "FLV" || type == "MP4") {
            qualityList.forEach { quality ->
                streamList += stream {
                    streamInfo = streamInfo {
                        this.quality = quality
                        intact = true
                        attribute = 0
                        formatMap[quality]?.let { fmt ->
                            reconstructFormat(fmt)
                        }
                    }

                    if (quality == optInt("quality")) {
                        segmentVideo = segmentVideo {
                            for (seg in optJSONArray("durl").orEmpty()) {
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

        if (isDownload) {
            fixDownloadProto(true)
        }
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

    private fun purifyViewInfo(response: Any, supplement: PlayViewReply? = null) = runCatching {
        supplement?.copy {
            playExtConf = playExtConf.copy { clearFreyaConfig() }
            viewInfo = viewInfo.copy {
                clearAnimation()
                clearCouponInfo()
                if (endPage.dialog.type != "pay")
                    clearEndPage()
                clearHighDefinitionTrialInfo()
                clearPayTip()
                if (popWin.buttonList.all { it.actionType != "pay" })
                    clearPopWin()
                if (toast.button.actionType != "pay")
                    clearToast()
                if (tryWatchPromptBar.buttonList.all { it.actionType != "pay" })
                    clearTryWatchPromptBar()
                extToast.clear()
            }
        }?.let {
            val serializedResponse = response.callMethodAs<ByteArray>("toByteArray")
            val newRes = PlayViewUniteReply.parseFrom(serializedResponse).copy {
                this.supplement = any {
                    typeUrl = PGC_ANY_MODEL_TYPE_URL
                    value = it.toByteString()
                }
            }.toByteArray()
            response.javaClass.callStaticMethod("parseFrom", newRes)
        } ?: run {
            response.callMethodOrNull("getPlayExtConf")
                ?.callMethod("clearFreyaConfig")
            response.callMethod("getViewInfo")?.run {
                callMethodOrNull("clearAnimation")
                callMethod("clearCouponInfo")
                if (callMethod("getEndPage")
                        ?.callMethod("getDialog")
                        ?.callMethod("getType") != "pay"
                ) callMethod("clearEndPage")
                callMethod("clearHighDefinitionTrialInfo")
                callMethod("clearPayTip")
                if (callMethod("getPopWin")
                        ?.callMethodAs<List<Any>>("getButtonList")
                        ?.all { it.callMethod("getActionType") != "pay" } != false
                ) callMethod("clearPopWin")
                if (callMethod("getToast")
                        ?.callMethod("getButton")
                        ?.callMethod("getActionType") != "pay"
                ) callMethod("clearToast")
                if (callMethod("getTryWatchPromptBar")
                        ?.callMethodAs<List<Any>>("getButtonList")
                        ?.all { it.callMethod("getActionType") != "pay" } != false
                ) callMethod("clearTryWatchPromptBar")
                callMethodOrNullAs<LinkedHashMap<*, *>>("internalGetMutableExtToast")?.clear()
            }
            response
        }
    }.onFailure { Log.e(it) }.getOrDefault(response)
}
