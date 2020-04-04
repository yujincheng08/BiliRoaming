package me.iacn.biliroaming.hook

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import me.iacn.biliroaming.Constant.TAG
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.XposedInit.Companion.toastMessage

/**
 * Created by iAcn on 2019/12/15
 * Email i@iacn.me
 */
class TeenagersModeHook(classLoader: ClassLoader?) : BaseHook(classLoader!!) {
    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("teenagers_mode_dialog", false)) return
        Log.d(TAG, "startHook: TeenagersMode")
        XposedHelpers.findAndHookMethod("com.bilibili.teenagersmode.ui.TeenagersModeDialogActivity",
                mClassLoader, "onCreate", Bundle::class.java, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as Activity
                activity.finish()
                Log.d(TAG, "Teenagers mode dialog has been closed")
                toastMessage("已关闭青少年模式对话框")
            }
        })
    }
}