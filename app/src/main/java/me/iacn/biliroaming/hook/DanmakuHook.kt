package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class DanmakuHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    override fun startHook() {
        val blockWeight = sPrefs.getInt("danmaku_filter_weight", 0)
            .takeIf { it > 0 } ?: return
        instance.dmMossClass?.hookBeforeMethod(
            "dmSegMobile",
            "com.bapis.bilibili.community.service.dm.v1.DmSegMobileReq",
            instance.mossResponseHandlerClass
        ) { param ->
            param.args[1] = param.args[1].mossResponseHandlerProxy {
                filterDanmaku(it, blockWeight)
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
