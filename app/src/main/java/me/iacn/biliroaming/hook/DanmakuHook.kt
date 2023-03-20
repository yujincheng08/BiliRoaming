package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.*


class DanmakuHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    val MossException = "com.bilibili.lib.moss.api.MossException".findClass(mClassLoader)

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
                try {
                    methodHookParam.thisObject.callMethod(
                        "dmSegMobile", methodHookParam.args[0]
                    )
                } catch (e: Throwable) {
                    methodHookParam.args[1].callMethod(
                        "onError", MossException.getStaticObjectField("UNSUPPORTED")
                    )
                    null
                }?.let { dmSegMobileReply ->
                    methodHookParam.args[1].callMethod("onNext", dmSegMobileReply)
                }
                methodHookParam.result = null
            }
            it.hookAfterMethod(
                "dmSegMobile", "com.bapis.bilibili.community.service.dm.v1.DmSegMobileReq"
            ) { methodHookParam ->
                Log.d("DanmakuHook: call " + methodHookParam.method.name)
                try {
                    filterDanmaku(methodHookParam.result)
                } catch (e: Throwable) {
                    println(e)
                    methodHookParam.throwable =
                        MossException.getStaticObjectField("UNSUPPORTED") as Throwable?
                }
            }
        }
    }


    fun filterDanmaku(dmSegmentMobileReply: Any) {
        val resultDanmakuList = mutableListOf<Any>()
        val weightThreshold = if (sPrefs.getBoolean(
                "danmaku_filter_weight_switch", false
            )
        ) sPrefs.getString("danmaku_filter_weight_value", "0")?.toInt() else null
        Log.d("DanmakuHook: weightThreshold" + weightThreshold)
        for (danmakuElem in (dmSegmentMobileReply.getObjectField("elems_") as List<*>)) {
            if (danmakuElem != null) {
                if (weightThreshold != null) {
                    val weight = danmakuElem.callMethod("getWeight") as Int
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
