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

    fun convert(json: String): String {
        val subJson = JSONObject(json)
        var subBody = subJson.getJSONArray("body")
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
    
    private const val furrySubInfoT = "「字幕由 富睿字幕組 搬運」\n（禁止在B站宣傳漫遊相關内容，否則拉黑）"
    private const val furrySubInfoS = "「字幕由 富睿字幕组 搬运」\n（禁止在B站宣传漫游相关内容，否则拉黑）"
    private const val furrySubInfoS2 =
        "「字幕由 富睿字幕组 搬运」\n（禁止在B站宣传漫游相关内容，否则拉黑）\n（禁止在泰区评论，禁止在B站任何地方讨论泰区相关内容）"
    private val mineSubInfo by lazy { moduleRes.getString(R.string.subtitle_append_info) }

    fun JSONArray.removeSubAppendedInfo() = apply {
        var maybeHasSame = false
        (5 downTo 0).forEach { idx ->
            optJSONObject(idx)?.let {
                val content = it.optString("content")
                if (content == furrySubInfoT || content == furrySubInfoS || content == furrySubInfoS2 || content == mineSubInfo) {
                    remove(idx)
                } else if (content.contains(furrySubInfoT)
                    || content.contains(furrySubInfoS)
                    || content.contains(furrySubInfoS2)
                ) {
                    maybeHasSame = true
                    val newContent = content
                        .replace("\n$furrySubInfoT", "")
                        .replace("$furrySubInfoT\n", "")
                        .replace("\n$furrySubInfoS2", "")
                        .replace("$furrySubInfoS2\n", "")
                        .replace("\n$furrySubInfoS", "")
                        .replace("$furrySubInfoS\n", "")
                    it.put("content", newContent)
                }
            }
        }
        if (maybeHasSame) {
            var end = -1
            var start = -1
            var content = ""
            var from = 0.0
            var to = 0.0
            (5 downTo 0).forEach { idx ->
                optJSONObject(idx)?.let {
                    val f = it.optDouble("from")
                    val t = it.optDouble("to")
                    val c = it.optString("content")
                    if (end == -1) {
                        end = idx; content = c; to = t; from = f
                    } else {
                        if (c != content) {
                            if (start != -1) {
                                for (i in end downTo start + 1)
                                    remove(i)
                                optJSONObject(start)?.put("to", to)
                            }
                            end = idx; content = c; to = t; from = f; start = -1
                        } else if (t == from) {
                            start = idx; from = t
                            if (start == 0) {
                                for (i in end downTo 1)
                                    remove(i)
                                optJSONObject(0)?.put("to", to)
                            }
                        }
                    }
                }
            }
        }
        val lastIdx = length() - 1
        optJSONObject(lastIdx)?.let {
            val content = it.optString("content")
            if (content == mineSubInfo) {
                remove(lastIdx)
            }
        }
    }

    fun JSONArray.reSort() = apply {
        for (o in this) {
            val content = o.getString("content")
            val from = o.getDouble("from")
            val location = o.getInt("location")
            val to = o.getDouble("to")
            o.remove("content")
            o.remove("from")
            o.remove("location")
            o.remove("to")
            o.put("content", content)
            o.put("from", from)
            o.put("location", location)
            o.put("to", to)
        }
    }

    fun JSONArray.convertToSrt(): String {
        fun timeFormat(time: Double): String {
            val ms = (1000 * (time - time.toInt())).toInt()
            val seconds = time.toInt()
            val sec = seconds % 60
            val minutes = seconds / 60
            val min = minutes % 60
            val hour = minutes / 60
            return "%02d:%02d:%02d,%03d".format(hour, min, sec, ms)
        }

        var lineCount = 1
        val result = StringBuilder()
        for (o in this) {
            val content = o.optString("content")
            val from = o.optDouble("from")
            val to = o.optDouble("to")
            result.appendLine(lineCount++)
            result.appendLine(timeFormat(from) + " --> " + timeFormat(to))
            result.appendLine(content)
            result.appendLine()
        }
        return result.toString()
    }
}