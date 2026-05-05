package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class VideoQualityHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("main_func", false)) return

        val halfScreenQuality = sPrefs.getString("half_screen_quality", "0")?.toInt() ?: 0
        val fullScreenQuality = sPrefs.getString("full_screen_quality", "0")?.toInt() ?: 0
        if (halfScreenQuality != 0) {
            instance.playerPreloadHolderClass?.hookAllMethods(instance.getPreload()) { null }
            instance.playerQualityServices().forEach { (clazz, getDefaultQnThumb) ->
                clazz?.hookAllMethods(getDefaultQnThumb) { halfScreenQuality }
            }
        }
        if (fullScreenQuality != 0) {
            instance.playerSettingHelperClass?.hookMethod(instance.getDefaultQn()) { fullScreenQuality }
        }

        if (halfScreenQuality != 0 || fullScreenQuality != 0) {
            instance.autoSupremumQualityClass?.hookConstructor(
                *Array(6) { Int::class.javaPrimitiveType }
            ) { chain ->
                val args = chain.args.toTypedArray()
                if (halfScreenQuality != 0) {
                    args[0] = halfScreenQuality       // loginHalf

                    args[3] = halfScreenQuality       // unloginHalf
                    args[4] = halfScreenQuality       // unloginFull
                    args[5] = halfScreenQuality       // unloginMobileFull
                }
                if (fullScreenQuality != 0) {
                    args[1] = fullScreenQuality       // loginFull
                    args[2] = fullScreenQuality       // loginMobileFull
                }
                chain.proceed(args)
            }
            instance.qualityStrategyProviderClass?.hookMethod(
                instance.selectQuality(),
                instance.autoSupremumQualityClass,
                Boolean::class.javaPrimitiveType,           // isFullscreen
                Boolean::class.javaPrimitiveType            // isVideoPortrait
            ) { chain ->
                // videoQuality = when {
                //     isVideoPortrait && isFullscreen -> loginFull
                //     isVideoPortrait && !isFullscreen -> unloginFull
                //     isFullscreen -> loginHalf
                //     else -> unloginHalf
                // }
                val args = chain.args.toTypedArray()
                args[2] = true
                chain.proceed(args)
            }
        }
    }
}
