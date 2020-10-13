package me.iacn.hotxposed

import dalvik.system.PathClassLoader
import de.robv.android.xposed.IXposedHookInitPackageResources
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.IXposedHookZygoteInit.StartupParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_InitPackageResources
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import me.iacn.biliroaming.BuildConfig
import me.iacn.biliroaming.Constant
import me.iacn.biliroaming.utils.*
import java.io.File


/**
 * Created by iAcn on 2019/3/25
 * Email i@iacn.me
 */
class HotXposedInit : IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources {

    override fun initZygote(startupParam: StartupParam) {
        modulePath = startupParam.modulePath
    }

    companion object {
        private const val REAL_XPOSED_INIT = "me.iacn.biliroaming.XposedInit"
        lateinit var modulePath: String

        val moduleApkFile: File
            get() {
                return try {
                    val applicationInfo = systemContext.packageManager.getApplicationInfo(BuildConfig.APPLICATION_ID, 0)
                    File(applicationInfo.sourceDir)
                } catch (e: Throwable) {
                    null
                } ?: File(modulePath)
            }

        val moduleInstance: Any?
            get() {
                moduleApkFile.let {
                    if (!it.exists()) return null
                    val classLoader = PathClassLoader(it.absolutePath, XposedBridge.BOOTCLASSLOADER)
                    return classLoader.loadClass(REAL_XPOSED_INIT)?.new()
                }
            }
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (!Constant.BILIBILI_PACKAGENAME.containsValue(lpparam.packageName) && lpparam.packageName != BuildConfig.APPLICATION_ID) return

        hookSSL(lpparam.classLoader)

        moduleInstance?.run {
            val param = StartupParam::class.java.new() as StartupParam
            param.modulePath = moduleApkFile.absolutePath
            param.startsSystemServer = false
            try {
                callMethod("initZygote", param)
            } catch (e: Throwable) {
            }
            try {
                callMethod("handleLoadPackage", lpparam)
            } catch (e: Throwable) {
            }
        }
    }

    override fun handleInitPackageResources(resparam: XC_InitPackageResources.InitPackageResourcesParam) {
        if (!Constant.BILIBILI_PACKAGENAME.containsValue(resparam.packageName)) return
        try {
            moduleInstance?.callMethod("handleInitPackageResources", resparam)
        } catch (e: Throwable) {
        }
    }
}

