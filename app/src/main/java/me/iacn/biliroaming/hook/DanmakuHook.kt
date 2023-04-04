package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import java.lang.reflect.Proxy

class DanmakuHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    override fun startHook() {
        val blockWeight = sPrefs.getInt("danmaku_filter_weight", 0)
            .takeIf { it > 0 } ?: return
        "com.bapis.bilibili.community.service.dm.v1.DMMoss".from(mClassLoader)?.hookBeforeMethod(
            "dmSegMobile",
            "com.bapis.bilibili.community.service.dm.v1.DmSegMobileReq",
            instance.mossResponseHandlerClass
        ) { param ->
            val handler = param.args[1]
            param.args[1] = Proxy.newProxyInstance(
                handler.javaClass.classLoader,
                arrayOf(instance.mossResponseHandlerClass)
            ) { _, m, args ->
                if (m.name == "onNext") {
                    filterDanmaku(args[0], blockWeight)
                    m(handler, *args)
                } else if (args == null) {
                    m(handler)
                } else {
                    m(handler, *args)
                }
            }
        }
    }

    private fun filterDanmaku(reply: Any?, blockWeight: Int) {
        reply?.callMethodAs<List<Any>>("getElemsList")?.filter {
            it.callMethodAs<Int>("getWeight") >= blockWeight
        }?.let {
            reply.callMethod("clearElems")
            reply.callMethod("addAllElems", it)
        }
    }
}
