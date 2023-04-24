package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class LosslessSettingHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private var losslessEnabled: Boolean
        get() = biliPrefs.getBoolean("biliroaming_lossless", false)
        set(value) {
            biliPrefs.edit().putBoolean("biliroaming_lossless", value).apply()
        }

    override fun startHook() {
        if (!sPrefs.getBoolean("remember_lossless_setting", false))
            return
        instance.playURLMossClass?.run {
            hookBeforeMethod(
                "playConf",
                "com.bapis.bilibili.app.playurl.v1.PlayConfReq",
                instance.mossResponseHandlerClass
            ) { param ->
                param.args[1] = param.args[1].mossResponseHandlerProxy {
                    it?.callMethod("getPlayConf")
                        ?.callMethod("getLossLessConf")
                        ?.callMethod("getConfValue")
                        ?.callMethod("setSwitchVal", losslessEnabled)
                }
            }
            hookBeforeMethod(
                "playConfEdit",
                "com.bapis.bilibili.app.playurl.v1.PlayConfEditReq"
            ) { param ->
                param.args[0].callMethodAs<List<Any>>("getPlayConfList")
                    .firstOrNull {
                        it.callMethodAs<Int>("getConfTypeValue") == 30 // LOSSLESS
                    }?.callMethod("getConfValue")
                    ?.callMethodAs<Boolean>("getSwitchVal")
                    ?.let { losslessEnabled = it }
            }
            hookAfterMethod(
                "playView", instance.playViewReqClass
            ) { param ->
                param.result?.callMethod("getPlayConf")
                    ?.takeIf { it.callMethodAs("hasLossLessConf") }
                    ?.callMethod("getLossLessConf")
                    ?.callMethod("getConfValue")
                    ?.callMethod("setSwitchVal", losslessEnabled)
            }
        }
        instance.playerMossClass?.hookAfterMethod(
            "playViewUnite", instance.playViewUniteReqClass
        ) { param ->
            param.result?.callMethod("getPlayDeviceConf")
                ?.callMethodAs<LinkedHashMap<Int, *>>("internalGetMutableDeviceConfs")?.let {
                    it[30/*LOSSLESS*/]?.callMethod("getConfValue")
                        ?.callMethod("setSwitchVal", losslessEnabled)
                }
        }
    }
}
