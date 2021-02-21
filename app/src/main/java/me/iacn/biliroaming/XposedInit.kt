package me.iacn.biliroaming

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Build
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import me.iacn.biliroaming.hook.*
import me.iacn.biliroaming.utils.*
import java.text.SimpleDateFormat
import java.util.*


/**
 * Created by iAcn on 2019/3/24
 * Email i@iacn.me
 */
class XposedInit : IXposedHookLoadPackage, IXposedHookZygoteInit {
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
        moduleRes = getModuleRes(modulePath)
    }

    @Throws(Throwable::class)
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (BuildConfig.APPLICATION_ID == lpparam.packageName) {
            MainActivity.Companion::class.java.name.replaceMethod(lpparam.classLoader,
                    "isModuleActive") { true }
            return
        }
        if (!Constant.BILIBILI_PACKAGE_NAME.containsValue(lpparam.packageName) &&
                "tv.danmaku.bili.MainActivityV2".findClassOrNull(lpparam.classLoader) == null) return
        Instrumentation::class.java.hookBeforeMethod("callApplicationOnCreate", Application::class.java) { param ->
            // Hook main process and download process
            @Suppress("DEPRECATION")
            when {
                !lpparam.processName.contains(":") -> {
                    if (sPrefs.getBoolean("save_log", false) ||
                            sPrefs.getBoolean("show_hint", true)) {
                        startLog()
                    }
                    Log.d("BiliBili process launched ...")
                    Log.d("BiliRoaming version: ${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) from $modulePath${if (isBuiltIn) "(BuiltIn)" else ""}")
                    Log.d("Bilibili version: ${getPackageVersion(lpparam.packageName)} (${if (is64) "64" else "32"}bit)")
                    Log.d("SDK: ${Build.VERSION.RELEASE}(${Build.VERSION.SDK_INT}); Phone: ${Build.BRAND} ${Build.MODEL}")
                    Log.d("Config: ${sPrefs.all}")
                    Log.toast("哔哩漫游已激活${
                        if (sPrefs.getBoolean("main_func", false)) ""
                        else "。但未启用番剧解锁功能，请检查哔哩漫游设置。"
                    }")
                    BiliBiliPackage(lpparam.classLoader, param.args[0] as Context)
                    startHook(HintHook(lpparam.classLoader))
                    startHook(ijkhook(lpparam.classLoader))
                    startHook(BangumiSeasonHook(lpparam.classLoader))
                    startHook(BangumiPlayUrlHook(lpparam.classLoader))
                    startHook(CustomThemeHook(lpparam.classLoader))
                    startHook(TeenagersModeHook(lpparam.classLoader))
                    startHook(CommentHook(lpparam.classLoader))
                    startHook(JsonHook(lpparam.classLoader))
                    startHook(MiniProgramHook(lpparam.classLoader))
                    startHook(AutoLikeHook(lpparam.classLoader))
                    startHook(SettingHook(lpparam.classLoader))
                    startHook(SplashHook(lpparam.classLoader))
                    startHook(EnvHook(lpparam.classLoader))
                    startHook(DownloadThreadHook(lpparam.classLoader))
                    startHook(DarkHook(lpparam.classLoader))
                    startHook(MusicNotificationHook(lpparam.classLoader))
                    startHook(DrawerHook(lpparam.classLoader))
                    startHook(CoverHook(lpparam.classLoader))
                }
                lpparam.processName.endsWith(":web") -> {
                    BiliBiliPackage(lpparam.classLoader, param.args[0] as Context)
                    CustomThemeHook(lpparam.classLoader).insertColorForWebProcess()
                    startHook(WebViewHook(lpparam.classLoader))
                }
                lpparam.processName.endsWith(":download") -> {
                    BiliBiliPackage(lpparam.classLoader, param.args[0] as Context)
                    startHook(BangumiPlayUrlHook(lpparam.classLoader))
                }
            }
        }
        lateInitHook = Activity::class.java.hookBeforeMethod("onResume") {
            startLateHook()
            lateInitHook?.unhook()
        }
    }

    private fun startHook(hooker: BaseHook) {
        try {
            hookers.add(hooker)
            hooker.startHook()
        } catch (e: Throwable) {
            Log.e(e)
            Log.toast("出现错误${e.message}，部分功能可能失效。")
        }
    }

    private fun startLateHook() {
        hookers.forEach {
            try {
                it.lateInitHook()
            } catch (e: Throwable) {
                Log.e(e)
                Log.toast("出现错误${e.message}，部分功能可能失效。")
            }
        }
    }

    private fun startLog() = try {
        logFile.delete()
        logFile.createNewFile()
        val cmd = arrayOf("logcat", "-T", SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date()), "-f", logFile.absolutePath)
        Runtime.getRuntime().exec(cmd)
    } catch (e: Throwable) {
        Log.e(e)
        null
    }

    companion object {
        lateinit var modulePath: String
        lateinit var moduleRes: Resources

        private val hookers = ArrayList<BaseHook>()
        private var lateInitHook: XC_MethodHook.Unhook? = null


        @Suppress("DEPRECATION")
        @SuppressLint("DiscouragedPrivateApi")
        @JvmStatic
        fun getModuleRes(path: String): Resources {
            val assetManager = AssetManager::class.java.newInstance()
            val addAssetPath = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
            addAssetPath.invoke(assetManager, path)
            return Resources(assetManager, null, null)
        }
    }
}
