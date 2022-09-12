package me.iacn.biliroaming.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.Options
import me.iacn.biliroaming.R
import me.iacn.biliroaming.XposedInit.Companion.moduleRes
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.URL
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream

class TrieNode<V>(val key: Char, val level: Int = 0) {
    private val children = hashMapOf<Char, TrieNode<V>>()

    val isLeaf get() = value != null
    var value: V? = null

    fun getOrAddChild(k: Char) = children.computeIfAbsent(k) { TrieNode(k, level + 1) }

    fun child(k: Char) = children[k]
}

class Trie<T> {
    private val root = TrieNode<T>(key = '\u0000')

    fun add(w: String, value: T) {
        if (w.isEmpty()) return
        var p = root
        for (c in w.toCharArray())
            p = p.getOrAddChild(c)
        p.value = value
    }

    fun bestMatch(sen: CharArray): TrieNode<T>? {
        var node: TrieNode<T> = root
        var leaf: TrieNode<T>? = null
        for (c in sen) {
            node = node.child(c) ?: break
            if (node.isLeaf) leaf = node
        }
        return leaf
    }
}

class Dictionary(
    private val chars: Map<Char, Char>,
    private val dict: Trie<String>,
    private val maxLen: Int
) {
    private fun convert(reader: Reader, writer: Writer) {
        val `in` = PushbackReader(reader.buffered(), maxLen)
        val buf = CharArray(maxLen)
        var len: Int

        while (true) {
            len = `in`.read(buf)
            if (len == -1) break
            val node = dict.bestMatch(buf)
            if (node != null) {
                val offset = node.level
                node.value?.let { writer.write(it) }
                `in`.unread(buf, offset, len - offset)
            } else {
                `in`.unread(buf, 0, len)
                val ch = `in`.read().toChar()
                writer.write(chars.getOrDefault(ch, ch).code)
            }
        }
    }

    fun convert(str: String) = StringWriter().also {
        convert(str.reader(), it)
    }.toString()

    companion object {
        private const val SHARP = '#'
        private const val EQUAL = '='

        fun loadDictionary(mappingFile: File): Dictionary {
            val charMap = HashMap<Char, Char>(4096)
            val dict = Trie<String>()
            var maxLen = 2
            mappingFile.bufferedReader().useLines { lines ->
                lines.filterNot { it.isBlank() || it.trimStart().startsWith(SHARP) }
                    .map { it.split(EQUAL, limit = 2) }.filter { it.size == 2 }.forEach { (k, v) ->
                        if (k.length == 1 && v.length == 1) {
                            charMap[k[0]] = v[0]
                        } else {
                            maxLen = k.length.coerceAtLeast(maxLen)
                            dict.add(k, v)
                        }
                    }
            }
            return Dictionary(charMap, dict, maxLen)
        }
    }
}

object SubtitleHelper {
    private val dictFile by lazy { File(currentContext.filesDir, "t2cn.txt") }
    private val dictionary by lazy { Dictionary.loadDictionary(dictFile) }
    private const val dictUrl =
        "https://archive.biliimg.com/bfs/archive/566adec17e127bf92aed21832db0206ccecc8caa.png"

    // !!! Do not remove symbol '\' for "\}", Android need it
    @Suppress("RegExpRedundantEscape")
    private val noStyleRegex =
        Regex("""\{\\?\\an\d+\}|<font\s[^>]*>|<\\?/font>|<i>|<\\?/i>|<b>|<\\?/b>|<u>|<\\?/u>""")
    val dictExist get() = dictFile.isFile

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
            dictFile.outputStream().use { o ->
                GZIPInputStream(bytes.inputStream()).use { it.copyTo(o) }
            }
        }.onSuccess {
            return true
        }.onFailure {
            Log.e(it)
            dictFile.delete()
        }
        return false
    }

    fun convert(json: String): String {
        val subJson = JSONObject(json)
        var subBody = subJson.optJSONArray("body") ?: return json
        val subText = subBody.asSequence<JSONObject>().map { it.optString("content") }
            .joinToString("\u0000").run {
                // Remove srt style, bilibili not support it
                if (contains("\\an") || contains("<font")
                    || contains("<i>") || contains("<b>") || contains("<u>")
                ) replace(noStyleRegex, "") else this
            }
        val converted = dictionary.convert(subText)
        val lines = converted.split('\u0000')
        subBody.asSequence<JSONObject>().zip(lines.asSequence()).forEach { (obj, line) ->
            obj.put("content", line)
        }
        subBody = subBody.appendInfo(moduleRes.getString(R.string.subtitle_append_info))
        return subJson.apply {
            put("body", subBody)
        }.toString()
    }

    fun errorResponse(content: String) = JSONObject().apply {
        put("body", JSONArray().apply {
            put(JSONObject().apply {
                put("from", 0)
                put("location", 2)
                put("to", 9999)
                put("content", content)
            })
        })
    }.toString()

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
