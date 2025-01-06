package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class VideoQualityHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("main_func", false)) return

        val halfScreenQuality = sPrefs.getString("half_screen_quality", "0")?.toInt() ?: 0
        val fullScreenQuality = sPrefs.getString("full_screen_quality", "0")?.toInt() ?: 0
        if (halfScreenQuality != 0) {
            instance.playerPreloadHolderClass?.replaceAllMethods(instance.getPreload()) { null }
            instance.playerQualityServices().forEach { (clazz, getDefaultQnThumb) ->
                clazz?.replaceAllMethods(getDefaultQnThumb) { halfScreenQuality }
            }
        }
        if (fullScreenQuality != 0) {
            instance.playerSettingHelperClass?.replaceMethod(instance.getDefaultQn()) { fullScreenQuality }
        }

        if (halfScreenQuality != 0 || fullScreenQuality != 0) {
            instance.autoSupremumQualityClass?.hookBeforeConstructor(
                *Array(6) { Int::class.javaPrimitiveType }
            ) { param ->
                if (halfScreenQuality != 0) {
                    param.args[0] = halfScreenQuality       // loginHalf

                    param.args[3] = halfScreenQuality       // unloginHalf
                    param.args[4] = halfScreenQuality       // unloginFull
                    param.args[5] = halfScreenQuality       // unloginMobileFull
                }
                if (fullScreenQuality != 0) {
                    param.args[1] = fullScreenQuality       // loginFull
                    param.args[2] = fullScreenQuality       // loginMobileFull
                }
            }
            instance.qualityStrategyProviderClass?.hookBeforeMethod(
                instance.selectQuality(),
                instance.autoSupremumQualityClass,
                Boolean::class.javaPrimitiveType,           // isFullscreen
                Boolean::class.javaPrimitiveType            // isVideoPortrait
            ) { param ->
                // videoQuality = when {
                //     isVideoPortrait && isFullscreen -> loginFull
                //     isVideoPortrait && !isFullscreen -> unloginFull
                //     isFullscreen -> loginHalf
                //     else -> unloginHalf
                // }
                param.args[2] = true
            }
        }
    }
}
