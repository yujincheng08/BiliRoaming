package me.iacn.biliroaming.hook

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.view.View
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class DarkHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("follow_dark", false)) return
        Log.d("startHook: Dark")
        val hooker: Hooker = { param ->
            val dark = inDark
            val night = isNight
            if (night != null && dark != night) switch(param.thisObject as Activity)
        }
        Activity::class.java.hookBeforeMethod("onCreate", Bundle::class.java, hooker = hooker)
        Activity::class.java.hookAfterMethod("onPostResume", hooker = hooker)

        instance.brandSplashClass?.hookBeforeMethod(
            "onViewCreated",
            View::class.java,
            Bundle::class.java
        ) { param ->
            if (inDark) {
                (param.args[0] as View).setBackgroundColor(Color.BLACK)
            }
        }
    }

    fun switch(activity: Activity) {
        instance.homeUserCenterClass?.new()?.run {
            val unhook = fragmentClass?.hookAfterMethod("getActivity") { param2 ->
                if (param2.thisObject == this)
                    param2.result = activity
            }
            val viewId = getId("mine_day_night_setting")
            callMethod("onClick", View(activity).apply { id = viewId })
            unhook?.unhook()
        }
    }

    private val fragmentClass by Weak {
        "androidx.fragment.app.Fragment".findClassOrNull(mClassLoader)
            ?: "android.support.v4.app.Fragment".findClassOrNull(mClassLoader)
    }

    private val isNight
        get() = instance.garb()?.let {
            instance.garbHelperClass?.callStaticMethod(it)?.callMethodAs<Boolean?>("isNight")
        }

    private val inDark
        get() = Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}
