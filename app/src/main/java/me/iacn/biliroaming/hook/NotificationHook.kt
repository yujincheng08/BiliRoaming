package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookAfterMethod
import me.iacn.biliroaming.utils.setIntField

class NotificationHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("music_notificaion", false)) return

        Log.d("start hook: Music Notification")

        instance.musicServiceClass?.hookAfterMethod(instance.musicNotificationStyle()) { param ->
            param.result.setIntField("a", 1)
        }
    }
}