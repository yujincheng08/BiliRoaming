package me.iacn.biliroaming;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;

import java.lang.reflect.Method;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;


/**
 * Created by iAcn on 2019/3/24
 * Email i@iacn.me
 */
public class XposedInit implements IXposedHookLoadPackage {

    private static final String TAG = "BiliRoaming";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!"tv.danmaku.bili".equals(lpparam.packageName)) return;

        findAndHookMethod(Instrumentation.class, "callApplicationOnCreate", Application.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Context context = (Context) param.args[0];
                // Only hook on the main process
                if (isMainProcess(context)) {

                    // ============== START ==============
                    System.out.println("============== START ==============");

                    Class<?> activityClass = findClass(
                            "com.bilibili.bangumi.ui.detail.BangumiDetailActivity", lpparam.classLoader);
                    Class<?> seasonClass = findClass(
                            "com.bilibili.bangumi.api.uniform.BangumiUniformSeason", lpparam.classLoader);

                    findAndHookMethod(activityClass, "a", seasonClass, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Object bangumiSeason = param.args[0];
                            List episodes = (List) getObjectField(bangumiSeason, "episodes");
                            Object rights = getObjectField(bangumiSeason, "rights");
                            boolean areaLimit = getBooleanField(rights, "areaLimit");

                            if (areaLimit && episodes.size() == 0) {
                                /*System.out.println("Limited Bangumi: seasonTitle = " +
                                        getObjectField(bangumiSeason, "seasonTitle") +
                                        ", seasonId = " + getObjectField(bangumiSeason, "seasonId"));*/
                            }
                        }
                    });

//                    Class<?> clazz = findClass("com.bilibili.bangumi.viewmodel.detail.BangumiDetailViewModel", lpparam.classLoader);
                    for (Method method : activityClass.getDeclaredMethods()) {
                        XposedBridge.hookMethod(method, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                System.out.println("--------------" + param.method.getName() + "--------------");

                                if (param.method.getName().equals("onEvent")) {
                                    System.out.println("param = " + param.args[0]);
                                    Object[] objArr = (Object[]) param.args[1];
                                    for (int i = 0; i < objArr.length; i++) {
                                        System.out.println("param = " + objArr[i]);
                                    }

                                } else {
                                    for (int i = 0; i < param.args.length; i++) {
                                        System.out.println("param = " + param.args[i]);
                                    }
                                }
                            }

                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                System.out.println("Return = " + param.getResult());
                            }
                        });
                    }

                    // ============== END ==============
                }
            }
        });
    }

    private boolean isMainProcess(Context context) {
        int myPid = android.os.Process.myPid();
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningAppProcessInfo processInfo : manager.getRunningAppProcesses()) {
            if (processInfo.pid == myPid && "tv.danmaku.bili".equals(processInfo.processName)) {
                return true;
            }
        }
        return false;
    }
}