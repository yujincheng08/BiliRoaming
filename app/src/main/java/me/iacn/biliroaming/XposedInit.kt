package me.iacn.biliroaming

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AndroidAppHelper
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.Context.MODE_MULTI_PROCESS
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import me.iacn.biliroaming.hook.*
import me.iacn.biliroaming.utils.*
import java.io.File
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
        }
        if (!Constant.BILIBILI_PACKAGENAME.containsValue(lpparam.packageName)) return
        Instrumentation::class.java.hookBeforeMethod("callApplicationOnCreate", Application::class.java) { param ->
            // Hook main process and download process
            @Suppress("DEPRECATION")
            when (lpparam.processName) {
                "tv.danmaku.bili", "com.bilibili.app.blue", "com.bilibili.app.in" -> {
                    if (sPrefs.getBoolean("save_log", false) ||
                            sPrefs.getBoolean("show_hint", true)) {
                        startLog()
                    }
                    Log.d("BiliBili process launched ...")
                    Log.d("BiliRoaming version: ${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) from $modulePath${if (isBuiltIn) "(BuiltIn)" else ""}")
                    Log.d("Bilibili version: ${getPackageVersion(lpparam.packageName)} (${if (is64) "64" else "32"}bit)")
                    Log.d("SDK: ${Build.VERSION.RELEASE}(${Build.VERSION.SDK_INT}); Phone: ${Build.BRAND} ${Build.MODEL}")
                    Log.d("Config: ${sPrefs.all}")
                    toastMessage("哔哩漫游已激活${
                        if (sPrefs.getBoolean("main_func", false)) ""
                        else "。但未启用番剧解锁功能，请检查哔哩漫游设置。"
                    }")
                    BiliBiliPackage(lpparam.classLoader, param.args[0] as Context)
                    startHook(HintHook(lpparam.classLoader))
                    startHook(BangumiSeasonHook(lpparam.classLoader))
                    startHook(BangumiPlayUrlHook(lpparam.classLoader))
                    startHook(CustomThemeHook(lpparam.classLoader))
                    startHook(TeenagersModeHook(lpparam.classLoader))
                    startHook(CommentHook(lpparam.classLoader))
                    startHook(JsonHook(lpparam.classLoader))
                    startHook(CDNHook(lpparam.classLoader, lpparam.processName))
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
                "tv.danmaku.bili:web", "com.bilibili.app.in:web", "com.bilibili.app.blue:web" -> {
                    BiliBiliPackage(lpparam.classLoader, param.args[0] as Context)
                    CustomThemeHook(lpparam.classLoader).insertColorForWebProcess()
                }
                "tv.danmaku.bili:download", "com.bilibili.app.in:download", "com.bilibili.app.blue:download" -> {
                    BiliBiliPackage(lpparam.classLoader, param.args[0] as Context)
                    startHook(CDNHook(lpparam.classLoader, lpparam.processName))
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
            toastMessage("出现错误${e.message}，部分功能可能失效。")
        }
    }

    private fun startLateHook() {
        hookers.forEach {
            try {
                it.lateInitHook()
            } catch (e: Throwable) {
                Log.e(e)
                toastMessage("出现错误${e.message}，部分功能可能失效。")
            }
        }
    }

    private fun startLog(): Process? {
        return try {
            logFile.delete()
            logFile.createNewFile()
            val cmd = arrayOf("logcat", "-T", SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date()), "-f", logFile.absolutePath)
            Runtime.getRuntime().exec(cmd)
        } catch (e: Throwable) {
            Log.e(e)
            null
        }
    }

    companion object {
        @Suppress("DEPRECATION")
        val sPrefs
            get() = currentContext.getSharedPreferences("biliroaming", MODE_MULTI_PROCESS)!!
        val currentContext by lazy { AndroidAppHelper.currentApplication() as Context }
        private val handler by lazy { Handler(Looper.getMainLooper()) }
        private val toast by lazy { Toast.makeText(currentContext, "", Toast.LENGTH_SHORT) }
        val logFile by lazy { File(currentContext.externalCacheDir, "log.txt") }
        lateinit var modulePath: String
        lateinit var moduleRes: Resources

        private val hookers = ArrayList<BaseHook>()
        private var lateInitHook: XC_MethodHook.Unhook? = null


        val isBuiltIn
            get() = modulePath.endsWith("so")

        val is64
            get() = currentContext.applicationInfo.nativeLibraryDir.contains("64")

        fun toastMessage(msg: String, force: Boolean = false) {
            if (!force && !sPrefs.getBoolean("show_info", true)) return
            handler.post {
                toast.setText("哔哩漫游：$msg")
                toast.show()
            }
        }

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