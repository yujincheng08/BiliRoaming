package me.iacn.biliroaming.hook

import android.os.Bundle
import android.view.ViewGroup
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.sPrefs

class VideoAdHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        private const val UPRecommend = 1L shl 1
        private const val Game = 1L shl 2

        @JvmStatic
        fun parse(set: Set<String>): Long {
            return set.fold(0L) { stack, str ->
                when (str) {
                    "up_recommend" -> stack or UPRecommend
                    "game" -> stack or Game
                    else -> stack
                }
            }
        }

        @JvmStatic
        fun onPrefChanged(stack: Long) {
            val set = mutableSetOf<String>()
            if (stack and UPRecommend != 0L) {
                set += setOf(
                    "MallHolderSmall",
                    "MallHolderLarge",
                    "MallHolderSmallV2",
                    "MallHolderSmallNew",
                    "MallHolderLargeNew"
                )
            }

            if (stack and Game != 0L) {
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
        105 to "UpperHolderNone",     //Default None
        106 to "CommonHolderSmall",
        107 to "CommonHolderLarge",
        108 to "MallHolderSmall",
        109 to "MallHolderLarge",
        110 to "GameHolderSmall",
        111 to "GameHolderLarge",
        112 to "MallHolderSmallV2",
        113 to "CommonHolderSmallNew",
        114 to "CommonHolderLargeNew",
        115 to "MallHolderSmallNew",
        116 to "MallHolderLargeNew",
        117 to "GameHolderSmallNew",
        118 to "GameHolderLargeNew",
    )


    private fun enableFor(id: Int): Boolean {
        for (k in sets) {
            if (bannerMap[id] == k) return true
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
                param.args[1] = bannerMap.keys.first()
                Log.toast("已干掉推荐广告")
            }
        }
    }
}
