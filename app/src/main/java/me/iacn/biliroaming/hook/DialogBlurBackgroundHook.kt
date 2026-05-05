package me.iacn.biliroaming.hook

import android.app.Dialog
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.blurBackground
import me.iacn.biliroaming.utils.hookMethod
import me.iacn.biliroaming.utils.sPrefs

class DialogBlurBackgroundHook(mClassLoader: ClassLoader) : BaseHook(mClassLoader) {
    override fun startHook() {
        if (sPrefs.getBoolean("dialog_blur_background", false).not()) return
        Log.d("startHook: DialogBlurBackgroundHook")
        Dialog::class.java.hookMethod("onStart") { chain ->
            val result = chain.proceed()
            (chain.thisObject as Dialog).window?.blurBackground()
            result
        }
    }
}
