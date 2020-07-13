package me.iacn.biliroaming.hook

import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.XposedInit.Companion.toastMessage
import me.iacn.biliroaming.utils.*
import java.net.InetAddress

class CDNHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("use_cdn", false)) return

        Log.d("startHook: CDN")

        "java.net.InetAddress".hookAfterMethod(mClassLoader, "getAllByName", String::class.java) { param ->
            val host = param.args[0] as String
            val cdn: String = getCDN()
            if (cdn.isNotEmpty() && host == "upos-hz-mirrorakam.akamaized.net") {
                param.result = arrayOf(InetAddress.getByName(cdn))
                Log.d("Replace by CDN: $cdn")
                toastMessage("CDN加速已生效")
            }
        }

        "java.net.InetAddress".hookAfterMethod(mClassLoader, "getByName", String::class.java) { param ->
            val host = param.args[0] as String
            val cdn: String = getCDN()
            if (cdn.isNotEmpty() && host == "upos-hz-mirrorakam.akamaized.net") {
                param.result = InetAddress.getByName(cdn)
                Log.d("Replace by CDN: $cdn")
                toastMessage("CDN加速已生效")
            }
        }

        "tv.danmaku.ijk.media.player.IjkMediaPlayerItem".hookBeforeMethod(mClassLoader, "setItemOptions") { param ->
            val url = param.thisObject.callMethod("mediaAssetToUrl", 0, 0) as String
            val cdn: String = getCDN()
            if (cdn.isNotEmpty() && url.contains("upos-hz-mirrorakam.akamaized.net")) {
                param.thisObject.getObjectField("mIjkMediaConfigParams").setObjectField("mHttpProxy", "http://$cdn:80")
                Log.d("Using cdn as proxy: $cdn")
                toastMessage("CDN加速已生效")
            }
        }
}

fun getCDN(): String {
    var cdn = XposedInit.sPrefs.getString("cdn", "")!!
    if (cdn.isEmpty()) cdn = XposedInit.sPrefs.getString("custom_cdn", "")!!
    return cdn
}

}