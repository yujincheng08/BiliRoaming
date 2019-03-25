package me.iacn.biliroaming;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;

import net.androidwing.hotxposed.IHookerDispatcher;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;


/**
 * Created by iAcn on 2019/3/24
 * Email i@iacn.me
 */
public class XposedInit implements IHookerDispatcher {
    @Override
    public void dispatch(LoadPackageParam lpparam) {
        if (!"tv.danmaku.bili".equals(lpparam.packageName)) return;

        findAndHookMethod(Instrumentation.class, "callApplicationOnCreate", Application.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Context context = (Context) param.args[0];
                // Only hook on the main process
                if (isMainProcess(context)) {

                    // ============== START ==============
                    System.out.println("============== START ==============");

                    try {
                        XposedHelpers.findAndHookMethod("b.efu", lpparam.classLoader,
                                "a", "okhttp3.t", new XC_MethodHook() {
                                    @Override
                                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                        // get OkHttpClientBuilder object
                                        Object builder = XposedHelpers.callMethod(param.thisObject, "e");
                                        List interceptors = (List) XposedHelpers.callMethod(builder, "a");

                                        boolean hasAdded = false;
                                        for (Object interceptor : interceptors) {
                                            String simpleName = interceptor.getClass().getSimpleName();
                                            if (simpleName.contains("$Proxy")) {
                                                hasAdded = true;
                                                break;
                                            }
                                        }

                                        if (!hasAdded) {
                                            Class<?> InterceptorInterface = XposedHelpers.findClass(
                                                    "okhttp3.t", lpparam.classLoader);
                                            Method addInterceptorMethod = XposedHelpers.findMethodExact(builder.getClass(),
                                                    "a", InterceptorInterface);
                                            Object instance = Proxy.newProxyInstance(lpparam.classLoader, new Class[]{
                                                    InterceptorInterface}, new BangumiInterceptor2(lpparam.classLoader));

                                            addInterceptorMethod.invoke(builder, instance);
                                        }
                                    }
                                });
                    } catch (Exception e) {
                        e.printStackTrace();
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