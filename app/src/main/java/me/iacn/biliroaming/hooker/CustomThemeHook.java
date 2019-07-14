package me.iacn.biliroaming.hooker;

import android.app.Activity;
import android.graphics.Color;
import android.util.Log;
import android.util.SparseArray;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import me.iacn.biliroaming.XposedInit;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;
import static de.robv.android.xposed.XposedHelpers.setBooleanField;
import static de.robv.android.xposed.XposedHelpers.setIntField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;
import static me.iacn.biliroaming.Constant.TAG;

/**
 * Created by iAcn on 2019/7/14
 * Email i@iacn.me
 */
public class CustomThemeHook extends BaseHook {

    private static final int CUSTOM_THEME_ID = 114514;  // ん？

    public CustomThemeHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public void startHook() {
        if (!XposedInit.sPrefs.getBoolean("custom_theme", false)) return;
        Log.d(TAG, "startHook: CustomTheme");

        Class<?> helperClass = findClass("tv.danmaku.bili.ui.theme.a", mClassLoader);
        SparseArray<int[]> colorArray = (SparseArray<int[]>) getStaticObjectField(helperClass, "l");

        int[] array = {Color.RED, Color.GREEN, Color.BLUE, Color.BLACK};
        colorArray.put(CUSTOM_THEME_ID, array);

        findAndHookMethod("tv.danmaku.bili.ui.theme.ThemeStoreActivity", mClassLoader, "a",
                "tv.danmaku.bili.ui.theme.api.BiliSkinList", boolean.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object biliSkinList = param.args[0];
                        List mList = (List) getObjectField(biliSkinList, "mList");

                        if (mList != null) {
                            Class<?> biliSkinClass = findClass("tv.danmaku.bili.ui.theme.api.BiliSkin", mClassLoader);
                            Object biliSkin = biliSkinClass.newInstance();

                            setIntField(biliSkin, "mId", CUSTOM_THEME_ID);
                            setObjectField(biliSkin, "mName", "自选颜色");
                            setBooleanField(biliSkin, "mIsFree", true);
                            // Under the night mode item
                            mList.add(3, biliSkin);
                        }
                    }
                });

        findAndHookMethod(helperClass, "a", Activity.class, XC_MethodReplacement.DO_NOTHING);
    }
}