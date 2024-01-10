package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.callMethod
import me.iacn.biliroaming.utils.callMethodOrNullAs
import me.iacn.biliroaming.utils.getObjectField
import me.iacn.biliroaming.utils.hookAfterAllConstructors
import me.iacn.biliroaming.utils.sPrefs

class SpeedHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        Log.d("startHook: SpeedHook")
        val defaultPlaybackSpeed = sPrefs.getInt("default_speed", 100) / 100f
        if (defaultPlaybackSpeed == 1f) return
        instance.playSpeedManager?.hookAfterAllConstructors {
            for (f in it.thisObject.javaClass.declaredFields) {
                val o = it.thisObject.getObjectField(f.name)
                val v = o?.callMethodOrNullAs<Float?>("getValue") ?: continue
                if (v != 1f) continue
                o.callMethod("setValue", defaultPlaybackSpeed)
                Log.toast("已设置 $defaultPlaybackSpeed 倍速")
                break
            }
        }
    }
}
