package me.iacn.biliroaming.hook

import android.net.Uri
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.BuildConfig
import me.iacn.biliroaming.utils.*
import org.json.JSONArray
import org.json.JSONObject

class LiveQualityHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    companion object {
        private const val TAG = "LiveQualityHook"
    }

    @Volatile
    private var newQn: String = ""

    private fun debug(msg: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d("[$TAG] - ${msg()}")
        }
    }

    override fun startHook() {
        val liveQuality = sPrefs.getString("live_quality", "0")?.toIntOrNull() ?: 0
        if (liveQuality <= 0) {
            return
        }

        val canSwitchLiveRoom = !sPrefs.getBoolean("forbid_switch_live_room", false)

        instance.defaultRequestInterceptClass?.hookBeforeAllMethods(instance.interceptMethod()) { param ->
            val request = param.args[0]
            val httpUrl = request.getObjectField(instance.urlField())
            val url = httpUrl.toString()
            if (!url.startsWith("https://api.live.bilibili.com/xlive/app-room/v2/index/getRoomPlayInfo?")) {
                return@hookBeforeAllMethods
            }

            debug { "oldHttpUrl: $url" }

            val uri = Uri.parse(httpUrl.toString())
            val qn = uri.getQueryParameter("qn")
            if (qn.isNullOrEmpty() || qn == "0") {
                val builder = uri.buildUpon().clearQuery()
                for (name in uri.queryParameterNames) {
                    if (name == "qn") {
                        builder.appendQueryParameter(name, newQn.ifEmpty { liveQuality.toString() })
                    } else {
                        builder.appendQueryParameter(name, uri.getQueryParameter(name))
                    }
                }
                val newHttpUrl = instance.httpUrlClass?.callStaticMethod(
                    instance.httpUrlParseMethod(),
                    builder.build().toString()
                )
                debug { "newHttpUrl: $newHttpUrl" }
                request.setObjectField(instance.urlField(), newHttpUrl)
            }
        }

        instance.retrofitResponseClass?.hookBeforeAllConstructors { param ->
            val url = getRetrofitUrl(param.args[0]) ?: return@hookBeforeAllConstructors
            val body = param.args[1] ?: return@hookBeforeAllConstructors

            when {
                instance.generalResponseClass?.isInstance(body) != true -> Unit
                // 处理上下滑动切换直播间
                url.startsWith("https://api.live.bilibili.com/xlive/app-interface/v2/room/recList?") && canSwitchLiveRoom -> {
                    val data = body.getObjectField("data") ?: return@hookBeforeAllConstructors
                    val info = JSONObject(
                        instance.fastJsonClass?.callStaticMethod("toJSONString", data).toString()
                    )
                    if (fixLiveRoomFeedInfo(info, liveQuality)) {
                        body.setObjectField(
                            "data",
                            instance.fastJsonClass?.callStaticMethod(
                                instance.fastJsonParse(),
                                info.toString(),
                                data.javaClass
                            )
                        )
                    }
                }

                BuildConfig.DEBUG && url.startsWith("https://api.live.bilibili.com/xlive/app-room/v2/index/getRoomPlayInfo?") -> {
                    val data = body.getObjectField("data") ?: return@hookBeforeAllConstructors
                    val info = JSONObject(
                        instance.fastJsonClass?.callStaticMethod("toJSONString", data).toString()
                    )
                    printCodec(info)
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
            instance.buildSelectorDataMethod(),
            Uri::class.java
        ) { param ->
            val originalUri = param.args[0] as Uri
            if (!originalUri.isLive()) {
                return@hookBeforeMethod
            }

            debug { "originalLiveUrl: $originalUri" }

            newQn = findQuality(
                JSONArray(originalUri.getQueryParameter("accept_quality")),
                liveQuality
            ).toString()
            debug { "newQn: $newQn" }

            param.args[0] = originalUri.removeQuery { name ->
                name.startsWith("playurl")
            }.also {
                debug { "newLiveUrl: $it" }
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

    private fun Uri.removeQuery(predicate: (String) -> Boolean): Uri {
        val newBuilder = buildUpon().clearQuery()
        for (name in queryParameterNames) {
            val value = getQueryParameter(name) ?: ""
            if (!predicate(name)) {
                newBuilder.appendQueryParameter(name, value)
            }
        }
        return newBuilder.build()
    }

    private fun fixLiveRoomFeedInfo(info: JSONObject, expectQuality: Int): Boolean {
        val feedList = info.optJSONArray("list") ?: return false
        feedList.iterator().forEach { feedData ->
            debug { "oldFeedData: ${feedData.toString(2)}" }
            val newQuality = findQuality(feedData.getJSONArray("accept_quality"), expectQuality)
            feedData.apply {
                put("current_qn", newQuality)
                put("current_quality", newQuality)
                put("play_url", "")
                put("play_url_h265", "")
                put("playurl_infos", JSONArray())
            }
            debug { "newFeedData: ${feedData.toString(2)}" }
        }
        return true
    }

    private fun printCodec(info: JSONObject) {
        val playUrlInfo = info.optJSONObject("playurl_info") ?: return
        val playUrlObj = playUrlInfo.getJSONObject("playurl")
        debug { "printCodec >>>>>>>>>>>>>>" }
        playUrlObj.getJSONArray("stream").iterator().forEach { stream ->
            stream.getJSONArray("format").iterator().forEach { format ->
                format.getJSONArray("codec").iterator().forEach {
                    debug { "codec: ${it.toString(2)}" }
                }
            }
        }
        debug { "printCodec <<<<<<<<<<<<<<" }
    }
}