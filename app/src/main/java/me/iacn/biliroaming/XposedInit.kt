package me.iacn.biliroaming

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.util.Log
import android.widget.Toast
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import me.iacn.biliroaming.Constant.TAG
import me.iacn.biliroaming.hook.*


/**
 * Created by iAcn on 2019/3/24
 * Email i@iacn.me
 */
class XposedInit : IXposedHookLoadPackage {
    @Throws(Throwable::class)
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (BuildConfig.APPLICATION_ID == lpparam.packageName) {
            XposedHelpers.findAndHookMethod(MainActivity.Companion::class.java.name, lpparam.classLoader,
                    "isModuleActive", XC_MethodReplacement.returnConstant(true))
        }
        if (Constant.BILIBILI_PACKAGENAME != lpparam.packageName
                && Constant.BILIBILI_PACKAGENAME2 != lpparam.packageName
                && Constant.BILIBILI_PACKAGENAME3 != lpparam.packageName) return
        sPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID)
        sPrefs.makeWorldReadable()
        XposedHelpers.findAndHookMethod(Instrumentation::class.java, "callApplicationOnCreate", Application::class.java, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                // Hook main process and download process
                when (lpparam.processName) {
                    "tv.danmaku.bili", "com.bilibili.app.blue", "com.bilibili.app.in" -> {
                        Log.d(Constant.TAG, "BiliBili process launched ...")
                        Log.d(TAG, "Config: ${sPrefs.all}")
                        BiliBiliPackage.instance?.init(lpparam.classLoader, param.args[0] as Context)
                        BangumiSeasonHook(lpparam.classLoader).startHook()
                        BangumiPlayUrlHook(lpparam.classLoader).startHook()
                        CustomThemeHook(lpparam.classLoader).startHook()
                        TeenagersModeHook(lpparam.classLoader).startHook()
                        CommentHook(lpparam.classLoader).startHook()
                        JsonHook(lpparam.classLoader).startHook()
                        CDNHook(lpparam.classLoader).startHook()
                        (object : BaseHook(lpparam.classLoader) {
                            override fun startHook() {
                                XposedBridge.hookAllMethods(XposedHelpers.findClass(
                                        "android.app.Instrumentation", lpparam.classLoader),
                                        "newActivity", object : XC_MethodHook() {
                                    override fun afterHookedMethod(param: MethodHookParam) {
                                        currentActivity = param.result as Activity
                                        if (!started) {
                                            toastMessage("哔哩漫游已生效")
                                            started = true
                                        }
                                    }
                                })
                            }
                        }).startHook()
                    }
                    "tv.danmaku.bili:web", "com.bilibili.app.in:web", "com.bilibili.app.blue:web" -> {
                        CustomThemeHook(lpparam.classLoader).insertColorForWebProcess()
                    }
                    "com.bilibili.app.in:download", "com.bilibili.app.blue:download", "tv.danmaku.bili:download" -> {
                        BangumiPlayUrlHook(lpparam.classLoader).startHook()
                        CDNHook(lpparam.classLoader).startHook()
                    }
                }
            }
        })
    }

    companion object {
        var sPrefs: XSharedPreferences = XSharedPreferences(BuildConfig.APPLICATION_ID)
        var currentActivity: Activity? = null
        var toast: Toast? = null
        var started = false
        fun toastMessage(msg: String, new: Boolean = false) {
            if (sPrefs.getBoolean("show_info", false)) {
                currentActivity?.runOnUiThread {
                    if (new || toast == null) {
                        toast = Toast.makeText(currentActivity, msg, Toast.LENGTH_SHORT)
                    }
                    toast?.setText("哔哩漫游：$msg")
                    toast?.duration = Toast.LENGTH_SHORT
                    toast?.show()
                }
            }
        }
    }
}