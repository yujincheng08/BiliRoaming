package me.iacn.biliroaming.hook

import android.net.Uri
import com.google.protobuf.any
import me.iacn.biliroaming.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.hook.BangumiSeasonHook.Companion.lastSeasonInfo
import me.iacn.biliroaming.network.BiliRoamingApi.CustomServerException
import me.iacn.biliroaming.network.BiliRoamingApi.getPlayUrl
import me.iacn.biliroaming.network.BiliRoamingApi.getSeason
import me.iacn.biliroaming.utils.*
import me.iacn.biliroaming.utils.UposReplaceHelper.enableUposReplace
import me.iacn.biliroaming.utils.UposReplaceHelper.isPCdnUpos
import me.iacn.biliroaming.utils.UposReplaceHelper.replaceUpos
import me.iacn.biliroaming.utils.UposReplaceHelper.videoUposBackups
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
        val qnApplied = AtomicBoolean(false)
        private const val PGC_ANY_MODEL_TYPE_URL =
            "type.googleapis.com/bilibili.app.playerunite.pgcanymodel.PGCAnyModel"
        private const val UGC_ANY_MODEL_TYPE_URL =
            "type.googleapis.com/bilibili.app.playerunite.ugcanymodel.UGCAnyModel"
        private val codecMap =
            mapOf(CodeType.CODE264 to 7, CodeType.CODE265 to 12, CodeType.CODEAV1 to 13)
        val supportedPlayArcIndices = arrayOf(
            1, // FILPCONF
            2, // CASTCONF
            3, // FEEDBACK
            4, // SUBTITLE
            5, // PLAYBACKRATE
            6, // TIMEUP
            7, // PLAYBACKMODE
            8, // SCALEMODE
            9, // BACKGROUNDPLAY
            10, // LIKE
            12, // COIN
            14, // SHARE
            15, // SCREENSHOT
            16, // LOCKSCREEN
            17, // RECOMMEND
            18, // PLAYBACKSPEED
            19, // DEFINITION
            20, // SELECTIONS
            21, // NEXT
            22, // EDITDM
            23, // SMALLWINDOW
            25, // OUTERDM
            26, // INNERDM
            29, // COLORFILTER
            34, // RECORDSCREEN
        )
    }

    private val defaultQn: Int?
        get() = instance.playerSettingHelperClass?.callStaticMethodAs<Int>(instance.getDefaultQn())

    override fun startHook() {
        if (!sPrefs.getBoolean("main_func", false)) return
        Log.d("startHook: BangumiPlayUrl")
        val blockBangumiPageAds = sPrefs.getBoolean("block_view_page_ads", false)
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
                        val seasonId = req.seasonId.toString().takeIf { it != "0" }
                            ?: lastSeasonInfo["season_id"] ?: "0"
                        val (thaiSeason, thaiEp) = getSeasonLazy(seasonId, req.epId)
                        val content = getPlayUrl(reconstructQuery(req, response, thaiEp))
                        content?.let {
                            Log.toast("已从代理服务器获取播放地址\n如加载缓慢或黑屏，可去漫游设置中测速并设置 UPOS")
                            param.result = reconstructResponse(
                                req, response, it, isDownload, thaiSeason, thaiEp
                            )
                        } ?: run {
                            Log.toast("获取播放地址失败", alsoLog = true)
                        }
                    } catch (e: CustomServerException) {
                        param.result = showPlayerError(
                            response,
                            "请求解析服务器发生错误(点此查看更多)\n${e.message}"
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
                if (instance.useNewMossFunc) "executePlayView" else "playView",
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
                if (instance.useNewMossFunc) "executePlayView" else "playView",
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
                        val seasonId = req.seasonId.toString().takeIf { it != "0" }
                            ?: lastSeasonInfo["season_id"] ?: "0"
                        val (thaiSeason, thaiEp) = getSeasonLazy(seasonId, req.epId)
                        val content = getPlayUrl(reconstructQuery(req, response, thaiEp))
                        content?.let {
                            Log.toast("已从代理服务器获取播放地址\n如加载缓慢或黑屏，可去漫游设置中测速并设置 UPOS")
                            param.result = reconstructResponse(
                                req, response, it, isDownload, thaiSeason, thaiEp
                            )
                        }
                            ?: throw CustomServerException(mapOf("未知错误" to "请检查哔哩漫游设置中解析服务器设置。"))
                    } catch (e: CustomServerException) {
                        param.result = showPlayerError(
                            response,
                            "请求解析服务器发生错误(点此查看更多)\n${e.message}"
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
            hookBeforeMethod(
                if (instance.useNewMossFunc) "executePlayViewUnite" else "playViewUnite",
                instance.playViewUniteReqClass
            ) { param ->
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
            hookAfterMethod(
                if (instance.useNewMossFunc) "executePlayViewUnite" else "playViewUnite",
                instance.playViewUniteReqClass
            ) { param ->
                if (instance.networkExceptionClass?.isInstance(param.throwable) == true)
                    return@hookAfterMethod
                val request = param.args[0]
                val response =
                    param.result ?: "com.bapis.bilibili.app.playerunite.v1.PlayViewUniteReply"
                        .on(mClassLoader).new()
                val supplementAny = response.callMethod("getSupplement")
                val typeUrl = supplementAny?.callMethodAs<String>("getTypeUrl")

                // Only handle pgc video
                ///////////////////
                // newPlayUnite:
                // request.vod.cid, response.play_arc.cid need skip
                val requestVod = request.callMethod("getVod")
                val reqCid = requestVod?.callMethodAs<Long>("getCid")

                val responsePlayArc = response.callMethod("getPlayArc")
                val respCid = responsePlayArc?.callMethodAs<Long>("getCid")

                val isThai = reqCid != 0.toLong() && reqCid != respCid
                // if it is download request
                // can't skip
                if (!isDownload && param.result != null && typeUrl != PGC_ANY_MODEL_TYPE_URL && !isThai
                ) {
                    return@hookAfterMethod
                }

                val extraContent = request.callMethodAs<Map<String, String>>("getExtraContentMap")
                val seasonId = extraContent.getOrDefault("season_id", "0")
                val reqEpId = extraContent.getOrDefault("ep_id", "0").toLong()
                if (!isDownload && seasonId == "0" && reqEpId == 0L)
                    return@hookAfterMethod
                val supplement = supplementAny?.callMethod("getValue")
                    ?.callMethodAs<ByteArray>("toByteArray")
                    ?.runCatchingOrNull { PlayViewReply.parseFrom(this) } ?: playViewReply {}
                if (needProxyUnite(response, supplement) || isThai) {
                    try {
                        val serializedRequest = request.callMethodAs<ByteArray>("toByteArray")
                        val req = PlayViewUniteReq.parseFrom(serializedRequest)
                        val (thaiSeason, thaiEp) = getSeasonLazy(seasonId, reqEpId)
                        val content = getPlayUrl(reconstructQueryUnite(req, supplement, thaiEp))
                        content?.let {
                            Log.toast("已从代理服务器获取播放地址\n如加载缓慢或黑屏，可去漫游设置中测速并设置 UPOS")
                            param.result = reconstructResponseUnite(
                                req, response, supplement, it, isDownload, thaiSeason, thaiEp
                            )
                        }
                            ?: throw CustomServerException(mapOf("未知错误" to "请检查哔哩漫游设置中解析服务器设置。"))
                    } catch (e: CustomServerException) {
                        param.result = showPlayerErrorUnite(
                            response, supplement,
                            "请求解析服务器发生错误", e.message, true
                        )
                        Log.toast("请求解析服务器发生错误: ${e.message}", alsoLog = true)
                    }
                } else if (isDownload) {
                    param.result = fixDownloadProtoUnite(response)
                } else if (blockBangumiPageAds) {
                    param.result = purifyViewInfo(response, supplement)
                }
            }
            // 7.41.0+ use async
            hookBeforeMethod(
                "playViewUnite",
                instance.playViewUniteReqClass,
                instance.mossResponseHandlerClass
            ) { param ->
                param.args[0].callMethod("getVod")?.apply {
                    isDownload = sPrefs.getBoolean("allow_download", false)
                            && callMethodAs<Int>("getDownload") >= 1
                    if (isDownload) {
                        if (!sPrefs.getBoolean("fix_download", false)
                            || callMethodAs<Int>("getFnval") <= 1
                        ) {
                            callMethod("setFnval", MAX_FNVAL)
                            callMethod("setFourk", true)
                        }
                        callMethod("setDownload", 0)
                    } else if (halfScreenQuality != 0 || fullScreenQuality != 0) {
                        // unlock available quality limit, allow quality up to 8K
                        callMethod("setFnval", MAX_FNVAL)
                        callMethod("setFourk", true)
                        if (halfScreenQuality != 0 && qnApplied.compareAndSet(false, true)) {
                            if (halfScreenQuality != 1) {
                                callMethod("setQn", halfScreenQuality)
                            } else {
                                // follow full screen quality
                                defaultQn?.let { callMethod("setQn", it) }
                            }
                        }
                    }
                }
                param.args[1] = param.args[1].mossResponseHandlerReplaceProxy { originalResp ->
                    val request = param.args[0]
                    val response =
                        originalResp ?: "com.bapis.bilibili.app.playerunite.v1.PlayViewUniteReply"
                            .on(mClassLoader).new()
                    val supplementAny = response.callMethod("getSupplement")
                    val typeUrl = supplementAny?.callMethodAs<String>("getTypeUrl")

                    // Only handle pgc video
                    ///////////////////
                    // newPlayUnite:
                    // request.vod.cid, response.play_arc.cid
                    val requestVod = request.callMethod("getVod")
                    val reqCid = requestVod?.callMethodAs<Long>("getCid")

                    val responsePlayArc = response.callMethod("getPlayArc")
                    val respCid = responsePlayArc?.callMethodAs<Long>("getCid")

                    val isThai = reqCid != 0.toLong() && reqCid != respCid
                    if (originalResp != null && typeUrl != PGC_ANY_MODEL_TYPE_URL && !isThai) {
                        return@mossResponseHandlerReplaceProxy null
                    }

                    val extraContent =
                        request.callMethodAs<Map<String, String>>("getExtraContentMap")
                    val seasonId = extraContent.getOrDefault("season_id", "0")
                    val reqEpId = extraContent.getOrDefault("ep_id", "0").toLong()
                    if (seasonId == "0" && reqEpId == 0L)
                        return@mossResponseHandlerReplaceProxy null
                    val supplement = supplementAny?.callMethod("getValue")
                        ?.callMethodAs<ByteArray>("toByteArray")
                        ?.runCatchingOrNull { PlayViewReply.parseFrom(this) } ?: playViewReply {}
                    val newResponse = if (needProxyUnite(response, supplement) || isThai) {
                        try {
                            val serializedRequest = request.callMethodAs<ByteArray>("toByteArray")
                            val req = PlayViewUniteReq.parseFrom(serializedRequest)
                            val (thaiSeason, thaiEp) = getSeasonLazy(seasonId, reqEpId)
                            val content = getPlayUrl(reconstructQueryUnite(req, supplement, thaiEp))
                            content?.let {
                                Log.toast("已从代理服务器获取播放地址\n如加载缓慢或黑屏，可去漫游设置中测速并设置 UPOS")
                                reconstructResponseUnite(
                                    req, response, supplement, it, isDownload, thaiSeason, thaiEp
                                )
                            }
                                ?: throw CustomServerException(mapOf("未知错误" to "请检查哔哩漫游设置中解析服务器设置。"))
                        } catch (e: CustomServerException) {
                            Log.toast("请求解析服务器发生错误: ${e.message}", alsoLog = true)
                            showPlayerErrorUnite(
                                response, supplement, "请求解析服务器发生错误", e.message
                            )
                        }
                    } else if (isDownload) {
                        fixDownloadProtoUnite(response)
                    } else if (blockBangumiPageAds) {
                        purifyViewInfo(response, supplement)
                    } else null
                    newResponse
                }
            }
        }
        instance.playURLMossClass?.hookBeforeMethod(
            if (instance.useNewMossFunc) "executePlayView" else "playView",
            instance.playViewReqClass
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

    private fun getSeasonLazy(
        seasonId: String, reqEpId: Long
    ): Pair<Lazy<JSONObject>, Lazy<JSONObject>> {
        val season = lazy {
            getSeason(mapOf("season_id" to seasonId, "ep_id" to reqEpId.toString()), null)
                ?.toJSONObject()?.optJSONObject("result")
                ?: throw CustomServerException(mapOf("解析服务器错误" to "无法获取剧集信息"))
        }
        val ep = lazy {
            season.value.let { s ->
                s.optJSONArray("modules").orEmpty().asSequence<JSONObject>().flatMap {
                    it.optJSONObject("data")?.optJSONArray("episodes")
                        .orEmpty().asSequence<JSONObject>()
                }.let { es ->
                    es.firstOrNull { if (reqEpId != 0L) it.optLong("id") == reqEpId else true }
                } ?: s.optJSONObject("new_ep")?.apply { put("status", 2L) }
            } ?: throw CustomServerException(mapOf("解析服务器错误" to "无法获取剧集信息"))
        }
        return season to ep
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
        return viewInfo?.callMethod("getEndPage")?.callMethod("getDialog")
            ?.callMethodAs<String>("getType")?.let { it != "" } == true
    }

    private fun needProxyUnite(response: Any, supplement: PlayViewReply): Boolean {
        if (!response.callMethodAs<Boolean>("hasVodInfo")) return true

        val viewInfo = supplement.viewInfo
        if (viewInfo.dialog.type == "area_limit") return true
        if (viewInfo.endPage.dialog.type == "area_limit") return true

        sPrefs.getString("cn_server_accessKey", null) ?: return false
        if (supplement.business.isPreview) return true
        if (viewInfo.dialog.type.isNotEmpty()) return true
        return viewInfo.endPage.dialog.type.isNotEmpty()
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

    private fun PlaysharedViewInfo.toErrorReply(message: String, subMessage: String) = copy {
        val startPlayingDialog = dialogMap["start_playing"] ?: playsharedDialog {}
        dialogMap.put("start_playing", startPlayingDialog.copy {
            backgroundInfo = backgroundInfo.copy {
                drawableBitmapUrl =
                    "http://i0.hdslb.com/bfs/bangumi/e42bfa7427456c03562a64ac747be55203e24993.png"
                effects = 2 // Effects::HALF_ALPHA
            }
            title = title.copy {
                text = message
                if (!hasTextColor()) textColor = "#ffffff"
            }
            subtitle = subtitle.copy {
                text = subMessage
                if (!hasTextColor()) textColor = "#ffffff"
            }
            // use GuideStyle::VERTICAL_TEXT, for HORIZONTAL_IMAGE cannot show error details
            styleType = 2
            limitActionType = 1 // SHOW_LIMIT_DIALOG
        })
    }

    private fun showPlayerError(response: Any, message: String) = runCatchingOrNull {
        val serializedResponse = response.callMethodAs<ByteArray>("toByteArray")
        val newRes = PlayViewReply.parseFrom(serializedResponse).toErrorReply(message)
        response.javaClass.callStaticMethod("parseFrom", newRes.toByteArray())
    } ?: response

    private fun showPlayerErrorUnite(
        response: Any,
        supplement: PlayViewReply,
        message: String,
        subMessage: String,
        isBlockingReq: Boolean = false
    ) =
        runCatchingOrNull {
            val serializedResponse = response.callMethodAs<ByteArray>("toByteArray")
            val newRes = PlayViewUniteReply.parseFrom(serializedResponse).copy {
                this.supplement = any {
                    val supplementMessage = if (isBlockingReq) {
                        message
                    } else {
                        message + "\n" + subMessage
                    }
                    typeUrl = PGC_ANY_MODEL_TYPE_URL
                    value = supplement.toErrorReply(supplementMessage).toByteString()
                }
                viewInfo = viewInfo.toErrorReply(message, subMessage)
                clearVodInfo()
            }.toByteArray()
            response.javaClass.callStaticMethod("parseFrom", newRes)
        } ?: response

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

    private fun needForceProxy(response: Any): Boolean {
        sPrefs.getString("cn_server_accessKey", null) ?: return false
        val serializedResponse = response.callMethodAs<ByteArray>("toByteArray")
        return PlayViewReply.parseFrom(serializedResponse).business.isPreview
    }

    private fun reconstructQuery(
        req: PlayViewReq,
        response: Any,
        thaiEp: Lazy<JSONObject>
    ): String? {
        val episodeInfo by lazy {
            response.callMethodOrNull("getBusiness")?.callMethodOrNull("getEpisodeInfo")
        }
        // CANNOT use reflection for compatibility with Xpatch
        return Uri.Builder().run {
            appendQueryParameter("ep_id", req.epId.let {
                if (it != 0L) it else episodeInfo?.callMethodOrNullAs<Int>("getEpId") ?: 0
            }.let {
                if (it != 0) it else thaiEp.value.optLong("id")
            }.toString())
            appendQueryParameter("cid", req.cid.let {
                if (it != 0L) it else episodeInfo?.callMethodOrNullAs<Long>("getCid") ?: 0
            }.let {
                if (it != 0L) it else thaiEp.value.optLong("id")
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
        thaiEp: Lazy<JSONObject>
    ): String? {
        val episodeInfo = supplement.business.episodeInfo
        // CANNOT use reflection for compatibility with Xpatch
        return Uri.Builder().run {
            appendQueryParameter("ep_id", req.extraContentMap["ep_id"].let {
                if (!it.isNullOrEmpty() && it != "0") it.toLong() else episodeInfo.epId
            }.let {
                if (it != 0) it else thaiEp.value.optLong("id")
            }.toString())
            appendQueryParameter("cid", req.vod.cid.let {
                if (it != 0L) it else episodeInfo.cid
            }.let {
                if (it != 0L) it else thaiEp.value.optLong("id")
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
        thaiSeason: Lazy<JSONObject>,
        thaiEp: Lazy<JSONObject>
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
            fixBusinessProto(thaiSeason, thaiEp, jsonContent)
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
        thaiSeason: Lazy<JSONObject>,
        thaiEp: Lazy<JSONObject>
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
                fixBusinessProto(thaiSeason, thaiEp, jsonContent)
                viewInfo = viewInfo {}
            }
            this.supplement = any {
                typeUrl = PGC_ANY_MODEL_TYPE_URL
                value = newSupplement.toByteString()
            }
            playArcConf = playArcConf {
                val supportedConf = arcConf { isSupport = true }
                supportedPlayArcIndices.forEach { arcConf[it] = supportedConf }
            }
            if (!hasPlayArc()) {
                playArc = playArc {
                    val episode = thaiEp.value
                    aid = episode.optLong("aid")
                    cid = episode.optLong("cid")
                    videoType = BizType.BIZ_TYPE_PGC
                    episode.optJSONObject("dimension")?.run {
                        dimension = dimension {
                            width = optLong("width")
                            height = optLong("height")
                            rotate = optLong("rotate")
                        }
                    }
                }
            }
        }.toByteArray()
        response.javaClass.callStaticMethod("parseFrom", newRes)
    }.onFailure { Log.e(it) }.getOrDefault(response)

    private fun PlayViewReplyKt.Dsl.fixBusinessProto(
        thaiSeason: Lazy<JSONObject>,
        thaiEp: Lazy<JSONObject>,
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
                val episode = thaiEp.value
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
        reconstructVideoInfoUpos(isDownload)
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

    private fun VideoInfoKt.Dsl.reconstructVideoInfoUpos(isDownload: Boolean = false) {
        if (!isDownload || !enableUposReplace) return
        val newStreamList = streamList.map { stream ->
            stream.copy { reconstructStreamUpos() }
        }
        val newDashAudio = dashAudio.map { dashItem ->
            dashItem.copy { reconstructDashItemUpos() }
        }
        streamList.clear()
        dashAudio.clear()
        streamList.addAll(newStreamList)
        dashAudio.addAll(newDashAudio)
    }

    private fun StreamKt.Dsl.reconstructStreamUpos() {
        if (hasDashVideo()) {
            dashVideo = dashVideo.copy {
                if (!hasBaseUrl()) return@copy
                val (newBaseUrl, newBackupUrl) = reconstructVideoInfoUpos(baseUrl, backupUrl)
                baseUrl = newBaseUrl
                backupUrl.clear()
                backupUrl.addAll(newBackupUrl)
            }
        } else if (hasSegmentVideo()) {
            segmentVideo = segmentVideo.copy {
                val newSegment = segment.map { responseUrl ->
                    responseUrl.copy {
                        val (newUrl, newBackupUrl) = reconstructVideoInfoUpos(url, backupUrl)
                        url = newUrl
                        backupUrl.clear()
                        backupUrl.addAll(newBackupUrl)
                    }
                }
                segment.clear()
                segment.addAll(newSegment)
            }
        }
    }

    private fun DashItemKt.Dsl.reconstructDashItemUpos() {
        if (!hasBaseUrl()) return
        val (newBaseUrl, newBackupUrl) = reconstructVideoInfoUpos(baseUrl, backupUrl)
        baseUrl = newBaseUrl
        backupUrl.clear()
        backupUrl.addAll(newBackupUrl)
    }

    private fun reconstructVideoInfoUpos(
        baseUrl: String, backupUrls: List<String>
    ): Pair<String, List<String>> {
        val filteredBackupUrls = backupUrls.filter { !it.isPCdnUpos() }
        val rawUrl = filteredBackupUrls.firstOrNull() ?: baseUrl
        return if (baseUrl.isPCdnUpos()) {
            if (filteredBackupUrls.isNotEmpty()) {
                rawUrl.replaceUpos() to listOf(rawUrl.replaceUpos(videoUposBackups[0]), baseUrl)
            } else baseUrl to backupUrls
        } else {
            baseUrl.replaceUpos() to listOf(
                rawUrl.replaceUpos(videoUposBackups[0]),
                rawUrl.replaceUpos(videoUposBackups[1])
            )
        }
    }
}
