package me.iacn.biliroaming.hook

import android.app.Activity
import android.os.Bundle
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookAfterMethod
import me.iacn.biliroaming.utils.sPrefs

/**
 * Created by iAcn on 2019/12/15
 * Email i@iacn.me
 */
class TeenagersModeHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("teenagers_mode_dialog", false)) return
        Log.d("startHook: TeenagersMode")
        "com.bilibili.teenagersmode.ui.TeenagersModeDialogActivity".hookAfterMethod(
                mClassLoader, "onCreate", Bundle::class.java) { param ->
            val activity = param.thisObject as Activity
            activity.finish()
            Log.d("Teenagers mode dialog has been closed")
            Log.toast("已关闭青少年模式对话框")
        }
    }
}