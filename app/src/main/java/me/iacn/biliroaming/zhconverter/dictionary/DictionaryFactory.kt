package me.iacn.biliroaming.zhconverter.dictionary

import me.iacn.biliroaming.zhconverter.Trie
import java.io.File

object DictionaryFactory {
    private const val SHARP = "#"
    private const val EQUAL = "="

    /**
     * 一个简单的字符串分割，避免直接使用String#split的正则
     */
    private fun split(content: String, split: String): Array<String> {
        val index = content.indexOf(split)
        return if (index < 0) {
            arrayOf(content)
        } else {
            arrayOf(content.substring(0, index), content.substring(index + 1))
        }
    }

    fun loadDictionary(mappingFile: String, reverse: Boolean): BasicDictionary {
        val charMap = HashMap<Char, Char>(256)
        val dict = Trie<String>()
        var maxLen = 2
        val dictFile = File(mappingFile).inputStream()
        dictFile.bufferedReader().use {
            var line: String?
            var pair: Array<String>
            while (true) {
                line = it.readLine() ?: break
                if (line.isBlank() || line.trimStart().startsWith(SHARP)) {
                    continue
                }
                pair = split(line, EQUAL)
                if (pair.size < 2) {
                    continue
                }
                if (reverse) {
                    if (pair[0].length == 1 && pair[1].length == 1) {
                        charMap[pair[1][0]] = pair[0][0]
                    } else {
                        maxLen = pair[0].length.coerceAtLeast(maxLen)
                        dict.add(pair[1], pair[0])
                    }
                } else {
                    if (pair[0].length == 1 && pair[1].length == 1) {
                        charMap[pair[0][0]] = pair[1][0]
                    } else {
                        maxLen = pair[0].length.coerceAtLeast(maxLen)
                        dict.add(pair[0], pair[1])
                    }
                }
            }
        }
        return BasicDictionary(charMap, dict, maxLen)
    }
}
