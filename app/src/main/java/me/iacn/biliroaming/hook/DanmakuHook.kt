package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.*


class DanmakuHook(classLoader: ClassLoader) : BaseHook(classLoader) {


    override fun startHook() {
        if (!sPrefs.getBoolean("danmaku_filter", false)) {
            return
        }
        Log.d("StartHook: DanmakuHook")
        hookDanmaku()
    }


    private fun hookDanmaku() {
        "com.bapis.bilibili.community.service.dm.v1.DMMoss".findClass(mClassLoader).run {
            hookBeforeMethod(
                "dmSegMobile",
                "com.bapis.bilibili.community.service.dm.v1.DmSegMobileReq",
                "com.bilibili.lib.moss.api.MossResponseHandler"
            ) { methodHookParam ->
                methodHookParam.thisObject.callMethod(
                    "dmSegMobile", methodHookParam.args[0]
                )
                    ?.let { dmSegMobileReply ->
                        methodHookParam.args[1].callMethod("onNext", dmSegMobileReply)
                    }
                methodHookParam.result = null
            }
            hookAfterMethod(
                "dmSegMobile", "com.bapis.bilibili.community.service.dm.v1.DmSegMobileReq"
            ) { methodHookParam ->
                filterDanmaku(methodHookParam.result)
            }
        }
    }


    private fun filterDanmaku(dmSegmentMobileReply: Any) {
        val resultDanmakuList = mutableListOf<Any>()
        val weightThreshold = if (sPrefs.getBoolean(
                "danmaku_filter_weight_switch", false
            )
        ) sPrefs.getInt("danmaku_filter_weight_value", 0) else null
        dmSegmentMobileReply.getObjectFieldOrNullAs<List<*>>("elems_").orEmpty().let { elems ->
            for (danmakuElem in elems) {
                if (danmakuElem == null || weightThreshold == null) {
                    continue
                }
                val weight = danmakuElem.callMethodAs<Int>("getWeight")
                if (weight < weightThreshold) continue
                resultDanmakuList.add(danmakuElem)
            }
        }
        dmSegmentMobileReply.callMethod("clearElems")
        dmSegmentMobileReply.callMethod("addAllElems", resultDanmakuList)
    }
}
