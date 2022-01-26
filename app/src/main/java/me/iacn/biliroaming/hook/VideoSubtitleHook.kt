package me.iacn.biliroaming.hook

import android.net.Uri
import me.iacn.biliroaming.BiliBiliPackage
import me.iacn.biliroaming.utils.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class VideoSubtitleHook(private val classLoader: ClassLoader) : BaseHook(classLoader) {

    override fun startHook() {
        if (!sPrefs.getBoolean("auto_generate_chs_subtitle", false)) return

        BiliBiliPackage.instance.videoSubtitleClass?.hookAfterMethod("getSubtitlesList") ret@{ param ->
            val subtitles = param.result as? List<*> ?: listOf<Any>()
            val subtitleItemClass = BiliBiliPackage.instance.subtitleItemClass ?: return@ret
            val lanCodes = subtitles.map { s -> s?.callMethod("getLan") as? String ?: "" }
            if ("zh-CN" !in lanCodes && "zh-Hant" in lanCodes) {
                val zhHant = subtitles.first { s -> s?.callMethod("getLan") == "zh-Hant" } ?: return@ret
                val subUrl = zhHant.callMethod("getSubtitleUrl") as? String ?: return@ret
                val markUrl = Uri.parse("https://www.baidu.com/s")
                    .buildUpon().appendQueryParameter("wd", subUrl).build().toString()
                val subTypeClass = "com.bapis.bilibili.community.service.dm.v1.SubtitleType".findClass(classLoader)
                val ccType = subTypeClass.getStaticObjectField("CC")
                val item = subtitleItemClass.new()
                item.callMethod("setLan", "zh-CN")
                item.callMethod("setLanDoc", "中文（简体-生成）")
                item.callMethod("setSubtitleUrl", markUrl)
                item.callMethod("setType", ccType)
                item.callMethod("setId", zhHant.callMethod("getId"))
                item.callMethod("setIdStr", zhHant.callMethod("getIdStr"))
                val thiz = param.thisObject
                thiz.callMethod("addSubtitles", subtitles.size, item)
                param.result = thiz.callMethod("getSubtitlesList")
            }
        }

        "okhttp3.Response".hookAfterMethod(classLoader, "body") ret@{ param ->
            val thiz = param.thisObject
            val request = thiz.callMethod("request")
            val url = request?.callMethod("url")?.toString() ?: ""

            if (!url.startsWith("https://www.baidu.com/s")) return@ret
            val subUrl = Uri.parse(url).getQueryParameter("wd")
            if (subUrl.isNullOrEmpty()) return@ret

            val subtitleData = try {
                URL(subUrl).openStream().reader().readText()
            } catch (e: Throwable) {
                Log.e(e)
                ""
            }
            val ccSubtitle = StringBuilder()
            val body = JSONObject(subtitleData).optJSONArray("body")
            body?.iterator()?.forEach {
                var content = it.optString("content")
                content = content.replace("\n", "||")
                ccSubtitle.append(content).appendLine()
            }
            val translator = FanhuajiTranslator()
            val translated = try {
                translator.translate(ccSubtitle.toString())
            } catch (e: Throwable) {
                Log.e(e)
                ""
            }
            if (translated.isEmpty()) return@ret
            val replaced = replace(subtitleData, translated)
            val responseBodyClass = "okhttp3.ResponseBody".findClass(classLoader)
            val mediaTypeClass = "okhttp3.MediaType".findClass(classLoader)
            val jsonType = mediaTypeClass.callStaticMethod("get", "application/json;charset=utf-8")
            val bytes = replaced.toByteArray()
            val responseBody = responseBodyClass.callStaticMethod("create", jsonType, bytes)
            param.result = responseBody
        }
    }

    private fun replace(origin: String, text: String): String {
        val result = JSONObject(origin)
        val body = result.optJSONArray("body")
        val lines = text.split("\n")
        var count = 0
        body?.iterator()?.forEach {
            it.put("content", lines[count++].replace("||", "\n"))
        }
        result.put("body", body)
        return result.toString()
    }

    private fun format(time: String): String {
        if (time.isEmpty()) return time
        val split = time.split(".")
        val seconds = split[0].toIntOrNull() ?: 0
        if (seconds == 0) return time
        val ms = if (split.size > 1) split[1].toIntOrNull() ?: 0 else 0
        val sec = seconds % 60
        val minutes = seconds / 60 % 60
        val hours = seconds / 60 / 60
        return "%02d:%02d:%02d,%d".format(hours, minutes, sec, ms)
    }

    interface ITranslator {
        fun translate(text: String): String
    }

    class FanhuajiTranslator : ITranslator {
        override fun translate(text: String): String {
            val params = JSONObject().apply {
                put("converter", "Simplified")
                put("text", text)
            }.toString()
            val contentType = "application/json;charset=utf-8"
            val api = "https://api.zhconvert.org/convert"
            val url = URL(api)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", contentType)
            }
            connection.doInput = true
            connection.doOutput = true
            connection.instanceFollowRedirects = true
            connection.connect()
            val writer = connection.outputStream.bufferedWriter()
            writer.write(params)
            writer.close()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return ""
            }
            val response = connection.inputStream.reader().readText()
            val jsonObject = JSONObject(response)
            val code = jsonObject.optInt("code", -1)
            if (code != 0) return ""
            return jsonObject.optJSONObject("data")
                ?.optString("text") ?: ""
        }
    }
}
