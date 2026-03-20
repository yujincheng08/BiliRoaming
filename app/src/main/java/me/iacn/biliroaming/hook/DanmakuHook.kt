package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class DanmakuHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    override fun startHook() {
        val blockWeight = sPrefs.getInt("danmaku_filter_weight", 0)
        val disableVipDmColorful = sPrefs.getBoolean("disable_vip_dm_colorful", false)
        if (blockWeight <= 0 && !disableVipDmColorful) return
        instance.dmMossClass?.hookMethod(
            "dmSegMobile",
            "com.bapis.bilibili.community.service.dm.v1.DmSegMobileReq",
            instance.mossResponseHandlerClass
        ) { chain ->
            val args = chain.args.toTypedArray()
            args[1] = args[1]!!.mossResponseHandlerProxy {
                if (blockWeight > 0) filterDanmaku(it, blockWeight)
                if (disableVipDmColorful) clearVipColorfulSrc(it)
            }
            chain.proceed(args)
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

    private fun clearVipColorfulSrc(reply: Any?) {
        reply?.callMethodOrNullAs<List<Any>>("getColorfulSrcList")?.filter {
            // DmColorfulType 60001 VipGradualColor
            it.callMethodAs<Int>("getTypeValue") != 60001
        }?.let {
            reply.callMethod("clearColorfulSrc")
            reply.callMethod("addAllColorfulSrc", it)
        }
    }
}
