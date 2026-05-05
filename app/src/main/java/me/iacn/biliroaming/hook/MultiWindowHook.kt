package me.iacn.biliroaming.hook

import android.app.Activity
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookAllMethods
import me.iacn.biliroaming.utils.hookMethod
import me.iacn.biliroaming.utils.sPrefs

class MultiWindowHook(mClassLoader: ClassLoader) : BaseHook(mClassLoader) {
    override fun startHook() {
        if (sPrefs.getBoolean("fake_non_multiwindow", false).not()) return
        Log.d("startHook: MultiWindowHook")
        Activity::class.java
            .getDeclaredMethod("isInMultiWindowMode")
            .hookMethod { false }
        Activity::class.java
            .hookAllMethods("onMultiWindowModeChanged") { chain ->
                val args = chain.args.toTypedArray()
                args[0] = false
                chain.proceed(args)
            }
    }
}