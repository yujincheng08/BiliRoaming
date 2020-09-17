package me.iacn.biliroaming.hook

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import me.iacn.biliroaming.BiliBiliPackage.Weak
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance

class DarkHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("follow_dark", false)) return
        Log.d("startHook: Dark")
        instance.splashActivityClass?.hookBeforeMethod("onCreate", Bundle::class.java) { param ->
            val self = param.thisObject as Activity
            if (inDark != isNight) switch(self)
        }
        instance.mainActivityClass?.hookBeforeMethod("onResume") { param ->
            val self = param.thisObject as Activity
            if (inDark != isNight) switch(self)
        }
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

    private val garbClass by Weak { "com.bilibili.lib.ui.garb.Garb".findClass(mClassLoader) }
    private val historySearchActivityClass by Weak { "com.bilibili.app.history.search.ui.HistorySearchActivity".findClass(mClassLoader) }
    private val fragmentClass by Weak { "androidx.fragment.app.Fragment".findClass(mClassLoader) }

    private val garb
        get() = historySearchActivityClass?.run {
            new().getObjectField(declaredFields.firstOrNull { it.type == garbClass }?.name)
        }

    private val isNight
        get() = garb?.callMethodAs<Boolean>("isNight")

    private val inDark
        get() = Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}