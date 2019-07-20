package me.iacn.biliroaming;

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
import me.iacn.biliroaming.hooker.CustomThemeHook;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static me.iacn.biliroaming.Constant.BILIBILI_PACKAGENAME;
import static me.iacn.biliroaming.Constant.TAG;


/**
 * Created by iAcn on 2019/3/24
 * Email i@iacn.me
 */
public class XposedInit implements IXposedHookLoadPackage {

    public static XSharedPreferences sPrefs;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (BuildConfig.APPLICATION_ID.equals(lpparam.packageName)) {
            findAndHookMethod(MainActivity.class.getName(), lpparam.classLoader,
                    "isModuleActive", XC_MethodReplacement.returnConstant(true));
        }

        if (!BILIBILI_PACKAGENAME.equals(lpparam.packageName)) return;

        sPrefs = new XSharedPreferences(BuildConfig.APPLICATION_ID);

        findAndHookMethod(Instrumentation.class, "callApplicationOnCreate", Application.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // Hook main process and download process
                switch (lpparam.processName) {
                    case "tv.danmaku.bili":
                        Log.d(TAG, "BiliBili process launched ...");
                        BiliBiliPackage.getInstance().init(lpparam.classLoader, (Context) param.args[0]);
                        new BangumiSeasonHook(lpparam.classLoader).startHook();
                        new CustomThemeHook(lpparam.classLoader).startHook();
                        break;
                    case "tv.danmaku.bili:download":
                        new BangumiPlayUrlHook(lpparam.classLoader).startHook();
                        break;
                    case "tv.danmaku.bili:web":
                        new CustomThemeHook(lpparam.classLoader).insertColorForWebProcess();
                        break;
                }
            }
        });
    }
}