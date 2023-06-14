package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class DanmakuHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val blockWeight = sPrefs.getInt("danmaku_filter_weight", 0)
    private val disableVipDmColorful = sPrefs.getBoolean("disable_vip_dm_colorful", false)

    override fun startHook() {
        if (blockWeight <= 0 && !disableVipDmColorful) return
        instance.dmMossClass?.hookBeforeMethod(
            "dmSegMobile",
            "com.bapis.bilibili.community.service.dm.v1.DmSegMobileReq",
            instance.mossResponseHandlerClass
        ) { param ->
            param.args[1] = param.args[1].mossResponseHandlerProxy {
                filterDanmaku(it)
                clearVipColorfulSrc(it)
            }
        }
    }

    private fun filterDanmaku(reply: Any?) {
        if (blockWeight <= 0) return
        reply?.callMethodAs<List<Any>>("getElemsList")?.filter {
            it.callMethodAs<Int>("getWeight") >= blockWeight
        }?.let {
            reply.callMethod("clearElems")
            reply.callMethod("addAllElems", it)
        }
    }

    private fun clearVipColorfulSrc(reply: Any?) {
        if (!disableVipDmColorful) return
        reply?.callMethodOrNullAs<List<Any>>("getColorfulSrcList")?.filter {
            // DmColorfulType 60001 VipGradualColor
            it.callMethodAs<Int>("getTypeValue") != 60001
        }?.let {
            reply.callMethod("clearColorfulSrc")
            reply.callMethod("addAllColorfulSrc", it)
        }
    }
}
