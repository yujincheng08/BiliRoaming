package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.sPrefs

class SpeedHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private var lastSet: Any? = null

    override fun startHook() {
        Log.d("startHook: SpeedHook")
        val speed = sPrefs.getInt("default_speed", 100)
        if (speed == 100) return
        instance.playerCoreServiceV2Class?.hookBeforeMethod(instance.setDefaultSpeed(), Float::class.javaPrimitiveType) {
            if (lastSet != it.thisObject) {
                lastSet = it.thisObject
                it.args[0] = speed / 100f
                Log.toast("已设置倍速为 ${speed}%")
            }
        }
    }
}
