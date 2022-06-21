package me.iacn.biliroaming.zhconverter

import me.iacn.biliroaming.zhconverter.dictionary.DictionaryContainer

object ChineseUtils {
    /**
     * 繁体转简体
     */
    fun t2cn(content: String): String {
        return DictionaryContainer.getInstance().getDictionary("t2cn").convert(content)
    }

    fun reloadDictionary(key: String) {
        DictionaryContainer.getInstance().reloadDictionary(key)
    }
}
