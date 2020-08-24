package me.iacn.biliroaming.hook

import me.iacn.biliroaming.Constant.AKAMAI_HOST
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.XposedInit.Companion.toastMessage
import me.iacn.biliroaming.utils.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import java.io.IOException
import java.net.*

class CDNHook(classLoader: ClassLoader, private val processName: String) : BaseHook(classLoader) {
    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("use_cdn", false)) return

        Log.d("startHook: CDN")

        if (processName.endsWith(":download")) {
            val proxySelector = object : ProxySelector() {
                override fun select(uri: URI): MutableList<Proxy> {
                    if (uri.scheme == "http" && uri.host == AKAMAI_HOST) {
                        val cdn: String = getCDN()
                        if(cdn.isNotEmpty()) {
                            Log.d("Try to use $cdn as proxy")
                            toastMessage("CDN加速已生效")
                            return mutableListOf(Proxy(Proxy.Type.HTTP, InetSocketAddress(cdn, 80)))
                        }
                    }
                    return mutableListOf()
                }

                override fun connectFailed(p0: URI?, p1: SocketAddress?, p2: IOException?) {
                    Log.e(p2)
                    toastMessage("CDN加速失败")
                }
            }

            instance.okHttpClientBuilderClass?.hookBeforeMethod(instance.httpClientBuild()) { param ->
                param.thisObject.setObjectField(instance.proxySelector(), proxySelector)
            }
        } else {
            "tv.danmaku.ijk.media.player.IjkMediaPlayerItem".hookBeforeMethod(mClassLoader, "setItemOptions") { param ->
                val url = param.thisObject.callMethod("mediaAssetToUrl", 0, 0) as String
                val cdn: String = getCDN()
                if (cdn.isNotEmpty() && url.startsWith("http://${AKAMAI_HOST}")) {
                    param.thisObject.getObjectField("mIjkMediaConfigParams").setObjectField("mHttpProxy", "http://$cdn:80")
                    Log.d("Using cdn as proxy: $cdn")
                    toastMessage("CDN加速已生效")
                }
            }
        }
    }

    private fun getCDN(): String {
        var cdn = XposedInit.sPrefs.getString("cdn", "")
        if (cdn.isNullOrEmpty()) cdn = XposedInit.sPrefs.getString("custom_cdn", "") ?: ""
        return cdn
    }

}