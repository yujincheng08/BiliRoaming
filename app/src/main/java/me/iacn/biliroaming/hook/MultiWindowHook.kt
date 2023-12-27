package me.iacn.biliroaming.hook

import android.app.Activity
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookBeforeAllMethods
import me.iacn.biliroaming.utils.replaceMethod
import me.iacn.biliroaming.utils.sPrefs

class MultiWindowHook(mClassLoader: ClassLoader) : BaseHook(mClassLoader) {
    override fun startHook() {
        if (sPrefs.getBoolean("fake_non_multiwindow", false).not()) return
        Log.d("startHook: MultiWindowHook")
        Activity::class.java
            .getDeclaredMethod("isInMultiWindowMode")
            .replaceMethod { false }
        Activity::class.java
            .hookBeforeAllMethods("onMultiWindowModeChanged") { param ->
                param.args[0] = false
            }
    }
}