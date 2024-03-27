package me.iacn.biliroaming.hook

import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import java.io.File

class SplashHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("custom_splash", false) && !sPrefs.getBoolean(
                "custom_splash_logo",
                false
            )
            && !sPrefs.getBoolean("full_splash", false) && !sPrefs.getBoolean("auto_dark_splash", false)
        ) return
        Log.d("startHook: Splash")

        instance.splashInfoClass?.hookAfterMethod(
            "getMode"
        ) { param ->
            param.result = if (sPrefs.getBoolean("full_splash", false)) {
                "full"
            } else {
                param.result
            }
        }

        instance.brandSplashClass?.hookAfterMethod(
            "onViewCreated",
            View::class.java,
            Bundle::class.java
        ) { param ->
            val view = param.args[0] as View
            val containerId = getId("splash_container")
            if (sPrefs.getBoolean("auto_dark_splash", false))
                view.findViewById<View>(containerId)
                    .setBackgroundColor(
                        if (view.resources.configuration.uiMode.and(Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
                            Color.BLACK
                        else Color.WHITE
                    )
            if (sPrefs.getBoolean("custom_splash", false)) {
                val brandId = getId("brand_splash")
                val fullId = getId("full_brand_splash")
                val brandSplash = view.findViewById<ImageView>(brandId)
                val full = if (fullId != 0) view.findViewById<ImageView>(fullId) else null
                val splashImage = File(currentContext.filesDir, SPLASH_IMAGE)
                if (splashImage.exists()) {
                    val uri = Uri.fromFile(splashImage)
                    brandSplash.setImageURI(uri)
                    full?.setImageURI(uri)
                } else {
                    brandSplash.alpha = .0f
                    full?.alpha = .0f
                }
            }
            if (sPrefs.getBoolean("custom_splash_logo", false)) {
                val logoId = getId("brand_logo")
                val brandLogo = view.findViewById<ImageView>(logoId)
                val logoImage = File(currentContext.filesDir, LOGO_IMAGE)
                if (logoImage.exists())
                    brandLogo.setImageURI(Uri.fromFile(logoImage))
                else
                    brandLogo.alpha = .0f
            }
        }
    }

    companion object {
        const val SPLASH_IMAGE = "biliroaming_splash"
        const val LOGO_IMAGE = "biliroaming_logo"
    }

}
