package me.iacn.biliroaming.hook

import android.net.Uri
import me.iacn.biliroaming.BiliBiliPackage
import me.iacn.biliroaming.utils.*
import java.io.InputStream
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream


class DanmakuHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    var aid: Int = 0
    var cid: Int = 0
    var desc: String = ""
    var pageIndex = 0
    var duration: Long = 0
    var segmentIndex: Long = 0
    var seasonId: String = ""
    var episodeId: String = ""

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
            }
        "com.bapis.bilibili.app.archive.v1.Page".findClass(mClassLoader).hookAfterMethod(
            "getCid"
        ) { methodHookParam ->
            pageIndex = methodHookParam.thisObject.callMethod("getPage") as Int
        }
        BiliBiliPackage.instance.retrofitResponseClass?.hookAfterAllConstructors { methodHookParam ->
            val url = getUrl(methodHookParam.args[0])
            url?.let { Regex("season_id=([^0]\\d*)").find(it)?.groups?.get(1)?.value }?.let {
                seasonId = it
            }
            url?.let { Regex("ep_id=([^0]\\d*)").find(it)?.groups?.get(1)?.value }?.let {
                episodeId = it
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
                    DexHelper(//com.bilibili.common.chronoscommon.plugins.f$a.a(okhttp3.Response) in version 6.89
                        mClassLoader.findDexClassLoader(BiliBiliPackage.Companion::findRealClassloader)
                            ?: return
                    ).let { dexHelper ->
                        dexHelper.findMethodUsingString(
                            "Resp body compressed without known codec in header",
                            false,
                            -1,
                            -1,
                            null,
                            -1,
                            null,
                            null,
                            null,
                            false
                        ).asSequence().map {
                            dexHelper.decodeMethodIndex(it)
                        }
                            .find { it?.declaringClass?.declaringClass != null }
                            ?.declaringClass?.declaringClass?.declaredMethods?.find {
                                it.returnType.name == "[B"
                            }
                    } as Method
                grpcDecodeResponseMethod.hookAfterMethod { methodHookParam ->
                    methodHookParam.result =
                        injectResponseBytes(methodHookParam.result as ByteArray)
                }
            }
        } else {
            mClassLoader.loadClass("com.bapis.bilibili.community.service.dm.v1.DmSegMobileReq")
                .hookAfterMethod("setSegmentIndex", "long") { methodHookParam ->
                    segmentIndex = methodHookParam.args[0] as Long
                }
            mClassLoader.loadClass("com.bapis.bilibili.community.service.dm.v1.DmSegMobileReply")
                .getDeclaredMethod("getElemsList")
                .hookBeforeMethod(
                ) { methodHookParam ->
                    Log.d("DanmakuHook: call " + methodHookParam.method + " aid:$aid,cid:$cid,pageIndex:$pageIndex,season:$seasonId,episodeId:$episodeId")
                    addDanmaku(methodHookParam.thisObject)
                }
        }
    }

    private fun getUrl(response: Any): String? {
        val requestField = BiliBiliPackage.instance.requestField() ?: return null
        val urlField = BiliBiliPackage.instance.urlField() ?: return null
        val request = response.getObjectField(requestField)
        return request?.getObjectField(urlField)?.toString()
    }

    fun addDanmaku(dmSegmentMobileReply: Any) {
        if (seasonId != "" || episodeId != "") {
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
                pageIndex
            )
        }
    }

    fun addDescDanmaku(
        dmSegmentMobileReply: Any,
        segmentIndex: Long,
        desc: String,
        page: Int,
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

    fun parseProtobufResponse(urlString: String): InputStream? {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty(
            "Accept-Encoding",
            "${if (BiliBiliPackage.instance.brotliInputStreamClass != null) "br," else ""}gzip,deflate"
        )
        connection.connect()
        return if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val inputStream = connection.inputStream
            val result = when (connection.contentEncoding?.lowercase()) {
                "gzip" -> GZIPInputStream(inputStream)
                "br" -> BiliBiliPackage.instance.brotliInputStreamClass!!.new(inputStream) as InputStream
                "deflate" -> InflaterInputStream(inputStream)
                else -> inputStream
            }
            result
        } else {
            null
        }
    }

    fun extendProtobufResponse(urlString: String, dmSegmentMobileReply: Any) {
        val result = parseProtobufResponse(urlString) ?: return
        val outsideDanmaku =
            dmSegmentMobileReply.javaClass.callStaticMethod("parseFrom", result)
        if (outsideDanmaku != null) {
            dmSegmentMobileReply.callMethod(
                "addAllElems",
                outsideDanmaku.getObjectField("elems_")
            )
        }
    }
}