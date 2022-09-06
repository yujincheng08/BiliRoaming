package me.iacn.biliroaming.zhconverter.dictionary

import me.iacn.biliroaming.zhconverter.Trie
import java.io.*

open class BasicDictionary(
    /**
     * chars
     */
    val chars: Map<Char, Char>,
    /**
     * dict
     */
    val dict: Trie<String>,
    /**
     * maxLen
     */
    val maxLen: Int
) {

    open fun convert(ch: Char): Char {
        return chars[ch] ?: ch
    }

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
                writer.write(convert(ch).code)
            }
        }
    }

    open fun convert(str: String): String {
        val `in` = StringReader(str)
        val out = StringWriter()
        convert(`in`, out)
        return out.toString()
    }
}
