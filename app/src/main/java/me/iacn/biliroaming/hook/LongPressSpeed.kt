package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.hookAllConstructors
import me.iacn.biliroaming.utils.sPrefs


class LongPressSpeed(cl: ClassLoader) : BaseHook(cl) {
    private val speed: Float = sPrefs.getInt("long_press_speed", 300) / 100f


    override fun startHook() {
        if (speed == 3f) return

        instance.tripleSpeedServiceClass!!.hookAllConstructors { chain ->
            chain.proceed()
            val obj = chain.thisObject!!
            obj::class.java.getDeclaredField("\$speed").apply {
                isAccessible = true
                set(obj, speed)
            }
            null
        }
    }
}