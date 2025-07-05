package me.iacn.biliroaming.hook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.findFieldByExactType
import me.iacn.biliroaming.utils.from
import me.iacn.biliroaming.utils.hookAfterMethod
import me.iacn.biliroaming.utils.sPrefs

class VipSectionHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("hidden", false)
            || !sPrefs.getBoolean("remove_vip_section", false)
        ) return


        instance.homeUserCenterClass!!.hookAfterMethod(
            "onViewCreated",
            View::class.java,
            Bundle::class.java
        ) {
            val obj = it.thisObject
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
        }
    }
}
