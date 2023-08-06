package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class BangumiPageAdHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("block_view_page_ads", false)) return
        Log.d("startHook: BangumiPageAd")

        val oGVActivityVoHooker: Hooker = { param ->
            val args = param.args
            for (i in args.indices) {
                when (val item = args[i]) {
                    is Int -> args[i] = 0
                    is MutableList<*> -> item.clear()
                    else -> args[i] = null
                }
            }
        }
        // activity toast ad
        "com.bilibili.bangumi.data.page.detail.entity.OGVActivityVo".from(mClassLoader)
            ?.hookBeforeAllConstructors(oGVActivityVoHooker)
        "com.bilibili.ship.theseus.ogv.activity.OGVActivityVo".from(mClassLoader)
            ?.hookBeforeAllConstructors(oGVActivityVoHooker)

        // mall
        instance.bangumiUniformSeasonActivityEntrance()?.let {
            instance.bangumiUniformSeasonClass?.replaceMethod(it) { null }
        }
    }
}
