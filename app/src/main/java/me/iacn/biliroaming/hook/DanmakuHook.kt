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

    override fun startHook() {
        if (!sPrefs.getBoolean("load_outside_danmaku", false)) {
            return
        }
        val versionCode = getVersionCode(packageName)

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
        "com.bapis.bilibili.community.service.dm.v1.DMMoss".findClass(mClassLoader)
            .hookAfterAllMethods(
                "dmSegMobile"
            ) { methodHookParam ->
                segmentIndex = methodHookParam.args[0].callMethod("getSegmentIndex") as Long
            }
        mClassLoader.loadClass("com.bapis.bilibili.community.service.dm.v1.DmSegMobileReq")
            .hookAfterMethod("setSegmentIndex", "long") { methodHookParam ->
                segmentIndex = methodHookParam.args[0] as Long
            }
        if (versionCode > 6520000) {

            var grpcDecodeResponseMethod =
                // byte[] tv.danmaku.chronos.wrapper.ChronosGrpcClient$a.e(Response response) in version 6.56
                "tv.danmaku.chronos.wrapper.ChronosGrpcClient\$a".findClassOrNull(mClassLoader)?.declaredMethods
                    ?.find { method -> method.parameterTypes.size == 1 && method.parameterTypes[0].name == "okhttp3.Response" }
            if (grpcDecodeResponseMethod != null) {
                grpcDecodeResponseMethod.hookAfterMethod { methodHookParam ->
                    if (methodHookParam.args[0].getObjectField("a")?.toString()
                            ?.contains("community.service.dm.v1.DM/DmSegMobile") == true
                    ) {
                        injectResponseBytes(methodHookParam.result as ByteArray)?.let {
                            methodHookParam.result = it
                        }
                    }
                }
            } else {
                grpcDecodeResponseMethod =
                    mClassLoader.loadClass("com.bilibili.common.chronoscommon.plugins.f").declaredMethods.find {
                        it.returnType == ByteArray::class.java
                    }
                grpcDecodeResponseMethod?.hookAfterMethod { methodHookParam ->
                    methodHookParam.result =
                        injectResponseBytes(methodHookParam.result as ByteArray)
                }
            }
        } else {
            mClassLoader.loadClass("com.bapis.bilibili.community.service.dm.v1.DmSegMobileReply")
                .getDeclaredMethod("getElemsList")
                .hookBeforeMethod(
                ) { methodHookParam ->
                    Log.d("DanmakuHook: call " + methodHookParam.method + " aid:$aid,cid:$cid,pageIndex:$pageIndex,season:$seasonId,episodeId:$episodeId")
                    addDanmaku(methodHookParam.thisObject)
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
            addDescDanmaku(
                dmSegmentMobileReply,
                segmentIndex,
                desc,
                pageIndex,
                duration
            )
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
        val episodeDuration = episodesDict[aid]?.get(1)?.toInt()
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
                if (it > duration) return else {
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
            danmaku.callMethod("setContent", comment.optString("m"))
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

    fun injectResponseBytes(responseBody: ByteArray): ByteArray? {
        val dmSegment =
            mClassLoader.loadClass("com.bapis.bilibili.community.service.dm.v1.DmSegMobileReply")
                .callStaticMethod(
                    "parseFrom",
                    responseBody
                )
        return if (dmSegment != null) {
            addDanmaku(dmSegment)
            dmSegment.callMethod("toByteArray") as ByteArray
        } else {
            null
        }
    }

    fun requestWithNewThread(
        urlString: String,
        method: String = "GET",
        contentType: String? = null,
        requestBody: ByteArray? = null
    ): ByteArray? {
        var finalResult: ByteArray? = null
        thread {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = method
            contentType?.let { connection.setRequestProperty("Content-Type", contentType) }
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty(
                "Accept-Encoding",
                "${if (BiliBiliPackage.instance.brotliInputStreamClass != null) "br," else ""}gzip,deflate"
            )
            connection.connect()
            if (requestBody != null) connection.outputStream.write(requestBody)
            finalResult = if (connection.responseCode == HttpURLConnection.HTTP_OK) {
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
        return finalResult
    }

    fun extendProtobufResponse(urlString: String, dmSegmentMobileReply: Any) {
        val result = requestWithNewThread(urlString)
        dmSegmentMobileReply.javaClass.callStaticMethod("parseFrom", result)?.let {
            if (dandanDanmakuPool.size == 0) {
                dmSegmentMobileReply.callMethod(
                    "addAllElems",
                    it.getObjectField("elems_")
                )
            } else {
                (it.getObjectField("elems_") as List<*>).forEach { danmakuElem ->
                    if (danmakuElem != null
                        && !dandanDanmakuPool.contains(
                            Pair(
                                (danmakuElem.callMethod("getProgress") as Int),
                                (danmakuElem.callMethod("getContent") as String)
                            )
                        )
                    ) {
                        dmSegmentMobileReply.callMethod("addElems", danmakuElem)
                    }
                }
            }
        }
    }
}