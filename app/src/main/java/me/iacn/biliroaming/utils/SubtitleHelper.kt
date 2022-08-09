package me.iacn.biliroaming.utils

import me.iacn.biliroaming.R
import me.iacn.biliroaming.XposedInit.Companion.moduleRes
import me.iacn.biliroaming.zhconverter.ChineseUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream

object SubtitleHelper {
    private const val checkInterval = 60 * 1000

    val dictFilePath: String by lazy { File(currentContext.filesDir, "t2cn.txt").absolutePath }
    val dictExist: Boolean get() = File(dictFilePath).isFile
    val executor: ExecutorService by lazy { Executors.newFixedThreadPool(1) }

    fun checkDictUpdate(): String? {
        val lastCheckTime = sCaches.getLong("subtitle_dict_last_check_time", 0)
        if (System.currentTimeMillis() - lastCheckTime < checkInterval && dictExist)
            return null
        sCaches.edit().putLong("subtitle_dict_last_check_time", System.currentTimeMillis()).apply()
        val url = moduleRes.getString(R.string.subtitle_dict_latest_url)
        val json = runCatchingOrNull {
            JSONObject(URL(url).readText())
        } ?: return null
        val tagName = json.optString("tag_name")
        val latestVer = sCaches.getString("subtitle_dict_latest_version", null) ?: ""
        if (latestVer != tagName || !dictExist) {
            val sha256sum = json.optString("body")
                .takeUnless { it.isNullOrEmpty() } ?: return null
            var dictUrl = json.optJSONArray("assets")
                ?.optJSONObject(0)?.optString("browser_download_url")
                .takeUnless { it.isNullOrEmpty() } ?: return null
            dictUrl = "https://ghproxy.com/$dictUrl"
            val tmpDictFile = File(currentContext.filesDir, "t2cn.txt.tmp")
            runCatching {
                tmpDictFile.outputStream().use { o ->
                    GZIPInputStream(URL(dictUrl).openStream())
                        .use { it.copyTo(o) }
                }
            }.onSuccess {
                val dictFile = File(dictFilePath).apply { delete() }
                if (tmpDictFile.renameTo(dictFile) && dictFile.sha256sum == sha256sum) {
                    sCaches.edit().putString("subtitle_dict_latest_version", tagName).apply()
                    return dictFilePath
                }
                dictFile.delete()
                tmpDictFile.delete()
            }.onFailure {
                Log.e(it)
                tmpDictFile.delete()
            }
        }
        return null
    }

    private const val furrySubInfo = "「字幕由 富睿字幕組 搬運」\n（禁止在B站宣傳漫遊相關内容，否則拉黑）"

    private fun JSONArray.removeFurrySubInfo() = apply {
        (5 downTo 0).forEach { idx ->
            optJSONObject(idx)?.let {
                val content = it.optString("content")
                if (content == furrySubInfo) {
                    remove(idx)
                } else if (content.contains(furrySubInfo)) {
                    it.put(
                        "content",
                        content.replace("\n$furrySubInfo", "").replace("$furrySubInfo\n", "")
                    )
                }
            }
        }
    }

    fun convert(json: String): String {
        val subJson = JSONObject(json)
        var subBody = subJson.getJSONArray("body").removeFurrySubInfo()
        var subText = buildString {
            for (line in subBody) {
                val content = line.optString("content").replace("\n", "||")
                appendLine(content)
            }
        }
        // Remove srt style, bilibili not support it
        if (subText.contains("\\an") || subText.contains("<font")
            || subText.contains("<i>") || subText.contains("<b>") || subText.contains("<u>")
        ) {
            // !!! Do not remove symbol '\' for "\}", Android need it
            val noStyleRegex =
                """\{(\\)?\\an\d+\}|<font\s.*>|<(\\)?/font>|<i>|<(\\)?/i>|<b>|<(\\)?/b>|<u>|<(\\)?/u>""".toRegex()
            subText = subText.replace(noStyleRegex, "")
        }
        val converted = ChineseUtils.t2cn(subText)
        val lines = converted.split('\n')
        var count = 0
        for (line in subBody) {
            line.put("content", lines[count++].replace("||", "\n"))
        }
        subBody = subBody.appendInfo(moduleRes.getString(R.string.subtitle_append_info))
        return subJson.apply {
            put("body", subBody)
        }.toString()
    }

    fun reloadDict() {
        ChineseUtils.reloadDictionary("t2cn")
    }

    fun errorResponse(content: String): String {
        return JSONObject().apply {
            put("body", JSONArray().apply {
                put(JSONObject().apply {
                    put("from", 0)
                    put("location", 2)
                    put("to", 9999)
                    put("content", content)
                })
            })
        }.toString()
    }

    private fun JSONArray.appendInfo(content: String): JSONArray {
        if (length() == 0) return this
        val firstLine = optJSONObject(0)
            ?: return this
        val lastLine = optJSONObject(length() - 1)
            ?: return this
        val firstFrom = firstLine.optDouble("from")
            .takeIf { !it.isNaN() } ?: return this
        val lastTo = lastLine.optDouble("to")
            .takeIf { !it.isNaN() } ?: return this
        val minDuration = 1.0
        val maxDuration = 5.0
        val interval = 0.3
        val appendStart = firstFrom >= minDuration + interval
        val from = if (appendStart) 0.0 else lastTo + interval
        val to = if (appendStart) {
            from + (firstFrom - interval).coerceAtMost(maxDuration)
        } else from + maxDuration
        val info = JSONObject().apply {
            put("from", from)
            put("location", 2)
            put("to", to)
            put("content", content)
        }
        return if (appendStart) {
            JSONArray().apply {
                put(info)
                for (jo in this@appendInfo) {
                    put(jo)
                }
            }
        } else apply { put(info) }
    }
}