package me.iacn.biliroaming

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.Context.MODE_MULTI_PROCESS
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import me.iacn.biliroaming.hook.*
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.replaceMethod


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
                    Log.d("BiliBili process launched ...")
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
    }

    private fun startHook(hooker: BaseHook) {
        try {
            hooker.startHook()
        } catch (e: Throwable) {
            Log.e(e)
            toastMessage("出现错误${e.message}，部分功能可能失效。")
        }
    }

    companion object {
        @Suppress("DEPRECATION")
        val sPrefs
            get() = currentContext.getSharedPreferences("biliroaming", MODE_MULTI_PROCESS)!!
        val currentContext by lazy { AndroidAppHelper.currentApplication() as Context }
        private val handler by lazy { Handler(Looper.getMainLooper()) }
        private val toast by lazy { Toast.makeText(currentContext, "", Toast.LENGTH_SHORT) }
        lateinit var modulePath: String
        lateinit var moduleRes: Resources

        fun toastMessage(msg: String) {
            if (!sPrefs.getBoolean("show_info", true)) return
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