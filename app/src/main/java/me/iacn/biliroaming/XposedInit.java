package me.iacn.biliroaming;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import me.iacn.biliroaming.hooker.BangumiPlayUrlHook;
import me.iacn.biliroaming.hooker.BangumiSeasonHook;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;


/**
 * Created by iAcn on 2019/3/24
 * Email i@iacn.me
 */
public class XposedInit implements IXposedHookLoadPackage {

    private static final String TAG = "BiliRoaming";
    private XSharedPreferences mPrefs;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (BuildConfig.APPLICATION_ID.equals(lpparam.packageName)) {
            findAndHookMethod(MainActivity.class.getName(), lpparam.classLoader,
                    "isModuleActive", XC_MethodReplacement.returnConstant(true));
        }

        if (!"tv.danmaku.bili".equals(lpparam.packageName)) return;

        mPrefs = new XSharedPreferences(BuildConfig.APPLICATION_ID);

        findAndHookMethod(Instrumentation.class, "callApplicationOnCreate", Application.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                boolean enableMainFunc = mPrefs.getBoolean("main_func", false);
                Log.d(TAG, "BiliBili process launched: enableMainFunc = " + enableMainFunc);

                if (!enableMainFunc) return;

                Context context = (Context) param.args[0];
                String currentProcessName = getCurrentProcessName(context);

                // Hook main process and download process
                if ("tv.danmaku.bili".equals(currentProcessName)) {
                    BiliBiliPackage.getInstance().init(lpparam.classLoader, context);
                    new BangumiSeasonHook(lpparam.classLoader).startHook();
                } else if ("tv.danmaku.bili:download".equals(currentProcessName)) {
                    new BangumiPlayUrlHook(lpparam.classLoader).startHook();
                }
            }
        });
    }

    private String getCurrentProcessName(Context context) {
        int myPid = android.os.Process.myPid();
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningAppProcessInfo processInfo : manager.getRunningAppProcesses()) {
            if (processInfo.pid == myPid) {
                return processInfo.processName;
            }
        }
        return null;
    }
}