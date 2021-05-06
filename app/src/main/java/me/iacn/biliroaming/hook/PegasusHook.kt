package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class PegasusHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        val switch = sPrefs.getStringSet("customize_home_recommend", emptySet()).orEmpty()
        if (switch.isEmpty()) return
        Log.d("startHook: Pegasus")

        instance.pegasusFeedClass?.hookAfterMethod(instance.pegasusFeed(), instance.okhttpResponseClass) { param ->
            removeHomeRecommendItems(param.result)
        }
    }

    /**
     * 只能这么改
     * 因为即使是
     * ```kotlin
     * val dataClass = body.getObjectField("data").javaClass
     * val dataJson = body.getObjectField("data").toJson()
     * body.setObjectField("data", dataClass.fromJson(dataJson)
     * ```
     * 也会导致崩溃
     */
    private fun removeHomeRecommendItems(body: Any) {
        val filterSet = sPrefs.getStringSet("customize_home_recommend", emptySet()).orEmpty()
        val items = body.getObjectField("data")?.getObjectFieldAs<ArrayList<Any>>("items") ?: return
        if ("advertisement" in filterSet) {
            items.removeAll {
                "ad" in (it.getObjectFieldAs("cardGoto") ?: "") ||
                        "large_cover_v6" in (it.getObjectFieldAs("cardType") ?: "") ||
                        "large_cover_v9" in (it.getObjectFieldAs("cardType") ?: "")
            }
        }
        if ("article" in filterSet) {
            items.removeAll {
                "article" in (it.getObjectFieldAs("cardGoto") ?: "")
            }
        }
        if ("bangumi" in filterSet) {
            items.removeAll {
                "bangumi" in (it.getObjectFieldAs("cardGoto") ?: "") ||
                        "special" in (it.getObjectFieldAs("cardGoto") ?: "")
            }
        }
        if ("picture" in filterSet) {
            items.removeAll {
                "picture" in (it.getObjectFieldAs("cardGoto") ?: "")
            }
        }
        if ("banner" in filterSet) {
            items.removeAll {
                "banner" in (it.getObjectFieldAs("cardGoto") ?: "")
            }
        }
    }
}
