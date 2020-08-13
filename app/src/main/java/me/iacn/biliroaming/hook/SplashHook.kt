package me.iacn.biliroaming.hook

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.*
import java.io.File

class SplashHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("custom_splash", false) && !XposedInit.sPrefs.getBoolean("custom_splash_logo", false)) return
        Log.d("startHook: Splash")

        instance.brandSplashClass?.hookAfterMethod("onViewCreated", View::class.java, Bundle::class.java) { param ->
            if (XposedInit.sPrefs.getBoolean("custom_splash", false)) {
                val brandSplash = param.thisObject.getObjectFieldAs<ImageView>("a")
                val splashImage = File(XposedInit.currentContext.filesDir, SPLASH_IMAGE)
                if (splashImage.exists())
                    brandSplash.setImageURI(Uri.fromFile(splashImage))
                else
                    brandSplash.alpha = .0f
            }
            if (XposedInit.sPrefs.getBoolean("custom_splash_logo", false)) {
                val brandLogo = param.thisObject.getObjectFieldAs<ImageView>("b")
                val logoImage = File(XposedInit.currentContext.filesDir, LOGO_IMAGE)
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