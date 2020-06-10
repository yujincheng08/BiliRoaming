package me.iacn.biliroaming

import android.app.AndroidAppHelper
import android.content.pm.PackageManager
import android.content.res.XResources
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import de.robv.android.xposed.IXposedHookInitPackageResources
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.callbacks.XC_InitPackageResources
import me.iacn.biliroaming.reshook.BaseHook
import me.iacn.biliroaming.reshook.SplashHook
import me.iacn.biliroaming.utils.Log


class XposedResInit : IXposedHookInitPackageResources {
    override fun handleInitPackageResources(resparam: XC_InitPackageResources.InitPackageResourcesParam) {
        if (Constant.BILIBILI_PACKAGENAME != resparam.packageName
                && Constant.BILIBILI_PACKAGENAME2 != resparam.packageName
                && Constant.BILIBILI_PACKAGENAME3 != resparam.packageName) return
        sPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID)
        sPrefs.makeWorldReadable()
        if(!sPrefs.getBoolean("beautify", false)) return

        startHook(SplashHook(resparam))

    }
    private fun startHook(hooker: BaseHook) {
        try {
            hooker.startHook()
        } catch (e: Throwable) {
            Log.e(e)
        }
    }
    companion object {
        var sPrefs: XSharedPreferences = XSharedPreferences(BuildConfig.APPLICATION_ID)
    }

}