package me.iacn.biliroaming.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge.hookAllConstructors
import de.robv.android.xposed.XposedHelpers.findClass
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.bv2av

class MiniProgramHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("mini_program", false)) return
        Log.d("startHook: mini program")
        hookAllConstructors(findClass(instance?.routeParams(), mClassLoader), object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam?) {
                val arg = param!!.args[1] as android.net.Uri
                if (arg.toString() != "action://share/shareto") return
                val bundle = param.args[2] as android.os.Bundle
                val extra = bundle.getBundle("default_extra_bundle")
                if (extra?.getString("platform") == "COPY"){
                    extra.getString("params_content")?.let {url ->
                        val idx = url.lastIndexOf("BV")
                        if (idx<0) return
                        val bv = url.substring(idx)
                        if (bv.length != 12) return
                        extra.putString("params_content", "${url.substring(0, idx)}av${bv2av(bv)}")
                    }
                    return
                }
                if (extra?.getString("params_type") != "type_min_program") return
                extra.putString("params_type", "type_web")
                if (extra.getString("platform") == "QQ") {
                    extra.putString("params_title", extra.getString("params_content"))
                }
                extra.putString("params_content", "由哔哩漫游分享")
            }
        })
    }
}