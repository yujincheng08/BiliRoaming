package me.iacn.biliroaming.hook.gson

import me.iacn.biliroaming.utils.sPrefs
import me.iacn.biliroaming.utils.getObjectFieldOrNullAs

class SplashShowProcessor : GsonProcessor {
    override val targetClassName =
        "tv.danmaku.bili.splash.ad.model.SplashShowResponse"

    override fun shouldEnable() =
        sPrefs.getBoolean("hidden", false) && sPrefs.getBoolean("purify_splash", false)

    override fun process(result: Any) {
        result.getObjectFieldOrNullAs<MutableList<*>>("strategyList")?.clear()
    }
}