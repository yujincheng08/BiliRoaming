package me.iacn.biliroaming.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.Options
import me.iacn.biliroaming.R
import me.iacn.biliroaming.XposedInit.Companion.moduleRes
import me.iacn.biliroaming.zhconverter.ChineseUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream

object SubtitleHelper {
    val dictFilePath: String by lazy { File(currentContext.filesDir, "t2cn.txt").absolutePath }
    val dictExist: Boolean get() = File(dictFilePath).isFile
    private const val dictUrl =
        "https://archive.biliimg.com/bfs/archive/566adec17e127bf92aed21832db0206ccecc8caa.png"

    @Synchronized
    fun downloadDict(): Boolean {
        if (dictExist) return true
        runCatching {
            val buffer = URL(dictUrl).openStream().buffered().use {
                val options = Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
                val bitmap = BitmapFactory.decodeStream(it, null, options)
                ByteBuffer.allocate(bitmap!!.byteCount).apply {
                    bitmap.let { b -> b.copyPixelsToBuffer(this); b.recycle() }
                    rewind()
                }
            }
            val bytes = ByteArray(buffer.int).also { buffer.get(it) }
            File(dictFilePath).outputStream().use { o ->
                GZIPInputStream(bytes.inputStream()).use { it.copyTo(o) }
            }
        }.onSuccess {
            return true
        }.onFailure {
            Log.e(it)
            File(dictFilePath).delete()
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
