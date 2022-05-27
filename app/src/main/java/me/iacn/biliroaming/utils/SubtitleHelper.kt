package me.iacn.biliroaming.utils

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.withContext
import me.iacn.biliroaming.R
import me.iacn.biliroaming.XposedInit.Companion.moduleRes
import me.iacn.biliroaming.zhconverter.ChineseUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit

object SubtitleHelper {
    private val checkInterval = TimeUnit.MINUTES.toMillis(10)

    val dictFilePath: String by lazy { File(currentContext.filesDir, "t2cn.txt").absolutePath }
    val dictExist: Boolean get() = File(dictFilePath).isFile

    suspend fun checkDictUpdate(): String? {
        val lastCheckTime = sCaches.getLong("subtitle_dict_last_check_time", 0)
        if (System.currentTimeMillis() - lastCheckTime < checkInterval && dictExist)
            return null
        sCaches.edit().putLong("subtitle_dict_last_check_time", System.currentTimeMillis()).apply()
        val url = moduleRes.getString(R.string.subtitle_dict_releases_url)
        val json = fetchJsonArray(url) ?: return null
        for (item in json) {
            val tagName = item.optString("tag_name").takeIf {
                it.startsWith("t2cn")
            } ?: continue
            item.optBoolean("draft", true).takeIf { !it } ?: continue
            val latestVer = sCaches.getString("subtitle_dict_latest_version", null) ?: ""
            if (latestVer != tagName || !dictExist) {
                val assets = item.optJSONArray("assets") ?: return null
                val dictAsset = assets.optJSONObject(0) ?: return null
                val dictUrl = dictAsset.getString("browser_download_url")
                val tmpDictFile = File(currentContext.filesDir, "t2cn.txt.tmp")
                tmpDictFile.runCatching {
                    withContext(Dispatchers.IO) { writeText(URL(dictUrl).readText()) }
                }.onSuccess {
                    val dictFile = File(dictFilePath).apply { delete() }
                    if (tmpDictFile.renameTo(dictFile)) {
                        sCaches.edit().putString("subtitle_dict_latest_version", tagName).apply()
                        return dictFilePath
                    }
                    return null
                }.onFailure {
                    Log.e(it)
                    tmpDictFile.delete()
                    return null
                }
            }
            break
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
        subBody = appendInfo(
            subBody,
            moduleRes.getString(R.string.subtitle_append_info)
        )
        return subJson.apply {
            put("body", subBody)
        }.toString()
    }

    fun reloadDict() {
        ChineseUtils.reloadDictionary("t2cn")
    }

    fun prepareBuiltInDict(): Boolean {
        val dictStream = SubtitleHelper::class.java.classLoader
            ?.getResourceAsStream("assets/t2cn.txt")
            ?: return false
        val dictFile = File(dictFilePath)
        dictFile.runCatching {
            delete()
            dictStream.use {
                it.copyTo(dictFile.outputStream())
            }
        }.onSuccess { return true }.onFailure {
            dictFile.delete()
            return false
        }
        return false
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

    private fun appendInfo(body: JSONArray, content: String): JSONArray {
        if (body.length() == 0) return body
        val firstLine = body.optJSONObject(0)
            ?: return body
        val lastLine = body.optJSONObject(body.length() - 1)
            ?: return body
        val firstFrom = firstLine.optDouble("from")
            .takeIf { !it.isNaN() } ?: return body
        val lastTo = lastLine.optDouble("to")
            .takeIf { !it.isNaN() } ?: return body
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
                for (jo in body) {
                    put(jo)
                }
            }
        } else body.apply { put(info) }
    }
}
