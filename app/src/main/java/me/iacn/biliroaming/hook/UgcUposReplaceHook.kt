package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.ListenPlayInfo
import me.iacn.biliroaming.copy
import me.iacn.biliroaming.utils.*
import me.iacn.biliroaming.utils.UposReplaceHelper.reconstructListenPlayInfoUpos
import me.iacn.biliroaming.utils.UposReplaceHelper.replaceRawVodInfoUpos

class UgcUposReplaceHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val enableUgcUposReplace = if (sPrefs.getString("upos_host", null) != "\$1") {
        sPrefs.getBoolean("enable_ugc_upos_replace", false)
    } else {
        sPrefs.edit().putBoolean("enable_ugc_upos_replace", false).apply()
        false
    }
    private val ugcAnyModelTypeUrl =
        "type.googleapis.com/bilibili.app.playerunite.ugcanymodel.UGCAnyModel"

    override fun startHook() {
        if (!enableUgcUposReplace) return
        Log.d("startHook: UgcUposReplaceHook")
        instance.playerUgcMossClass?.hookAfterMethod(
            "playView", instance.playViewUgcReqClass
        ) { param ->
            if (instance.networkExceptionClass?.isInstance(param.throwable) == true) return@hookAfterMethod
            param.result.runCatching {
                this.callMethod("getVideoInfo")?.let { vodInfo ->
                    val newVodInfo = vodInfo.replaceRawVodInfoUpos() ?: return@hookAfterMethod
                    this.callMethod("setVideoInfo", newVodInfo)
                }
            }.onFailure { Log.e(it) }
        }
        instance.playerUniteMossClass?.hookAfterMethod(
            "playViewUnite", instance.playViewUniteReqClass
        ) { param ->
            if (instance.networkExceptionClass?.isInstance(param.throwable) == true) return@hookAfterMethod
            param.result.runCatching {
                val typeUrl = this.callMethod("getSupplement")?.callMethodAs<String>("getTypeUrl")
                // here only handle ugc video, pgc video upos replace has been implemented before
                if (typeUrl.isNullOrEmpty() || typeUrl != ugcAnyModelTypeUrl) return@hookAfterMethod
                this.callMethod("getVodInfo")?.let { vodInfo ->
                    val newVodInfo = vodInfo.replaceRawVodInfoUpos() ?: return@hookAfterMethod
                    this.callMethod("setVodInfo", newVodInfo)
                }
            }.onFailure { Log.e(it) }
        }
        instance.listenerMossClass?.hookAfterMethod(
            "playURL", instance.listenerPlayURLReqClass
        ) { param ->
            if (instance.networkExceptionClass?.isInstance(param.throwable) == true) return@hookAfterMethod
            // if not playable then unlock limitation, which will be handled elsewhere
            if (param.result.callMethodOrNull("getPlayable") != 0) return@hookAfterMethod
            param.result.runCatching {
                this.callMethodAs<MutableMap<Long, Any>>("getMutablePlayerInfoMap").entries.forEach { entry ->
                    val listenPlayInfo = entry.value
                    val serializedListenPlayInfo =
                        listenPlayInfo.callMethodAs<ByteArray>("toByteArray")
                    val newSerializedListenPlayInfo =
                        ListenPlayInfo.parseFrom(serializedListenPlayInfo)
                            .copy { reconstructListenPlayInfoUpos() }.toByteArray()
                    val newListenPlayInfo = listenPlayInfo.javaClass.callStaticMethod(
                        "parseFrom", newSerializedListenPlayInfo
                    ) ?: return@forEach
                    entry.setValue(newListenPlayInfo)
                }
            }.onFailure { Log.e(it) }
        }
    }
}
