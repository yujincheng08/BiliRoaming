package me.iacn.biliroaming.hook

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.hookAfterMethod

class IntegratedDisabledHintHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        instance.mainActivityClass?.hookAfterMethod("onCreate", Bundle::class.java) { param ->
            AlertDialog.Builder(param.thisObject as Activity).run {
                setTitle("哔哩漫游")
                setMessage("哔哩漫游无法在内置模式下运行，请改用本地模式。")
                setNegativeButton("知道了") { _, _ -> }
                show()
            }
        }
    }
}
