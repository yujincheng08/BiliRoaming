package me.iacn.biliroaming.hook

import android.net.Uri
import me.iacn.biliroaming.utils.Log
import com.google.protobuf.CodedInputStream
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import me.iacn.biliroaming.BiliBiliPackage
import me.iacn.biliroaming.Constant.TAG
import me.iacn.biliroaming.hook.BangumiSeasonHook.Companion.lastSeasonInfo
import me.iacn.biliroaming.network.BiliRoamingApi
import me.iacn.biliroaming.network.BiliRoamingApi.getContent
import me.iacn.biliroaming.utils.*
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream


class DanmakuHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    var aid: Int = 0
    var cid: Int = 0
    var desc: String = ""
    var pages = mutableListOf<Int>()
    var segmentIndex: Long = 0
    var seasonId: String = ""
    var episodeId: String = ""


    override fun startHook() {
        if (!sPrefs.getBoolean("load_outside_danmaku", false)) {
            return
        }
        val viewMossClass = mClassLoader.loadClass("com.bapis.bilibili.app.view.v1.ViewMoss")

        viewMossClass.hookAfterMethod(
            "view",
            "com.bapis.bilibili.app.view.v1.ViewReq"
        ) { methodHookParam ->

            pages.clear()
            methodHookParam.result.callMethodAs<List<*>?>("getPagesList")?.forEach {
                if (it != null) {
                    it.callMethod("getPage")
                        ?.let { it1 -> pages.add(it1.callMethodAs<Int>("getCid")) }
                }
            }
            aid = methodHookParam.result.callMethod("getArc")
                ?.callMethodAs<Int>("getAid") ?: 0
            desc = methodHookParam.result.callMethod("getArc")
                ?.callMethodAs<String>("getDesc") ?: ""
            seasonId = ""
            episodeId = ""
        }
        viewMossClass.hookAfterMethod(
            "viewProgress",
            "com.bapis.bilibili.app.view.v1.ViewProgressReq"
        ) { methodHookParam ->
            aid = methodHookParam.args[0].callMethodAs("getAid")
            cid = methodHookParam.args[0].callMethodAs("getCid")

        }
        mClassLoader.loadClass("com.bapis.bilibili.community.service.dm.v1.DmSegMobileReq")
            .hookAfterMethod("setSegmentIndex", "long") { methodHookParam ->
                segmentIndex = methodHookParam.args[0] as Long
            }
        BiliBiliPackage.instance.retrofitResponseClass?.hookAfterAllConstructors { param ->

            val url = getUrl(param.args[0])
            if (url?.startsWith("https://api.bilibili.com/pgc/view/app/season") == true) {
                seasonId = Regex("season_id=(\\d+)").find(url)?.groups?.get(1)?.value ?: ""
                episodeId = if (seasonId == "") {
                    Regex("ep_id=(\\d+)").find(url)?.groups?.get(1)?.value ?: ""
                } else {
                    ""
                }
            }
        }
        mClassLoader.loadClass("com.bapis.bilibili.community.service.dm.v1.DmSegMobileReply")
            .getDeclaredMethod("getElemsList")
            .hookBeforeMethod(
            ) { methodHookParam ->
                Log.d("DanmakuHook: call " + methodHookParam.method + " aid:$aid,cid:$cid,pages:$pages,season:$seasonId,episodeId:$episodeId")

                if (seasonId != "" || episodeId != "") {
                    addSeasonDanmaku(
                        methodHookParam.thisObject,
                        segmentIndex,
                        seasonId = seasonId,
                        episodeId = episodeId,
                        aid = aid
                    )
                } else {
                    var i = 0
                    while (i < pages.size) {
                        if (pages[i] == cid) {
                            addOutsideDescDanmaku(
                                methodHookParam.thisObject,
                                segmentIndex,
                                desc,
                                i
                            )
                            return@hookBeforeMethod
                        }
                        i += 1
                    }
                }
            }
    }


    private fun getUrl(response: Any): String? {
        val requestField = BiliBiliPackage.instance.requestField() ?: return null
        val urlField = BiliBiliPackage.instance.urlField() ?: return null
        val request = response.getObjectField(requestField)
        return request?.getObjectField(urlField)?.toString()
    }

    fun addOutsideDescDanmaku(
        dmSegmentMobileReply: Any,
        segmentIndex: Long,
        desc: String = "",
        page: Int = -1,
    ) {
        val nicoReg = Regex("sm\\d+")
        val nicoGroups = nicoReg.findAll(desc).toList()
        if (nicoGroups.isNotEmpty()) {
            val builder = buildCustomUrl("/protobuf/desc")
            builder.appendQueryParameter("nicoid", nicoGroups[page].value)
            builder.appendQueryParameter("segmentIndex", segmentIndex.toString())
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
                extendProtobufResponse(builder.toString(), dmSegmentMobileReply)
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

    fun extendProtobufResponse(urlString: String, dmSegmentMobileReply: Any) {
        val url = URL(urlString)
        Log.d("DanmakuHook: query:$urlString")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty(
            "Accept-Encoding",
            "${if (BiliBiliPackage.instance.brotliInputStreamClass != null) "br," else ""}gzip,deflate"
        )
        connection.connect()
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val inputStream = connection.inputStream
            val result = when (connection.contentEncoding?.lowercase()) {
                "gzip" -> GZIPInputStream(inputStream)
                "br" -> BiliBiliPackage.instance.brotliInputStreamClass!!.new(inputStream) as InputStream
                "deflate" -> InflaterInputStream(inputStream)
                else -> inputStream
            }
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
}