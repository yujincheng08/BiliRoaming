package me.iacn.biliroaming.zhconverter

import java.io.File

object DictionaryFactory {
    private const val SHARP = '#'
    private const val EQUAL = '='

    fun loadDictionary(mappingFile: File): Dictionary {
        val charMap = HashMap<Char, Char>(4096)
        val dict = Trie<String>()
        var maxLen = 2
        mappingFile.bufferedReader().useLines { lines ->
            lines.filterNot { it.isBlank() || it.trimStart().startsWith(SHARP) }
                .map { it.split(EQUAL, limit = 2) }.filter { it.size == 2 }.forEach { pair ->
                    if (pair[0].length == 1 && pair[1].length == 1) {
                        charMap[pair[0][0]] = pair[1][0]
                    } else {
                        maxLen = pair[0].length.coerceAtLeast(maxLen)
                        dict.add(pair[0], pair[1])
                    }
                }
        }
        return Dictionary(charMap, dict, maxLen)
    }
}
