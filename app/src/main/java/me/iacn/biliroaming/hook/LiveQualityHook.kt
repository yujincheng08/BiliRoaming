package me.iacn.biliroaming.hook

import android.net.Uri
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import org.json.JSONArray
import org.json.JSONObject

class LiveQualityHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    override fun startHook() {
        val liveQuality = sPrefs.getString("live_quality", "0")?.toIntOrNull() ?: 0
        if (liveQuality <= 0) {
            return
        }

        val canSwitchLiveRoom = !sPrefs.getBoolean("forbid_switch_live_room", false)

        instance.retrofitResponseClass?.hookBeforeAllConstructors { param ->
            val url = getRetrofitUrl(param.args[0]) ?: return@hookBeforeAllConstructors
            val body = param.args[1] ?: return@hookBeforeAllConstructors

            when {
                instance.generalResponseClass?.isInstance(body) != true -> Unit
                // 处理上下滑动切换直播间
                url.startsWith("https://api.live.bilibili.com/xlive/app-interface/v2/room/recList?") && canSwitchLiveRoom -> {
                    val data = body.getObjectField("data") ?: return@hookBeforeAllConstructors
                    val info = JSONObject(instance.fastJsonClass?.callStaticMethod("toJSONString", data).toString())
                    if (fixLiveRoomFeedInfo(info, liveQuality)) {
                        body.setObjectField(
                            "data",
                            instance.fastJsonClass?.callStaticMethod(instance.fastJsonParse(), info.toString(), data.javaClass)
                        )
                    }
                }
                url.startsWith("https://api.live.bilibili.com/xlive/app-room/v2/index/getRoomPlayInfo?") -> {
                    val uri = Uri.parse(url)
                    val reqQn = uri.getQueryParameter("qn")
                    if (!reqQn.isNullOrEmpty() && reqQn != "0") {
                        return@hookBeforeAllConstructors
                    }
                    val data = body.getObjectField("data") ?: return@hookBeforeAllConstructors
                    val info = JSONObject(instance.fastJsonClass?.callStaticMethod("toJSONString", data).toString())
                    if (fixRoomPlayInfo(info, liveQuality)) {
                        body.setObjectField(
                            "data",
                            instance.fastJsonClass?.callStaticMethod(instance.fastJsonParse(), info.toString(), data.javaClass)
                        )
                    }
                }
            }
        }

        instance.liveRTCSourceServiceImplClass?.hookBeforeAllMethods(instance.switchAutoMethod()) { param ->
            val mode = param.args[0] as? Enum<*> ?: return@hookBeforeAllMethods
            if (mode.ordinal == 2) { // AUTO
                param.result = null
            }
        }

        instance.livePlayUrlSelectUtilClass?.hookBeforeMethod(
            instance.parseUriMethod(),
            Uri::class.java
        ) { param ->
            val originalUri = param.args[0] as Uri
            if (!originalUri.isLive()) {
                return@hookBeforeMethod
            }

            val newQuality = findQuality(
                JSONArray(originalUri.getQueryParameter("accept_quality")),
                liveQuality
            ).toString()

            param.args[0] = originalUri.replaceQuery { name, oldVal ->
                when {
                    "current_qn" == name -> newQuality
                    "current_quality" == name -> newQuality
                    name.endsWith("current_qn") -> "0"
                    else -> oldVal
                }
            }
        }
    }

    private fun Uri.isLive(): Boolean {
        return scheme in arrayOf("http", "https")
                && host == "live.bilibili.com"
                && pathSegments.firstOrNull()?.all { it.isDigit() } == true
    }

    private fun findQuality(acceptQuality: JSONArray, expectQuality: Int): Int {
        val acceptQnList = acceptQuality.asSequence<Int>().sorted().toList()
        val max = acceptQnList.max()
        val min = acceptQnList.min()
        return when {
            expectQuality > max -> max
            expectQuality < min -> min
            else -> acceptQnList.first { it >= expectQuality }
        }
    }

    private fun Uri.replaceQuery(replacer: (String, String) -> String?): Uri {
        val newBuilder = buildUpon().clearQuery()
        for (name in queryParameterNames) {
            val newValue = replacer(name, getQueryParameter(name) ?: "")
            newValue?.let { newBuilder.appendQueryParameter(name, it) }
        }
        return newBuilder.build()
    }

    private fun fixLiveRoomFeedInfo(info: JSONObject, expectQuality: Int): Boolean {
        val feedList = info.optJSONArray("list") ?: return false
        feedList.iterator().forEach { feedData ->
            val newQuality = findQuality(feedData.getJSONArray("accept_quality"), expectQuality)
            feedData.put("current_qn", newQuality)
            feedData.put("current_quality", newQuality)
        }
        return true
    }

    private fun fixRoomPlayInfo(info: JSONObject, expectQuality: Int): Boolean {
        val playUrlInfo = info.optJSONObject("playurl_info") ?: return false
        val playUrlObj = playUrlInfo.getJSONObject("playurl")
        playUrlObj.getJSONArray("stream").iterator().forEach { stream ->
            stream.getJSONArray("format").iterator().forEach { format ->
                format.getJSONArray("codec").iterator().asSequence().run {
                    firstOrNull { codec -> fixCodec(codec, expectQuality, true) }
                        ?: firstOrNull { codec ->  fixCodec(codec, expectQuality, false) }
                }
            }
        }
        return true
    }

    private fun fixCodec(codec: JSONObject, expectQuality: Int, strict: Boolean): Boolean {
        val newQuality = findQuality(codec.getJSONArray("accept_qn"), expectQuality)
        if (strict && newQuality != expectQuality) {
            return false
        }
        val oldQn = codec.getInt("current_qn")
        if (oldQn != newQuality) {
            codec.put("current_qn", newQuality)
        }
        return true
    }
}