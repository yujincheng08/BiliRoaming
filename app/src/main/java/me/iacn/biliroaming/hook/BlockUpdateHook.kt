package me.iacn.biliroaming.hook

import android.content.Context
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.new
import me.iacn.biliroaming.utils.sPrefs

class BlockUpdateHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("block_update", false)) return
        instance.updateInfoSupplierClass?.hookBeforeMethod(
            instance.check(), Context::class.java
        ) { param ->
            val message = "哼，休想要我更新！<(￣︶￣)>"
            param.throwable = instance.latestVersionExceptionClass?.new(message) as? Throwable
                ?: Exception(message)
        }
    }
}
