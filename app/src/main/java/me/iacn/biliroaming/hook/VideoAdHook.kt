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

        private const val UPPER_HOLDER_NONE = 105

        private val ID_MAP = mapOf(
            UP_RECOMMEND to setOf(
                108, // MallHolderSmall
                109, // MallHolderLarge
                112, // MallHolderSmallV2
                115, // MallHolderSmallNew
                116, // MallHolderLargeNew
            ),
            GAME to setOf(
                110, // GameHolderSmall
                111, // GameHolderLarge
                117, // GameHolderSmallNew
                118, // GameHolderLargeNew
            ),
//             None: 105
//             COMMON to setof(
//                106, // CommonHolderSmall
//                107, // CommonHolderLarge
//                113, // CommonHolderSmallNew
//                114, // CommonHolderLargeNew
//            )
        )
    }

    private val configSet by lazy {
        sPrefs.getStringSet("enable_banner_lists", emptySet()).orEmpty().flatMap {
            ID_MAP[it].orEmpty()
        }.toSet()
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
                param.args[1] = UPPER_HOLDER_NONE
                Log.toast("已干掉推荐广告")
            }
        }
    }
}
