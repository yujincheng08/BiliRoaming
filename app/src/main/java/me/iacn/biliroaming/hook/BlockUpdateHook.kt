package me.iacn.biliroaming.hook

import android.content.Context
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.hookMethod
import me.iacn.biliroaming.utils.new
import me.iacn.biliroaming.utils.sPrefs

class BlockUpdateHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("block_update", false)) return
        instance.updateInfoSupplierClass?.hookMethod(
            instance.check(), Context::class.java
        ) { chain ->
            val message = "哼，休想要我更新！<(￣︶￣)>"
            throw (instance.latestVersionExceptionClass?.new(message) as? Throwable
                ?: Exception(message))
        }
    }
}
