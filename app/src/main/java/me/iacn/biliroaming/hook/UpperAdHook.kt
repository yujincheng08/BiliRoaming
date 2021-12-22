package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookBeforeAllMethods
import me.iacn.biliroaming.utils.sPrefs

class UpperAdHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        private const val UPPER_HOLDER_NONE = 105

        private val ID_MAP = setOf(
            108, // MallHolderSmall
            109, // MallHolderLarge
            112, // MallHolderSmallV2
            115, // MallHolderSmallNew
            116, // MallHolderLargeNew
            106, // CommonHolderSmall
            107, // CommonHolderLarge
            113, // CommonHolderSmallNew
            114, // CommonHolderLargeNew
            110, // GameHolderSmall
            111, // GameHolderLarge
            117, // GameHolderSmallNew
            118, // GameHolderLargeNew
//             None: 105
        )
    }

    override fun startHook() {
        if (!sPrefs.getBoolean("block_upper_recommend_ad", false)) return
        Log.d("Start hook: UpperAdHook")
        instance.videoUpperAdClass?.hookBeforeAllMethods(
            instance.videoUpperAd()
        ) { param ->
            val id = param.args[1] as Int
            if (ID_MAP.contains(id)) {
                param.args[1] = UPPER_HOLDER_NONE
                Log.toast("已清除视频下方推荐")
            }
            if (id != UPPER_HOLDER_NONE)
                Log.d("not filtered $id")
        }
    }
}
