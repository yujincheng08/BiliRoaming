package me.iacn.biliroaming.zhconverter.dictionary

import me.iacn.biliroaming.utils.SubtitleHelper
import me.iacn.biliroaming.utils.runCatchingOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DictionaryContainer {
    private val dictionaryMap = HashMap<String, BasicDictionary>(8, 1F)

    private var reloadLatch: CountDownLatch? = null

    fun getDictionary(key: String): BasicDictionary {
        runCatchingOrNull {
            reloadLatch?.await(5, TimeUnit.SECONDS)
        }
        val dictionary = dictionaryMap[key]
        if (dictionary != null) {
            return dictionary
        }
        synchronized(this) {
            var dict = dictionaryMap[key]
            if (dict != null) return dict
            dict = when (key) {
                "t2cn" -> DictionaryFactory.loadDictionary(SubtitleHelper.dictFilePath, false)
                else -> throw IllegalArgumentException("暂不支持转化方式$key")
            }
            dictionaryMap[key] = dict
            return dict
        }
    }

    fun reloadDictionary(key: String) {
        synchronized(this) {
            reloadLatch = CountDownLatch(1)
            val dict = when (key) {
                "t2cn" -> DictionaryFactory.loadDictionary(SubtitleHelper.dictFilePath, false)
                else -> throw IllegalArgumentException("暂不支持转化方式$key")
            }
            dictionaryMap[key] = dict
            reloadLatch?.countDown()
            reloadLatch = null
        }
    }

    companion object {
        private val sInstance by lazy { DictionaryContainer() }
        fun getInstance() = sInstance
    }
}
