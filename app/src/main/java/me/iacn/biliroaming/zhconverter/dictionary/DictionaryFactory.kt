package me.iacn.biliroaming.zhconverter.dictionary

import me.iacn.biliroaming.zhconverter.Trie
import java.io.File

object DictionaryFactory {
    private const val SHARP = '#'
    private const val EQUAL = '='

    private fun String.liteSplit(delimiter: Char): Array<String> {
        val index = indexOf(delimiter)
        return if (index < 0) {
            arrayOf(this)
        } else {
            arrayOf(substring(0, index), substring(index + 1))
        }
    }

    fun loadDictionary(mappingFile: String, reverse: Boolean): BasicDictionary {
        val charMap = HashMap<Char, Char>(4096)
        val dict = Trie<String>()
        var maxLen = 2
        val dictFile = File(mappingFile).inputStream()
        dictFile.bufferedReader().useLines { lines ->
            lines.filterNot { it.isBlank() || it.trimStart().startsWith(SHARP) }
                .map { it.liteSplit(EQUAL) }.filter { it.size == 2 }.forEach { pair ->
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