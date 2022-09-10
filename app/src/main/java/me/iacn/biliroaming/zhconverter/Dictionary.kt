package me.iacn.biliroaming.zhconverter

import java.io.*

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
            val node = dict.bestMatch(buf, 0, len)
            if (node != null) {
                val offset = node.level
                node.value?.let { writer.write(it) }
                `in`.unread(buf, offset, len - offset)
            } else {
                `in`.unread(buf, 0, len)
                val ch = `in`.read().toChar()
                writer.write((chars[ch] ?: ch).code)
            }
        }
    }

    fun convert(str: String): String {
        return StringWriter().also {
            convert(str.reader(), it)
        }.toString()
    }
}
