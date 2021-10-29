package me.iacn.biliroaming.hook

import android.os.Bundle
import android.view.ViewGroup
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.sPrefs

class VideoAdHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        private const val UP_RECOMMEND = "up_recommend"
        private const val GAME = "game"

        private val keyMap = mapOf(
            UP_RECOMMEND to (1L shl 1),
            GAME to (1L shl 2)
        )

        @JvmStatic
        fun parse(set: Set<String>): Long {
            return set.fold(0L) { stack, str ->
                stack or (keyMap[str] ?: stack)
            }
        }

        @JvmStatic
        fun onPrefChanged(stack: Long) {
            val set = mutableSetOf<String>()
            if (stack and (keyMap[UP_RECOMMEND] ?: 0) != 0L) {
                set += setOf(
                    "MallHolderSmall",
                    "MallHolderLarge",
                    "MallHolderSmallV2",
                    "MallHolderSmallNew",
                    "MallHolderLargeNew"
                )
            }

            if (stack and (keyMap[GAME] ?: 0) != 0L) {
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

    private val configSet by lazy {
        sPrefs.getStringSet("enable_banner_lists", emptySet()).orEmpty().let { set ->
            bannerMap.filterValues {
                set.contains(it)
            }.keys
        }
    }

    override fun startHook() {
        if (configSet.isEmpty()) return
        Log.d("Start hook: AdHook")
        "com.bilibili.ad.adview.videodetail.upper.b".hookBeforeMethod(
            mClassLoader,
            "a",
            ViewGroup::class.java,
            Int::class.java,
            Bundle::class.java
        ) { param ->
            val id = param.args[1] as Int
            if (configSet.contains(id)) {
                param.args[1] = bannerMap.keys.first()
                Log.toast("已干掉推荐广告")
            }
        }
    }
}
