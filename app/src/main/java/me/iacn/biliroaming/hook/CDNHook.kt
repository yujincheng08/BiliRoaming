package me.iacn.biliroaming.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.*
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.XposedInit.Companion.toastMessage
import me.iacn.biliroaming.utils.Log
import java.net.InetAddress

class CDNHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("use_cdn", false)) return

        Log.d("startHook: CDN")

        findAndHookMethod("java.net.InetAddress", mClassLoader,
                "getAllByName", String::class.java, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                val host = param.args[0] as String
                val cdn: String = getCDN()
                if (cdn.isNotEmpty() && host == "upos-hz-mirrorakam.akamaized.net") {
                    param.result = arrayOf(InetAddress.getByName(cdn))
                    Log.d("Replace by CDN: $cdn")
                    toastMessage("CDN加速已生效")
                }
            }
        })

        findAndHookMethod("java.net.InetAddress", mClassLoader,
                "getByName", String::class.java, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                val host = param.args[0] as String
                val cdn: String = getCDN()
                if (cdn.isNotEmpty() && host == "upos-hz-mirrorakam.akamaized.net") {
                    param.result = InetAddress.getByName(cdn)
                    Log.d("Replace by CDN: $cdn")
                    toastMessage("CDN加速已生效")
                }
            }
        })

        findAndHookMethod("tv.danmaku.ijk.media.player.IjkMediaPlayerItem", mClassLoader,
                "setItemOptions", object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                val url = callMethod(param.thisObject, "mediaAssetToUrl", 0, 0) as String
                val cdn: String = getCDN()
                if (cdn.isNotEmpty() && url.contains("upos-hz-mirrorakam.akamaized.net")) {
                    val params = getObjectField(param.thisObject, "mIjkMediaConfigParams")
                    setObjectField(params, "mHttpProxy", "http://$cdn:80")
                    val proxy = getObjectField(params, "mHttpProxy") as String
                    Log.d("Using cdn as proxy: $proxy")
                    toastMessage("CDN加速已生效")
                }
            }
        })
    }

    fun getCDN(): String {
        var cdn = XposedInit.sPrefs.getString("cdn", "")!!
        if (cdn.isEmpty()) cdn = XposedInit.sPrefs.getString("custom_cdn", "")!!
        return cdn
    }

}