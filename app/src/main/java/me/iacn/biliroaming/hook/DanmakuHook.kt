package me.iacn.biliroaming.hook

import android.net.Uri
import me.iacn.biliroaming.BiliBiliPackage
import me.iacn.biliroaming.hook.BangumiSeasonHook.Companion.episodesDict
import me.iacn.biliroaming.utils.*
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import kotlin.concurrent.thread


class DanmakuHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    var aid: Int = 0
    var cid: Int = 0
    var desc: String = ""
    var pageIndex = 0
    var duration: Long = 0
    var segmentIndex: Long = 0
    var seasonId: String = ""
    var episodeId: String = ""

    val dandanDanmakuPool = HashSet<Pair<Int, String>>()

    var serverResponseArgv: JSONObject = JSONObject()
    var aliasCommentCursor: Any? = null
    var currentCommentIsEnd = false

    override fun startHook() {
        if (!sPrefs.getBoolean("load_outside_danmaku", false)) {
            return
        }
        videoInfoHook()
        danmakuHook()
        commentHook()
    }

    fun videoInfoHook() {
        "com.bapis.bilibili.app.archive.v1.Arc".findClass(mClassLoader)
            .hookAfterMethod("getAid") { methodHookParam ->
                desc = (methodHookParam.thisObject.callMethod("getDesc") as String)
                duration = (methodHookParam.thisObject.callMethod("getDuration") as Long)
                episodeId = ""
                seasonId = ""
                episodesDict.clear()
                dandanDanmakuPool.clear()
            }
        "com.bapis.bilibili.app.archive.v1.Page".findClass(mClassLoader).hookAfterMethod(
            "getCid"
        ) { methodHookParam ->
            pageIndex = methodHookParam.thisObject.callMethod("getPage") as Int
        }
        BiliBiliPackage.instance.retrofitResponseClass?.hookAfterAllConstructors { methodHookParam ->
            getRetrofitUrl(methodHookParam.args[0])?.let { url ->
                Regex("season_id=([^0]\\d*)").find(url)?.groups?.get(1)?.value?.let {
                    seasonId = it
                    episodeId = ""
                }
                Regex("ep_id=([^0]\\d*)").find(url)?.groups?.get(1)?.value?.let {
                    episodeId = it
                    seasonId = ""
                }
            }
        }
        mClassLoader.loadClass("com.bapis.bilibili.app.view.v1.ViewMoss").hookAfterMethod(
            "viewProgress",
            "com.bapis.bilibili.app.view.v1.ViewProgressReq"
        ) { methodHookParam ->
            aid = methodHookParam.args[0].callMethodAs("getAid")
            cid = methodHookParam.args[0].callMethodAs("getCid")
        }
    }


    fun danmakuHook() {
        mClassLoader.loadClass("com.bapis.bilibili.community.service.dm.v1.DmSegMobileReq")
            .hookAfterMethod("setSegmentIndex", "long") { methodHookParam ->
                segmentIndex = methodHookParam.args[0] as Long
            }
        "com.bapis.bilibili.community.service.dm.v1.DMMoss".findClass(mClassLoader).let {
            it.hookBeforeMethod(
                "dmSegMobile",
                "com.bapis.bilibili.community.service.dm.v1.DmSegMobileReq",
                "com.bilibili.lib.moss.api.MossResponseHandler"
            ) { methodHookParam ->
                val dmSegMobileReply: Any = methodHookParam.thisObject.callMethod(
                    "dmSegMobile",
                    methodHookParam.args[0]
                )!!
                methodHookParam.args[1].callMethod("onNext", dmSegMobileReply)
                methodHookParam.result = null
            }
            it.hookAfterMethod(
                "dmSegMobile",
                "com.bapis.bilibili.community.service.dm.v1.DmSegMobileReq"
            ) { methodHookParam ->
                Log.d("DanmakuHook: call " + methodHookParam.method.name + " aid:$aid,cid:$cid,pageIndex:$pageIndex,season:$seasonId,episodeId:$episodeId")
                segmentIndex = methodHookParam.args[0].callMethod("getSegmentIndex") as Long
                addDanmaku(methodHookParam.result)
            }
        }
    }


    fun commentHook() {
        "com.bapis.bilibili.main.community.reply.v1.ReplyMoss".findClass(mClassLoader)
            .hookBeforeMethod(
                "mainList", "com.bapis.bilibili.main.community.reply.v1.MainListReq",
                "com.bilibili.lib.moss.api.MossResponseHandler"
            ) { methodHookParam ->
                if (desc.isNotEmpty()) {

                } else if (sPrefs.getBoolean(
                        "alias_comment_switch",
                        true
                    ) && serverResponseArgv.has("aliasEpisode")
                ) {
                    Log.d("DanmakuHook: aliasComment:" + serverResponseArgv)
                    val mainListReply =
                        methodHookParam.thisObject.callMethod("mainList", methodHookParam.args[0])

                    if (currentCommentIsEnd) {
                        mainListReply?.callMethod("clearReplies")
                    }
                    currentCommentIsEnd = mainListReply?.callMethod("getCursor")
                        ?.callMethod("getIsEnd") as Boolean

                    val aliasCommentReply = methodHookParam.args[0]?.let { mainListReq ->
                        mainListReq.callMethod(
                            "setOid", serverResponseArgv.getJSONObject("aliasEpisode").optInt("aid")
                        )
                        mainListReq.callMethod("getCursor").let { cursorReq ->
                            if (cursorReq?.callMethod("getNext") != 0L) {
                                currentCommentIsEnd = false
                                if (aliasCommentCursor != null && methodHookParam.args[0].callMethod(
                                        "getCursor"
                                    )
                                        ?.callMethod("getModeValue")
                                    == aliasCommentCursor!!.callMethod("getModeValue")
                                ) {
                                    cursorReq?.callMethod(
                                        "setNext",
                                        aliasCommentCursor!!.callMethod("getNext")
                                    )
                                }
                            }
                        }

                        val extra = (mainListReq.callMethod("getExtra") as String).toJSONObject()
                        extra.put(
                            "ep_id",
                            serverResponseArgv.getJSONObject("aliasEpisode").optInt("ep_id")
                        )
                        extra.put(
                            "season_id",
                            serverResponseArgv.getJSONObject("aliasEpisode").getInt("season_id")
                        )
                        mainListReq.callMethod("setExtra", extra.toString())

                        methodHookParam.thisObject.callMethod("mainList", mainListReq)
                    }
                    mainListReply.callMethod(
                        "addAllReplies", aliasCommentReply?.callMethod("getRepliesList")
                    )

                    aliasCommentCursor = aliasCommentReply?.callMethod("getCursor")
                    if (aliasCommentCursor?.callMethod("getIsEnd") != true) {
                        mainListReply.callMethod("getCursor")?.callMethod("setIsEnd", false)
                    }

                    methodHookParam.args[1].callMethod("onNext", mainListReply)
                    methodHookParam.result = null
                }
            }
    }

    fun addDanmaku(dmSegmentMobileReply: Any) {
        if (seasonId != "" || episodeId != "") {
            if (segmentIndex == 1L) {
                if (sPrefs.getBoolean("dandanplay_danmaku_switch", true))
                    addDandanDanmaku(dmSegmentMobileReply)
            }
            if (sPrefs.getBoolean("danmaku_server_switch", false))
                addSeasonDanmaku(
                    dmSegmentMobileReply,
                    segmentIndex,
                    seasonId = seasonId,
                    episodeId = episodeId,
                    aid = aid
                )
        } else {
            if (sPrefs.getBoolean("reprint_danmaku_switch", true)) {
                addDescDanmaku(
                    dmSegmentMobileReply,
                    segmentIndex,
                    desc,
                    pageIndex,
                    duration
                )
            }
        }
    }

    fun addDescDanmaku(
        dmSegmentMobileReply: Any,
        segmentIndex: Long,
        desc: String,
        page: Int,
        duration: Long
    ) {
        val nicoGroups = Regex("sm\\d+").findAll(desc).toList()
        val twitchVod = Regex("https://www.twitch.tv/videos/(\\d+)")
            .find(desc)?.groups?.get(1)?.value
        if (nicoGroups.isNotEmpty() || twitchVod != null) {
            val builder = buildCustomUrl("/protobuf/desc")
            if (nicoGroups.isNotEmpty()) {
                builder.appendQueryParameter("nicoid", nicoGroups[page - 1].value)
            }
            if (twitchVod != null) {
                builder.appendQueryParameter("twitch_id", twitchVod)
            }
            builder.appendQueryParameter("segmentIndex", segmentIndex.toString())
            builder.appendQueryParameter("duration", duration.toString())
            appendTranslateParameter(builder)
            while (true) {
                try {
                    extendProtobufResponse(builder.toString(), dmSegmentMobileReply)
                    break
                } catch (e: SocketTimeoutException) {
                    Log.e("addOutsideDescDanmaku: $e")
                    extendProtobufResponse(builder.toString(), dmSegmentMobileReply)
                }
            }
        }
    }

    fun addSeasonDanmaku(
        dmSegmentMobileReply: Any,
        segmentIndex: Long,
        seasonId: String,
        episodeId: String,
        aid: Int
    ) {

        val builder = buildCustomUrl("/protobuf/season")
        builder.appendQueryParameter("segmentIndex", segmentIndex.toString())
        if (seasonId.isNotEmpty()) {
            builder.appendQueryParameter("ss", seasonId)
        }
        if (episodeId.isNotEmpty()) {
            builder.appendQueryParameter("episode_id", episodeId)
        }
        builder.appendQueryParameter("aid", aid.toString())
        builder.appendQueryParameter("duration", episodesDict[aid]?.get(1))
        builder.appendQueryParameter(
            "baha_danmaku_limit",
            sPrefs.getString("baha_danmaku_limit", "-1")
        )
        builder.appendQueryParameter(
            "nico_danmaku_limit",
            sPrefs.getString("nico_danmaku_limit", "1000")
        )
        if (sPrefs.getBoolean("alias_comment_switch", true)) {
            builder.appendQueryParameter("alias_comment", "1")
        }
        appendTranslateParameter(builder)
        while (true) {
            try {
                extendProtobufResponse(builder.toString(), dmSegmentMobileReply)
                break
            } catch (e: SocketTimeoutException) {
                Log.e("addSeasonDanmaku: " + e)
            }
        }
    }

    fun addDandanDanmaku(dmSegmentMobileReply: Any) {
        if (!episodesDict.containsKey(aid)) return
        dandanDanmakuPool.clear()
        val episodeDuration = episodesDict[aid]?.get(1)?.toInt() ?: return
        var episodeTitle: String = episodesDict[aid]?.get(0) ?: return
        val dictReady = if (!SubtitleHelper.dictExist) {
            SubtitleHelper.downloadDict()
        } else true
        if (dictReady) {
            episodeTitle =
                SubtitleHelper.convert("{\"body\":[{\"content\":\"${episodeTitle}}\"}]}").let {
                    it.substring(21, it.length - 5)
                }
        }
        val markOutsideDanmaku = sPrefs.getBoolean("danmaku_mark_switch", false)
        val data =
            "{\"fileName\":\"$episodeTitle\"," +
                    "\"fileHash\":\"00000000000000000000000000000000\"," +
                    "\"fileSize\":0," +
                    "\"videoDuration\":${
                        episodeDuration.toString()
                            .let { it.substring(0, it.length - 3) }
                    }," +
                    "\"matchMode\":\"hashAndFileName\"}"
        val builder = Uri.Builder().scheme("https")
            .encodedAuthority("api.dandanplay.net/api/v2/match")
        val content: JSONObject =
            requestWithNewThread(
                builder.toString(),
                "POST",
                "application/json",
                data.toByteArray()
            )
                ?.decodeToString().toJSONObject()

        val dandanEpisodeId: Int = content.optJSONArray("matches")
            ?.optJSONObject(0)?.optInt("episodeId") ?: return
        val commentList: JSONObject =
            requestWithNewThread("https://api.dandanplay.net/api/v2/comment/$dandanEpisodeId?withRelated=false")
                ?.decodeToString().toJSONObject()
        val danmakuElemClass =
            "com.bapis.bilibili.community.service.dm.v1.DanmakuElem"
                .findClassOrNull(mClassLoader) ?: return
        val danmakuElems = mutableListOf<Any>()
        for (comment in commentList.optJSONArray("comments") ?: return) {
            val danmaku = danmakuElemClass.callStaticMethod("access\$000") ?: return
            val args = comment.optString("p").split(",")
            (args[0].toFloat() * 1000).toInt().let {
                if (it > episodeDuration) return else {
                    danmaku.callMethod("setProgress", it)
                }
            }
            danmaku.callMethod("setMode", args[1].toInt())
            danmaku.callMethod("setColor", args[2].toInt())
            danmaku.callMethod("setMidHash", "dandanpl")
            danmaku.callMethod("setIdStr", comment.optLong("cid").toString())
            danmaku.callMethod("setId", comment.optLong("cid"))
            danmaku.callMethod("setPool", 1)
            danmaku.callMethod("setWeight", 8)
            if (markOutsideDanmaku) {
                danmaku.callMethod("setContent", '*' + comment.optString("m"))
            }
            dandanDanmakuPool.add(Pair((args[0].toFloat() * 1000).toInt(), comment.optString("m")))
            danmakuElems.add(danmaku)
        }
        dmSegmentMobileReply.callMethod("addAllElems", danmakuElems)
    }

    fun buildCustomUrl(path: String): Uri.Builder {
        val builder = Uri.Builder()

        val domain = sPrefs.getString(
            "danmaku_server_domain",
            "http://152.32.146.234:400"
        )
        if (domain != null) {
            if (domain.startsWith("http://")) {
                builder.scheme("http")
                builder.encodedAuthority(domain.substring(7) + path)
            } else if (domain.startsWith("https://")) {
                builder.scheme("https")
                builder.encodedAuthority(domain.substring(8) + path)
            }
        }
        return builder
    }

    fun appendTranslateParameter(builder: Uri.Builder) {
        if (sPrefs.getBoolean("translate_switch", true)) {
            builder.appendQueryParameter("translate", "1")
            builder.appendQueryParameter(
                "translateThreshold",
                sPrefs.getString("translate_threshold", "7")
            )
            if (sPrefs.getBoolean("translate_replace_katakana", true)) {
                builder.appendQueryParameter("replaceKatakana", "1")
            }
        }
    }

    fun requestWithNewThread(
        urlString: String,
        method: String = "GET",
        contentType: String? = null,
        requestBody: ByteArray? = null,
        connectTimeout: Int = 10000
    ): Pair<ByteArray?, String> {
        Log.d("DanmakuHook: parse url:$urlString")
        var finalResult: ByteArray? = null
        var responseContentType = ""
        thread {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = method
            contentType?.let { connection.setRequestProperty("Content-Type", contentType) }
            connection.connectTimeout = connectTimeout
            connection.readTimeout = connectTimeout
            connection.setRequestProperty(
                "Accept-Encoding",
                "${if (BiliBiliPackage.instance.brotliInputStreamClass != null) "br," else ""}gzip,deflate"
            )
            connection.connect()
            if (requestBody != null) connection.outputStream.write(requestBody)
            finalResult = if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                responseContentType = connection.contentType
                val inputStream = connection.inputStream
                val result = when (connection.contentEncoding?.lowercase()) {
                    "gzip" -> GZIPInputStream(inputStream)
                    "br" -> BiliBiliPackage.instance.brotliInputStreamClass!!.new(inputStream) as InputStream
                    "deflate" -> InflaterInputStream(inputStream)
                    else -> inputStream
                }
                result.readBytes()
            } else {
                null
            }
        }.join()
        return finalResult to responseContentType
    }

    fun requestWithNewThread(
        urlString: String,
        method: String = "GET",
        contentType: String? = null,
        requestBody: ByteArray? = null
    ): ByteArray? {
        return requestWithNewThread(urlString, method, contentType, requestBody, 10000).first
    }

    fun extendProtobufResponse(urlString: String, dmSegmentMobileReply: Any) {
        val result = requestWithNewThread(urlString, connectTimeout = 10000)
        serverResponseArgv = if (result.second.indexOf(";") != -1) {
            result.second.let { it.substring(it.indexOf(";") + 1).toJSONObject() }
        } else JSONObject()
        val markOutsideDanmaku = sPrefs.getBoolean("danmaku_mark_switch", false)
        dmSegmentMobileReply.javaClass.callStaticMethod("parseFrom", result.first)?.let {
            if (dandanDanmakuPool.size == 0 && !markOutsideDanmaku) {
                dmSegmentMobileReply.callMethod(
                    "addAllElems",
                    it.getObjectField("elems_")
                )
            } else {
                (it.getObjectField("elems_") as List<*>).forEach { danmakuElem ->
                    if (danmakuElem != null
                        && (dandanDanmakuPool.size == 0
                                || (dandanDanmakuPool.size != 0 && !dandanDanmakuPool.contains(
                            Pair(
                                (danmakuElem.callMethod("getProgress") as Int),
                                (danmakuElem.callMethod("getContent") as String)
                            )
                        )))
                    ) {
                        if (markOutsideDanmaku) {
                            danmakuElem.callMethod(
                                "setContent",
                                "*" + danmakuElem.callMethod("getContent")
                            )
                        }
                        dmSegmentMobileReply.callMethod("addElems", danmakuElem)
                    }
                }
            }
        }
    }
}