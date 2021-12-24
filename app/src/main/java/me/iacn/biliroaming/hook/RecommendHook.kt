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
        if (!sPrefs.getBoolean("block_upper_recommend_ad", false)) return
        Log.d("Start hook: RecommendHook")
        instance.videoUpperAdClass?.hookBeforeAllMethods(
            instance.videoUpperAd()
        ) { param ->
            (param.args[1] as? Int)?.let { id ->
                if (id != UPPER_HOLDER_NONE) {
                    param.args[1] = UPPER_HOLDER_NONE
                    Log.toast("已清除视频下方推荐")
                }
            } ?: run { param.args[1] = null }
        }
    }
}
