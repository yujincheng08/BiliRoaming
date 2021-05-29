package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class LowPlayCountRecommendHook(clzLoader: ClassLoader) : BaseHook(clzLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("hide_low_play_count_recommend", false)) return
        Log.d("startHook: LowPlayCountRecommend")
        instance.pegasusFeedClass?.hookAfterMethod(
            instance.pegasusFeed(),
            instance.okhttpResponseClass
        ) { param ->
            param.result.getObjectField("data")?.getObjectFieldAs<ArrayList<Any>>("items")
                ?.removeAll {
                    try {
                        val v = toLong(it.getObjectField("coverLeftText1").toString())
                        if (v == -1L) {
                            false
                        } else {
                            v < sPrefs.getLong(
                                "hide_low_play_count_recommend_limit",
                                100
                            )
                        }
                    } catch (e: NoSuchFieldError) {
                        false
                    }
                }
        }
    }

    private fun String.isNum(): Boolean {
        for (s in this) {
            if (!Character.isDigit(s)) return false
        }
        return true
    }

    private fun toLong(str: String): Long {
        return when {
            str.isNum() ->
                return str.toDouble().toLong()
            str.contains("万") ->
                (str.replace("万", "").toDouble() * 10_000).toLong()
            str.contains("亿") ->
                (str.replace("亿", "").toDouble() * 100_000_000).toLong()
            else ->
                -1L
        }
    }
}