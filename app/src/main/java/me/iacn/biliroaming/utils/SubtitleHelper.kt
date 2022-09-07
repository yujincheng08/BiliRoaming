package me.iacn.biliroaming.utils

import me.iacn.biliroaming.R
import me.iacn.biliroaming.XposedInit.Companion.moduleRes
import me.iacn.biliroaming.zhconverter.ChineseUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.zip.GZIPInputStream

object SubtitleHelper {
    val dictFilePath: String by lazy { File(currentContext.filesDir, "t2cn.txt").absolutePath }
    val dictExist: Boolean get() = File(dictFilePath).isFile
    private const val dictUrl =
        "https://archive.biliimg.com/bfs/archive/cbd69cf0913e0b573b799a697b5e29b6b69b411e.png"

    @Synchronized
    fun downloadDict(): Boolean {
        if (dictExist) return true
        val tmpDictFile = File(currentContext.filesDir, "t2cn.txt.tmp")
        runCatching {
            val decoded = URL(dictUrl).openStream().buffered()
                .use { PNGDecoder(it).decode() }
            val dataSize = decoded.readInt()
            val gzippedDict = decoded.copyOfRange(4, 4 + dataSize)
            tmpDictFile.outputStream().use { o ->
                GZIPInputStream(gzippedDict.inputStream())
                    .use { it.copyTo(o) }
            }
        }.onSuccess {
            val dictFile = File(dictFilePath).apply { delete() }
            if (tmpDictFile.renameTo(dictFile)) {
                return true
            }
            dictFile.delete()
            tmpDictFile.delete()
        }.onFailure {
            Log.e(it)
            tmpDictFile.delete()
        }
        return false
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
