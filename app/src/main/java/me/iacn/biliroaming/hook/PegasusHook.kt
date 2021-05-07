package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class PegasusHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val filterSet = sPrefs.getStringSet("customize_home_recommend", emptySet()).orEmpty()

    private val filterMap = mapOf(
            "advertisement" to arrayListOf("ad", "large_cover_v6", "large_cover_v9"),
            "article" to arrayListOf("article"),
            "bangumi" to arrayListOf("bangumi", "special"),
            "picture" to arrayListOf("picture"),
            "vertical" to arrayListOf("vertical"),
            "banner" to arrayListOf("banner"),
            "live" to arrayListOf("live"),
            "inline" to arrayListOf("inline")
    )

    private val filter = filterSet.flatMap {
        filterMap[it].orEmpty()
    }

    override fun startHook() {
        if (filter.isEmpty()) return
        Log.d("startHook: Pegasus")
        instance.pegasusFeedClass?.hookAfterMethod(instance.pegasusFeed(), instance.okhttpResponseClass) { param ->
            param.result.getObjectField("data")?.getObjectFieldAs<ArrayList<Any>>("items")?.removeAll {
                filter.fold(false) { acc, item ->
                    acc || item in it.getObjectFieldAs<String?>("cardGoto").orEmpty()
                }
            }
        }
    }
}
