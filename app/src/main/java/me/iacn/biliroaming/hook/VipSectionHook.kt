package me.iacn.biliroaming.hook

import android.os.Bundle
import android.view.View
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.hookMethod
import me.iacn.biliroaming.utils.sPrefs


class VipSectionHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("hidden", false)
            || !sPrefs.getBoolean("remove_vip_section", false)
        ) return

        instance.homeUserCenterClass!!.hookMethod(
            "onViewCreated",
            View::class.java,
            Bundle::class.java
        ) { chain ->
            chain.proceed()
            val obj = chain.thisObject
            val vipModuleManager = instance.homeUserCenterClass!!.declaredFields.single {
                // $mineVipModuleManager
                it.type.toString().contains("MineVipModuleManager")
            }.run {
                isAccessible = true
                get(obj)
            }

            vipModuleManager::class.java.declaredMethods.single {
                // $method(isTeenager: Boolean)
                it.parameterCount == 1 &&
                        it.parameterTypes[0] == Boolean::class.java
            }.run {
                isAccessible = true
                invoke(vipModuleManager, true)
            }
            null
        }
    }
}
