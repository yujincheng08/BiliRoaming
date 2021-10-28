package me.iacn.biliroaming.hook

import android.os.Bundle
import android.view.ViewGroup
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.sPrefs

class VideoAdHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        class VideoAdType {
            companion object {
                const val UPRecommend = 1L shl 1
                const val Game = 1L shl 2
            }
        }

        @JvmStatic
        fun parse(set: Set<String>): Long {
            return set.fold(0L) { stack, str ->
                when (str) {
                    "up_recommend" -> stack or VideoAdType.UPRecommend
                    "game" -> stack or VideoAdType.Game
                    else -> stack
                }
            }
        }

        @JvmStatic
        fun onPrefChanged(stack: Long) {
            val set = mutableSetOf<String>()
            if (stack and VideoAdType.UPRecommend != 0L) {
                set += setOf(
                    "MallHolderSmall",
                    "MallHolderLarge",
                    "MallHolderSmallV2",
                    "MallHolderSmallNew",
                    "MallHolderLargeNew"
                )
            }

            if (stack and VideoAdType.Game != 0L) {
                set += setOf(
                    "GameHolderSmall",
                    "GameHolderLarge",
                    "GameHolderSmallNew",
                    "GameHolderLargeNew",
                )
            }
            sPrefs.edit().putStringSet("enable_banner_lists", set).apply()
        }
    }


    private val sets by lazy {
        sPrefs.getStringSet("enable_banner_lists", emptySet()) ?: emptySet()
    }

    private val bannerMap = mapOf(
//        "UpperHolderNone" to 105,     //Default None
        "CommonHolderSmall" to 106,
        "CommonHolderLarge" to 107,
        "MallHolderSmall" to 108,
        "MallHolderLarge" to 109,
        "GameHolderSmall" to 110,
        "GameHolderLarge" to 111,
        "MallHolderSmallV2" to 112,
        "CommonHolderSmallNew" to 113,
        "CommonHolderLargeNew" to 114,
        "MallHolderSmallNew" to 115,
        "MallHolderLargeNew" to 116,
        "GameHolderSmallNew" to 117,
        "GameHolderLargeNew" to 118,
    )


    private fun enableFor(id: Int): Boolean {
        for (k in sets) {
            if (bannerMap[k] == id) return true
        }
        return false
    }

    override fun startHook() {
        if (sets.isEmpty()) return
        Log.d("Start hook: AdHook")
        "com.bilibili.ad.adview.videodetail.upper.b".hookBeforeMethod(
            mClassLoader,
            "a",
            ViewGroup::class.java,
            Int::class.java,
            Bundle::class.java
        ) { param ->
            val id = param.args[1] as Int
            if (enableFor(id)) {
                param.args[1] = 105
                Log.toast("已干掉推荐广告")
            }
        }
    }
}
