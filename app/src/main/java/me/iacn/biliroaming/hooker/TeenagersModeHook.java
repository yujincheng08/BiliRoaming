package me.iacn.biliroaming.hooker;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import me.iacn.biliroaming.XposedInit;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static me.iacn.biliroaming.Constant.TAG;

/**
 * Created by iAcn on 2019/12/15
 * Email i@iacn.me
 */
public class TeenagersModeHook extends BaseHook {

    public TeenagersModeHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public void startHook() {
        if (!XposedInit.sPrefs.getBoolean("teenagers_mode_dialog", false)) return;
        Log.d(TAG, "startHook: TeenagersMode");

        findAndHookMethod("com.bilibili.teenagersmode.ui.TeenagersModeDialogActivity",
                mClassLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        activity.finish();
                        Log.d(TAG, "Teenagers mode dialog has been closed");
                    }
                });
    }
}