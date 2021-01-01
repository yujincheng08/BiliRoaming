package me.iacn.biliroaming

/**
 * Created by iAcn on 2019/4/12
 * Email i@iacn.me
 */
object Constant {
    const val PINK_PACKAGE_NAME = "tv.danmaku.bili"
    const val BLUE_PACKAGE_NAME = "com.bilibili.app.blue"
    const val PLAY_PACKAGE_NAME = "com.bilibili.app.in"
    val BILIBILI_PACKAGE_NAME = hashMapOf(
            "原版" to PINK_PACKAGE_NAME,
            "概念版" to BLUE_PACKAGE_NAME,
            "play版" to PLAY_PACKAGE_NAME
    )
    const val TAG = "BiliRoaming"
    const val HOOK_INFO_FILE_NAME = "hookinfo.dat"
    const val TYPE_SEASON_ID = 0
    const val TYPE_MEDIA_ID = 1
    const val TYPE_EPISODE_ID = 2
    const val CUSTOM_COLOR_KEY = "biliroaming_custom_color"
    const val CURRENT_COLOR_KEY = "theme_entries_current_key"
    const val DEFAULT_CUSTOM_COLOR = -0xe6b7d
    val HOST_REGEX = Regex(""":\\?/\\?/([^/]+)\\?/""")
}