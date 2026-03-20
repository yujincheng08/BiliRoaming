package me.iacn.biliroaming

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Build
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.*
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
class XposedInit : XposedModule() {

    private lateinit var processName: String

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        modulePath = getModuleApplicationInfo().sourceDir
        moduleRes = getModuleRes(modulePath)
        instance = this
        processName = param.processName
    }

    override fun onPackageReady(param: PackageReadyParam) {
        val packageName = param.packageName
        val classLoader = param.classLoader
        if (BuildConfig.APPLICATION_ID == packageName) {
            MainActivity.Companion::class.java.name.hookMethod(
                classLoader,
                "isModuleActive"
            ) { true }
            return
        }
        if (!Constant.BILIBILI_PACKAGE_NAME.containsValue(packageName) &&
            "tv.danmaku.bili.MainActivityV2".findClassOrNull(classLoader) == null
        ) return
        Instrumentation::class.java.hookMethod(
            "callApplicationOnCreate",
            Application::class.java
        ) { chain ->
            // Hook main process and download process
            when {
                !processName.contains(":") -> {
                    if (shouldSaveLog) {
                        startLog()
                    }
                    Log.d("BiliBili process launched ...")
                    Log.d("BiliRoaming version: ${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) from $modulePath${if (isBuiltIn) "(BuiltIn)" else ""}")
                    Log.d("Bilibili version: ${getPackageVersion(packageName)} (${if (is64) "64" else "32"}bit)")
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

                    BiliBiliPackage(classLoader, chain.args[0] as Context)
                    if (BuildConfig.DEBUG) {
                        startHook { SSLHook(classLoader) }
                    }
                    startHook { KillDelayBootHook(classLoader) }
                    startHook { HintHook(classLoader) }
                    startHook { BangumiSeasonHook(classLoader) }
                    startHook { BangumiPlayUrlHook(classLoader) }
                    startHook { PegasusHook(classLoader) }
                    startHook { CustomThemeHook(classLoader) }
                    startHook { TeenagersModeHook(classLoader) }
                    startHook { JsonHook(classLoader) }
                    startHook { ShareHook(classLoader) }
                    startHook { AutoLikeHook(classLoader) }
                    startHook { SettingHook(classLoader) }
                    startHook { SplashHook(classLoader) }
                    startHook { EnvHook(classLoader) }
                    startHook { DownloadThreadHook(classLoader) }
                    startHook { MusicNotificationHook(classLoader) }
                    startHook { DrawerHook(classLoader) }
                    startHook { CoverHook(classLoader) }
                    startHook { SubtitleHook(classLoader) }
                    startHook { CopyHook(classLoader) }
                    startHook { CopyCommentHook(classLoader) }
                    startHook { LiveRoomHook(classLoader) }
                    startHook { QualityHook(classLoader) }
                    startHook { DynamicHook(classLoader) }
                    startHook { ProtoBufHook(classLoader) }
                    startHook { PlayArcConfHook(classLoader) }
                    startHook { TryWatchVipQualityHook(classLoader) }
                    startHook { AllowMiniPlayHook(classLoader) }
                    startHook { StartActivityHook(classLoader) }
                    startHook { FullStoryHook(classLoader) }
                    startHook { DialogBlurBackgroundHook(classLoader) }
                    startHook { PlayerLongPressHook(classLoader) }
                    startHook { BlockUpdateHook(classLoader) }
                    startHook { VipSectionHook(classLoader) }
                    startHook { CommentImageHook(classLoader) }
                    startHook { WebViewHook(classLoader) }
                    startHook { P2pHook(classLoader) }
                    startHook { DanmakuHook(classLoader) }
                    startHook { BangumiPageAdHook(classLoader) }
                    startHook { VideoQualityHook(classLoader) }
                    // startHook(PublishToFollowingHook(classLoader))
                    startHook { UposReplaceHook(classLoader) }
                    startHook { SpeedHook(classLoader) }
                    startHook { MultiWindowHook(classLoader) }
                    startHook { LiveQualityHook(classLoader) }
                    startHook { StoryPlayerAdHook(classLoader) }
                    startHook { LongPressSpeed(classLoader) }
                }

                processName.endsWith(":web") -> {
                    BiliBiliPackage(classLoader, chain.args[0] as Context)
                    CustomThemeHook(classLoader).insertColorForWebProcess()
                    startHook { WebViewHook(classLoader) }
                    startHook { ShareHook(classLoader) }
                    startHook { DialogBlurBackgroundHook(classLoader) }
                    startHook { RewardAdHook(classLoader) }
                }

                processName.endsWith(":download") -> {
                    BiliBiliPackage(classLoader, chain.args[0] as Context)
                    startHook { BangumiPlayUrlHook(classLoader) }
                }
            }
            chain.proceed()
        }
        lateInitHook = Activity::class.java.hookMethod("onResume") { chain ->
            startLateHook()
            lateInitHook?.unhook()
            chain.proceed()
        }
    }

    private fun startHook(hookerCreater: () -> BaseHook) {
        try {
            val hooker = hookerCreater()
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
        lateinit var instance: XposedInit

        private val hookers = ArrayList<BaseHook>()
        private var lateInitHook: HookHandle? = null

        @JvmStatic
        @Suppress("DiscouragedPrivateApi", "DEPRECATION")
        fun getModuleRes(path: String): Resources {
            val assetManager = AssetManager::class.java.getDeclaredConstructor().newInstance()
            AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
                .invoke(assetManager, path)
            return Resources(assetManager, null, null)
        }
    }
}
