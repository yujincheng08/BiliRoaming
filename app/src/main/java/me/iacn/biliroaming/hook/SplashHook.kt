package me.iacn.biliroaming.hook

import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import java.io.File

class SplashHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        val customSplash = sPrefs.getBoolean("custom_splash", false)
        val customSplashLogo = sPrefs.getBoolean("custom_splash_logo", false)
        val fullSplash = sPrefs.getBoolean("full_splash", false)
        val autoDarkSplash = sPrefs.getBoolean("auto_dark_splash", false)
        if (
            !customSplash &&
            !customSplashLogo &&
            !fullSplash &&
            !autoDarkSplash
        ) return
        Log.d("startHook: Splash")

        instance.splashInfoClass?.hookAfterMethod(
            "getMode"
        ) { param -> if (fullSplash) param.result = "full" }

        instance.brandSplashClass?.hookAfterMethod(
            "onViewCreated",
            View::class.java,
            Bundle::class.java
        ) { param ->
            val view = param.args[0] as View

            // auto_dark_splash: 优先设置 splash_container 背景，回退到 root view
            if (autoDarkSplash) {
                val bgColor = if (view.resources.configuration.uiMode.and(Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
                    Color.BLACK else Color.WHITE
                val containerId = getId("splash_container")
                val container = if (containerId != 0) view.findViewById<View>(containerId) else null
                (container ?: view).setBackgroundColor(bgColor)
            }

            // 检测渲染路径：brand_splash 存在于 XML 布局，不存在于 ComposeView
            val brandId = getId("brand_splash")
            val isXmlPath = brandId != 0 && view.findViewById<ImageView>(brandId) != null

            if (isXmlPath) {
                applyXmlPath(view, brandId, customSplash, customSplashLogo)
            } else if (view is ViewGroup) {
                view.post { applyComposeOverlay(view, customSplash, customSplashLogo) }
            }
        }
    }

    private fun applyXmlPath(view: View, brandId: Int, customSplash: Boolean, customSplashLogo: Boolean) {
        if (customSplash) {
            val brandSplash = view.findViewById<ImageView>(brandId)!!
            val fullId = getId("full_brand_splash")
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
        if (customSplashLogo) {
            val logoId = getId("brand_logo")
            val brandLogo = view.findViewById<ImageView>(logoId) ?: return
            val logoImage = File(currentContext.filesDir, LOGO_IMAGE)
            if (logoImage.exists())
                brandLogo.setImageURI(Uri.fromFile(logoImage))
            else
                brandLogo.alpha = .0f
        }
    }

    /**
     * Compose 渲染路径：在 ComposeView 上叠加 ImageView。
     * ComposeView 是 ViewGroup 子类，addView 可添加覆盖层于 Compose 内容之上。
     */
    private fun applyComposeOverlay(container: ViewGroup, customSplash: Boolean, customSplashLogo: Boolean) {
        if (sPrefs.getBoolean("custom_splash", false)) {
            val splashImage = File(currentContext.filesDir, SPLASH_IMAGE)
            val iv = ImageView(container.context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                if (splashImage.exists()) {
                    setImageURI(Uri.fromFile(splashImage))
                } else {
                    visibility = View.GONE
                }
            }
            container.addView(iv)
        }
        if (sPrefs.getBoolean("custom_splash_logo", false)) {
            val logoH = container.resources.displayMetrics.heightPixels / 8
            val logoImage = File(currentContext.filesDir, LOGO_IMAGE)
            val iv = ImageView(container.context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, logoH
                )
                y = (container.height - logoH).toFloat()
                if (logoImage.exists()) {
                    setImageURI(Uri.fromFile(logoImage))
                } else {
                    visibility = View.GONE
                }
            }
            container.addView(iv)
        }
    }

    companion object {
        const val SPLASH_IMAGE = "biliroaming_splash"
        const val LOGO_IMAGE = "biliroaming_logo"
    }
}
