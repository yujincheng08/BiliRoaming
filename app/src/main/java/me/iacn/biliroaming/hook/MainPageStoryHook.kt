package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.*

class MainPageStoryHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        Log.d("startHook: MainPageStory")
        val disableMainPageStory = sPrefs.getBoolean("disable_main_page_story", false)
        if (disableMainPageStory) {
            "com.bapis.bilibili.app.distribution.setting.experimental.TopLeft".from(mClassLoader)?.run {
                hookAfterMethod("getUrl") { param ->
                    param.result?.callMethod("setValue", "bilibili://root?tab_name=我的")
                }
                hookAfterMethod("getUrlV2") { param ->
                    param.result?.callMethod("setValue", "bilibili://root?tab_name=我的")
                }
            }
        }
    }
}