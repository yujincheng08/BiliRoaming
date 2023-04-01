package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import java.util.List

class BangumiPageAdHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("block_bangumi_page_ads", false)) return
        Log.d("startHook: BangumiPageAd")
        // activity toast ad
        "com.bilibili.bangumi.data.page.detail.entity.OGVActivityVo".from(mClassLoader)
            ?.hookBeforeAllConstructors { param ->
                val args = param.args
                for (i in args.indices) {
                    when (val item = args[i]) {
                        is Int -> args[i] = 0
                        is List<*> -> item.clear()
                        else -> args[i] = null
                    }
                }
            }
        // mall
        instance.bangumiUniformSeasonClass?.replaceMethod(instance.bangumiUniformSeasonActivityEntrance()) { null }
    }
}
