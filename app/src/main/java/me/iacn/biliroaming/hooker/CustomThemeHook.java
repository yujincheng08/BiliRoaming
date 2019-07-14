package me.iacn.biliroaming.hooker;

import android.graphics.Color;
import android.util.Log;
import android.util.SparseArray;

import me.iacn.biliroaming.XposedInit;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;
import static me.iacn.biliroaming.Constant.TAG;

/**
 * Created by iAcn on 2019/7/14
 * Email i@iacn.me
 */
public class CustomThemeHook extends BaseHook {


    public CustomThemeHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public void startHook() {
        if (!XposedInit.sPrefs.getBoolean("custom_theme", false)) return;
        Log.d(TAG, "startHook: CustomTheme");


    }
}