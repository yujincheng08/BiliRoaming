package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.*

class MainPageStoryHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        Log.d("startHook: MainPageStory")
        "com.bapis.bilibili.app.distribution.setting.experimental.TopLeft".from(mClassLoader)?.run {
            hookAfterMethod("getUrl") { param ->
                Log.d("com.bapis.bilibili.app.distribution.setting.experimental.TopLeft#getUrl -> hooked")
                param.result?.callMethod("setValue", "bilibili://root?tab_name=我的")
            }
            hookAfterMethod("getUrlV2") { param ->
                Log.d("com.bapis.bilibili.app.distribution.setting.experimental.TopLeft#getUrlV2 -> hooked")
                param.result?.callMethod("setValue", "bilibili://root?tab_name=我的")
            }
        }
    }
}