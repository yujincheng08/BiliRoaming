package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookBeforeAllMethods
import me.iacn.biliroaming.utils.sPrefs

class RecommendHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        private const val UPPER_HOLDER_NONE = 105
    }

    override fun startHook() {
        //region <UpperVideoRecommend>
        if (!sPrefs.getBoolean("block_upper_recommend_ad", false)) return
        Log.d("Start hook: UpperVideoRecommend")
        instance.videoUpperAdClass?.hookBeforeAllMethods(
            instance.videoUpperAd()
        ) { param ->
            val id = param.args[1] as Int
            if (id != UPPER_HOLDER_NONE) {
                param.args[1] = UPPER_HOLDER_NONE
                Log.toast("已清除视频下方推荐")
            }
        }
        //endregion
    }
}
