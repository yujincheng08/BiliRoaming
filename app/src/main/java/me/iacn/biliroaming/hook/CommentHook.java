package me.iacn.biliroaming.hook;

import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import me.iacn.biliroaming.XposedInit;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setIntField;
import static me.iacn.biliroaming.Constant.TAG;

/**
 * Created by iAcn on 2020/2/27
 * Email i@iacn.me
 */
public class CommentHook extends BaseHook {

    public CommentHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public void startHook() {
        if (!XposedInit.sPrefs.getBoolean("comment_floor", false)) return;
        Log.d(TAG, "startHook: Comment");

        XC_MethodHook floorHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Object config = getObjectField(param.thisObject, "config");
                if (config != null)
                    setIntField(config, "mShowFloor", 1);
            }
        };

        findAndHookMethod("com.bilibili.app.comm.comment2.model.BiliCommentList",
                mClassLoader, "isShowFloor", floorHook);
        findAndHookMethod("com.bilibili.app.comm.comment2.model.BiliCommentCursorList",
                mClassLoader, "isShowFloor", floorHook);
        findAndHookMethod("com.bilibili.app.comm.comment2.model.BiliCommentDialogue",
                mClassLoader, "isShowFloor", floorHook);
        findAndHookMethod("com.bilibili.app.comm.comment2.model.BiliCommentDetail",
                mClassLoader, "isShowFloor", floorHook);
    }
}