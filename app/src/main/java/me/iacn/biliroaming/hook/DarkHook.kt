package me.iacn.biliroaming.hook

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.BiliBiliPackage.Weak
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.*

class DarkHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("follow_dark", false)) return
        Log.d("startHook: Dark")
        val hooker = { param: XC_MethodHook.MethodHookParam ->
            if (isNight != null && inDark != isNight) switch(param.thisObject as Activity)
        }
        instance.splashActivityClass?.hookBeforeMethod("onCreate", Bundle::class.java, hooker = hooker)
        instance.mainActivityClass?.hookBeforeMethod("onCreate", Bundle::class.java, hooker = hooker)
        instance.mainActivityClass?.hookBeforeMethod("onResume", hooker = hooker)
    }

    fun switch(activity: Activity) {
        instance.homeUserCenterClass?.new()?.run {
            val unhook = fragmentClass?.hookAfterMethod("getActivity") { param2 ->
                if (param2.thisObject == this)
                    param2.result = activity
            }
            val viewId = activity.resources.getIdentifier("mine_day_night_setting", "id", activity.packageName)
            callMethod("onClick", View(activity).run { id = viewId; this })
            unhook?.unhook()
        }
    }

    private val fragmentClass by Weak {
        "androidx.fragment.app.Fragment".findClassOrNull(mClassLoader)
                ?: "android.support.v4.app.Fragment".findClassOrNull(mClassLoader)
    }

    private val isNight
        get() = instance.garb()?.let { instance.garbHelperClass?.callStaticMethod(it)?.callMethodAs<Boolean?>("isNight") }

    private val inDark
        get() = Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}