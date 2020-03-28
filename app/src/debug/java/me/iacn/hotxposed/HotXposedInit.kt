package me.iacn.hotxposed

import android.content.Context
import android.content.pm.PackageManager
import dalvik.system.PathClassLoader
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import me.iacn.biliroaming.BuildConfig
import java.io.File

/**
 * Created by iAcn on 2019/3/25
 * Email i@iacn.me
 */
class HotXposedInit : IXposedHookLoadPackage {
    @Throws(Throwable::class)
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        disableModulesUpdatedNotification(lpparam)
        if (HOST_PACKAGE != lpparam.packageName && HOST_PACKAGE2 != lpparam.packageName
                && HOST_PACKAGE3 != lpparam.packageName
                && BuildConfig.APPLICATION_ID != lpparam.packageName) return
        val moduleApkFile = moduleApkFile
        if (!moduleApkFile.exists()) return
        val classLoader = PathClassLoader(moduleApkFile.absolutePath, lpparam.javaClass.classLoader)
        val xposedInitClass = classLoader.loadClass(REAL_XPOSED_INIT)
        xposedInitClass?.let{
            XposedHelpers.callMethod(it.newInstance(), "handleLoadPackage", lpparam)
        }
    }

    private fun disableModulesUpdatedNotification(lpparam: LoadPackageParam) {
        if ("de.robv.android.xposed.installer" == lpparam.packageName) {
            XposedHelpers.findAndHookMethod("de.robv.android.xposed.installer.util.NotificationUtil", lpparam.classLoader,
                    "showModulesUpdatedNotification", XC_MethodReplacement.DO_NOTHING)
        }
    }

    @get:Throws(PackageManager.NameNotFoundException::class)
    private val moduleApkFile: File
        get() {
            val activityThread = XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.app.ActivityThread", null), "currentActivityThread")
            val context = XposedHelpers.callMethod(activityThread, "getSystemContext") as Context
            val applicationInfo = context.packageManager.getApplicationInfo(BuildConfig.APPLICATION_ID, 0)
            return File(applicationInfo.sourceDir)
        }

    companion object {
        private const val HOST_PACKAGE = "tv.danmaku.bili"
        private const val HOST_PACKAGE2 = "com.bilibili.app.blue"
        private const val HOST_PACKAGE3 = "com.bilibili.app.in"
        private const val REAL_XPOSED_INIT = "me.iacn.biliroaming.XposedInit"
    }
}