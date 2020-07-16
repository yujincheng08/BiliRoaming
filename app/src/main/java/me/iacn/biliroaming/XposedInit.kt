package me.iacn.biliroaming

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AndroidAppHelper
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.Context.MODE_MULTI_PROCESS
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import android.widget.Toast
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import me.iacn.biliroaming.hook.*
import me.iacn.biliroaming.utils.Log


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
            XposedHelpers.findAndHookMethod(MainActivity.Companion::class.java.name, lpparam.classLoader,
                    "isModuleActive", XC_MethodReplacement.returnConstant(true))
        }
        if (!Constant.BILIBILI_PACKAGENAME.contains(lpparam.packageName)) return
        XposedHelpers.findAndHookMethod(Instrumentation::class.java, "callApplicationOnCreate", Application::class.java, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                // Hook main process and download process
                @Suppress("DEPRECATION")
                sPrefs = AndroidAppHelper.currentApplication().getSharedPreferences("biliroaming", MODE_MULTI_PROCESS)
                when (lpparam.processName) {
                    "tv.danmaku.bili", "com.bilibili.app.blue", "com.bilibili.app.in" -> {
                        Log.d("BiliBili process launched ...")
                        Log.d("Config: ${sPrefs.all}")
                        startHook(object : BaseHook(lpparam.classLoader) {
                            override fun startHook() {
                                XposedBridge.hookAllMethods(XposedHelpers.findClass(
                                        "android.app.Instrumentation", lpparam.classLoader),
                                        "newActivity", object : XC_MethodHook() {
                                    override fun afterHookedMethod(param: MethodHookParam) {
                                        currentActivity = param.result as Activity
                                        if (!started) {
                                            if (!sPrefs.getBoolean("main_func", false)) {
                                                toastMessage("哔哩漫游已激活，但未启用番剧解锁功能")
                                            } else {
                                                toastMessage("哔哩漫游已激活")
                                            }
                                            started = true
                                        }
                                    }
                                })
                            }
                        })
                        BiliBiliPackage(lpparam.classLoader, param.args[0] as Context)
                        startHook(BangumiSeasonHook(lpparam.classLoader))
                        startHook(BangumiPlayUrlHook(lpparam.classLoader))
                        startHook(CustomThemeHook(lpparam.classLoader))
                        startHook(TeenagersModeHook(lpparam.classLoader))
                        startHook(CommentHook(lpparam.classLoader))
                        startHook(JsonHook(lpparam.classLoader))
                        startHook(CDNHook(lpparam.classLoader))
                        startHook(MiniProgramHook(lpparam.classLoader))
                        startHook(AutoLikeHook(lpparam.classLoader))
                        startHook(SettingHook(lpparam.classLoader))
                    }
                    "tv.danmaku.bili:web", "com.bilibili.app.in:web", "com.bilibili.app.blue:web" -> {
                        BiliBiliPackage(lpparam.classLoader, param.args[0] as Context)
                        CustomThemeHook(lpparam.classLoader).insertColorForWebProcess()
                    }
                    "tv.danmaku.bili:download", "com.bilibili.app.in:download", "com.bilibili.app.blue:download" -> {
                        BiliBiliPackage(lpparam.classLoader, param.args[0] as Context)
                        startHook(CDNHook(lpparam.classLoader))
                    }
                }
            }
        })
    }

    private fun startHook(hooker: BaseHook) {
        try {
            hooker.startHook()
        } catch (e: Throwable) {
            Log.e(e)
            toastMessage("出现错误，部分功能可能失效。")
        }
    }

    companion object {
        lateinit var sPrefs: SharedPreferences
        lateinit var currentActivity: Activity
        private val toast by lazy { Toast.makeText(currentActivity, "", Toast.LENGTH_SHORT) }
        var started = false
        lateinit var modulePath: String
        lateinit var moduleRes: Resources

        fun toastMessage(msg: String) {
            if (sPrefs.getBoolean("show_info", true)) {
                currentActivity.runOnUiThread {
                    toast.setText("哔哩漫游：$msg")
                    toast.duration = Toast.LENGTH_SHORT
                    toast.show()
                }
            }
        }

        @Suppress("DEPRECATION")
        @SuppressLint("DiscouragedPrivateApi")
        @JvmStatic
        fun getModuleRes(path: String): Resources {
            val assetManager = AssetManager::class.java.newInstance()
            val addAssetPath = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
            addAssetPath.invoke(assetManager, path)
            val metrics = DisplayMetrics()
            metrics.setToDefaults()
            val config = Configuration()
            config.setToDefaults()
            return Resources(assetManager, metrics, config)
        }
    }
}