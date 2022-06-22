package me.iacn.biliroaming.zhconverter

import me.iacn.biliroaming.zhconverter.dictionary.DictionaryContainer

object ChineseUtils {

    fun t2cn(content: String): String {
        return DictionaryContainer.getInstance().getDictionary("t2cn").convert(content)
    }

    fun reloadDictionary(key: String) {
        DictionaryContainer.getInstance().reloadDictionary(key)
    }
}