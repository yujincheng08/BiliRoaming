package me.iacn.biliroaming

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.res.Resources
import android.content.res.XModuleResources
import android.os.Build
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.future.future
import me.iacn.biliroaming.hook.*
import me.iacn.biliroaming.utils.*
import java.util.concurrent.CompletableFuture


/**
 * Created by iAcn on 2019/3/24
 * Email i@iacn.me
 */
class XposedInit : IXposedHookLoadPackage, IXposedHookZygoteInit {
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
        moduleRes = getModuleRes(modulePath)
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (BuildConfig.APPLICATION_ID == lpparam.packageName) {
            MainActivity.Companion::class.java.name.replaceMethod(
                lpparam.classLoader,
                "isModuleActive"
            ) { true }
            return
        }
        if (!Constant.BILIBILI_PACKAGE_NAME.containsValue(lpparam.packageName) &&
            "tv.danmaku.bili.MainActivityV2".findClassOrNull(lpparam.classLoader) == null
        ) return
        Instrumentation::class.java.hookBeforeMethod(
            "callApplicationOnCreate",
            Application::class.java
        ) { param ->
            // Hook main process and download process
            when {
                !lpparam.processName.contains(":") -> {
                    if (shouldSaveLog) {
                        startLog()
                    }
                    Log.d("BiliBili process launched ...")
                    Log.d("BiliRoaming version: ${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) from $modulePath${if (isBuiltIn) "(BuiltIn)" else ""}")
                    Log.d("Bilibili version: ${getPackageVersion(lpparam.packageName)} (${if (is64) "64" else "32"}bit)")
                    Log.d("SDK: ${Build.VERSION.RELEASE}(${Build.VERSION.SDK_INT}); Phone: ${Build.BRAND} ${Build.MODEL}")
                    Log.d("Config: ${sPrefs.all}")
                    Log.toast(
                        "哔哩漫游已激活${
                            if (sPrefs.getBoolean("main_func", false) &&
                                (!sPrefs.getString("hk_server", null).isNullOrEmpty() ||
                                        !sPrefs.getString("th_server", null).isNullOrEmpty() ||
                                        !sPrefs.getString("tw_server", null).isNullOrEmpty() ||
                                        !sPrefs.getString("cn_server", null).isNullOrEmpty())
                            ) ""
                            else "。\n但未启用番剧解锁功能，请检查解析服务器设置。"
                        }\n请勿在B站任何地方宣传漫游。\n漫游插件开源免费，谨防被骗。"
                    )

                    country = MainScope().future(Dispatchers.IO) {
                        when (fetchJson(Constant.zoneUrl)?.optJSONObject("data")?.optInt("country_code", 0)?.or(0)) {
                            86 -> "cn"
                            852, 853 -> "hk"
                            886 -> "tw"
                            else -> "global"
                        }.also { Log.d("当前地区: $it") }
                    }

                    BiliBiliPackage(lpparam.classLoader, param.args[0] as Context)
                    if (BuildConfig.DEBUG) {
                        startHook(SSLHook(lpparam.classLoader))
                    }
                    startHook(KillDelayBootHook(lpparam.classLoader))
                    startHook(HintHook(lpparam.classLoader))
                    startHook(BangumiSeasonHook(lpparam.classLoader))
                    startHook(BangumiPlayUrlHook(lpparam.classLoader))
                    startHook(PegasusHook(lpparam.classLoader))
                    startHook(CustomThemeHook(lpparam.classLoader))
                    startHook(TeenagersModeHook(lpparam.classLoader))
                    startHook(JsonHook(lpparam.classLoader))
                    startHook(ShareHook(lpparam.classLoader))
                    startHook(AutoLikeHook(lpparam.classLoader))
                    startHook(SettingHook(lpparam.classLoader))
                    startHook(SplashHook(lpparam.classLoader))
                    startHook(EnvHook(lpparam.classLoader))
                    startHook(DownloadThreadHook(lpparam.classLoader))
                    startHook(MusicNotificationHook(lpparam.classLoader))
                    startHook(DrawerHook(lpparam.classLoader))
                    startHook(CoverHook(lpparam.classLoader))
                    startHook(SubtitleHook(lpparam.classLoader))
                    startHook(CopyHook(lpparam.classLoader))
                    startHook(LiveRoomHook(lpparam.classLoader))
                    startHook(QualityHook(lpparam.classLoader))
                    startHook(DynamicHook(lpparam.classLoader))
                    startHook(ProtoBufHook(lpparam.classLoader))
                    startHook(PlayArcConfHook(lpparam.classLoader))
                    startHook(TryWatchVipQualityHook(lpparam.classLoader))
                    startHook(AllowMiniPlayHook(lpparam.classLoader))
                    startHook(StartActivityHook(lpparam.classLoader))
                    startHook(FullStoryHook(lpparam.classLoader))
                    startHook(DialogBlurBackgroundHook(lpparam.classLoader))
                    startHook(PlayerLongPressHook(lpparam.classLoader))
                    startHook(BlockUpdateHook(lpparam.classLoader))
                    startHook(VipSectionHook(lpparam.classLoader))
                    startHook(CommentImageHook(lpparam.classLoader))
                    startHook(WebViewHook(lpparam.classLoader))
                    startHook(P2pHook(lpparam.classLoader))
                    startHook(DanmakuHook(lpparam.classLoader))
                    startHook(BangumiPageAdHook(lpparam.classLoader))
                    startHook(VideoQualityHook(lpparam.classLoader))
                    // startHook(PublishToFollowingHook(lpparam.classLoader))
                    startHook(UposReplaceHook(lpparam.classLoader))
                    startHook(SpeedHook(lpparam.classLoader))
                    startHook(MultiWindowHook(lpparam.classLoader))
                }

                lpparam.processName.endsWith(":web") -> {
                    BiliBiliPackage(lpparam.classLoader, param.args[0] as Context)
                    CustomThemeHook(lpparam.classLoader).insertColorForWebProcess()
                    startHook(WebViewHook(lpparam.classLoader))
                    startHook(ShareHook(lpparam.classLoader))
                    startHook(DialogBlurBackgroundHook(lpparam.classLoader))
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
            Log.toast("出现错误\n${e.message}\n部分功能可能失效。${e.stackTrace.joinToString("\n")}")
        }
    }

    private fun startLateHook() {
        hookers.forEach {
            try {
                it.lateInitHook()
            } catch (e: Throwable) {
                Log.e(e)
                Log.toast("出现错误\n${e.message}\n部分功能可能失效。${e.stackTrace.joinToString("\n")}")
            }
        }
    }

    private fun startLog() = try {
        if (logFile.exists()) {
            if (oldLogFile.exists()) {
                oldLogFile.delete()
            }
            logFile.renameTo(oldLogFile)
        }
        logFile.delete()
        logFile.createNewFile()
        Runtime.getRuntime().exec(
            arrayOf(
                "logcat",
                "-T",
                "100",
                "-f",
                logFile.absolutePath
            )
        )
    } catch (e: Throwable) {
        Log.e(e)
        null
    }

    companion object {
        lateinit var modulePath: String
        lateinit var moduleRes: Resources
        lateinit var country: CompletableFuture<String>

        private val hookers = ArrayList<BaseHook>()
        private var lateInitHook: XC_MethodHook.Unhook? = null


        @JvmStatic
        fun getModuleRes(path: String): Resources {
            return XModuleResources.createInstance(path, null)
        }
    }
}
