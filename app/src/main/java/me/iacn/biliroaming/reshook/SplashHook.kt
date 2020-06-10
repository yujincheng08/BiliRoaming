package me.iacn.biliroaming.reshook

import android.app.AndroidAppHelper
import android.content.pm.PackageManager
import android.content.res.XResources
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam
import me.iacn.biliroaming.XposedResInit
import me.iacn.biliroaming.utils.Log

class SplashHook(params: InitPackageResourcesParam) : BaseHook(params) {
    override fun startHook() {
        if (!XposedResInit.sPrefs.getBoolean("beautify_splash", false)) return
        val theme = params.res.newTheme()
        getThemeId()?.let { theme.applyStyle(it, true) } ?: return
        val layerListId = theme
                .obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
                .getResourceId(0, -1)
        if (layerListId == -1) return

        params.res.setReplacement(layerListId, object : XResources.DrawableLoader() {
            override fun newDrawable(res: XResources, id: Int): Drawable {
                try {
                    val encodedImage = XposedResInit.sPrefs.getString("splash_image", null)
                            ?: return res.getDrawable(id, theme)
                    val decodedString: ByteArray = Base64.decode(encodedImage, Base64.DEFAULT)
                    Log.d("replaced splash")
                    return BitmapDrawable(res, BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size))
                } catch (e: Throwable) {
                    Log.d(e)
                }
                return res.getDrawable(id, theme)
            }
        })
    }

    private fun getThemeId(): Int? {
        return try {
            AndroidAppHelper.currentApplication()
                    ?.packageManager
                    ?.getPackageInfo(params.packageName, PackageManager.GET_ACTIVITIES)
                    ?.activities
                    ?.find { it.name.contains("splash.SplashActivity") }
                    ?.theme
        } catch (e: Throwable) {
            Log.d(e)
            null
        }
    }

}