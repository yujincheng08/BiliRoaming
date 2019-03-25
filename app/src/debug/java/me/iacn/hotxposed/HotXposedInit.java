package me.iacn.hotxposed;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.io.File;

import dalvik.system.PathClassLoader;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import me.iacn.biliroaming.BuildConfig;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

/**
 * Created by iAcn on 2019/3/25
 * Email i@iacn.me
 */
public class HotXposedInit implements IXposedHookLoadPackage {

    private static final String HOST_PACKAGE = "tv.danmaku.bili";
    private static final String REAL_XPOSED_INIT = "me.iacn.biliroaming.XposedInit";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        disableModulesUpdatedNotification(lpparam);

        if (!HOST_PACKAGE.equals(lpparam.packageName)) return;

        File moduleApkFile = getModuleApkFile();
        if (!moduleApkFile.exists()) return;

        PathClassLoader classLoader =
                new PathClassLoader(moduleApkFile.getAbsolutePath(), lpparam.getClass().getClassLoader());
        Class<?> xposedInitClass = classLoader.loadClass(REAL_XPOSED_INIT);
        if (xposedInitClass != null) {
            callMethod(xposedInitClass.newInstance(), "handleLoadPackage", lpparam);
        }
    }

    private void disableModulesUpdatedNotification(LoadPackageParam lpparam) {
        if ("de.robv.android.xposed.installer".equals(lpparam.packageName)) {
            findAndHookMethod("de.robv.android.xposed.installer.util.NotificationUtil", lpparam.classLoader,
                    "showModulesUpdatedNotification", XC_MethodReplacement.DO_NOTHING);
        }
    }

    private File getModuleApkFile() throws PackageManager.NameNotFoundException {
        Object activityThread = callStaticMethod(findClass("android.app.ActivityThread", null), "currentActivityThread");
        Context context = (Context) callMethod(activityThread, "getSystemContext");
        ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(BuildConfig.APPLICATION_ID, 0);
        return new File(applicationInfo.sourceDir);
    }
}