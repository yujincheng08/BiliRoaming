package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class BangumiPageAdHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("block_view_page_ads", false)) return
        Log.d("startHook: BangumiPageAd")

        val oGVActivityVoHooker: HookCallback = { chain ->
            val args = chain.args.toTypedArray()
            for (i in args.indices) {
                when (val item = args[i]) {
                    is Int -> args[i] = 0
                    is MutableList<*> -> item.clear()
                    else -> args[i] = null
                }
            }
            chain.proceed(args)
        }
        // activity toast ad
        "com.bilibili.bangumi.data.page.detail.entity.OGVActivityVo".from(mClassLoader)
            ?.hookAllConstructors(oGVActivityVoHooker)
        "com.bilibili.ship.theseus.ogv.activity.OGVActivityVo".from(mClassLoader)
            ?.hookAllConstructors(oGVActivityVoHooker)

        // mall
        instance.bangumiUniformSeasonActivityEntrance()?.let {
            instance.bangumiUniformSeasonClass?.hookMethod(it) { null }
        }
    }
}
