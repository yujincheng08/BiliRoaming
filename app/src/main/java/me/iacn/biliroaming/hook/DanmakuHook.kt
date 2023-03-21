package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.*


class DanmakuHook(classLoader: ClassLoader) : BaseHook(classLoader) {


    override fun startHook() {
        if (!sPrefs.getBoolean("danmaku_filter", false)) {
            return
        }
        Log.d("StartHook: DanmakuHook")
        danmakuHook()
    }


    fun danmakuHook() {
        "com.bapis.bilibili.community.service.dm.v1.DMMoss".findClass(mClassLoader).let {
            Log.d("DanmakuHook: hook $it")
            it.hookBeforeMethod(
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
            it.hookAfterMethod(
                "dmSegMobile", "com.bapis.bilibili.community.service.dm.v1.DmSegMobileReq"
            ) { methodHookParam ->
                Log.d("DanmakuHook: call ${methodHookParam.method.name}")
                filterDanmaku(methodHookParam.result)
            }
        }
    }


    fun filterDanmaku(dmSegmentMobileReply: Any) {
        val resultDanmakuList = mutableListOf<Any>()
        val weightThreshold = if (sPrefs.getBoolean(
                "danmaku_filter_weight_switch", false
            )
        ) sPrefs.getString("danmaku_filter_weight_value", "0")?.toInt() else null
        Log.d("DanmakuHook: weightThreshold: $weightThreshold")
        for (danmakuElem in (dmSegmentMobileReply.getObjectField("elems_") as List<*>)) {
            if (danmakuElem != null) {
                if (weightThreshold != null) {
                    val weight = danmakuElem.callMethodAs<Int>("getWeight")
                    if (weight < weightThreshold) continue
                }
                resultDanmakuList.add(danmakuElem)
            }
        }
        Log.d("DanmakuHook: before= " + dmSegmentMobileReply.callMethod("getElemsCount") + " ;after=" + resultDanmakuList.size)
        dmSegmentMobileReply.callMethod("clearElems")
        dmSegmentMobileReply.callMethod("addAllElems", resultDanmakuList)
    }
}
