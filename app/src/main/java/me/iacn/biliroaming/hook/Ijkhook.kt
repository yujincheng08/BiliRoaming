package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import java.io.Serializable
import java.lang.reflect.Modifier

class Ijkhook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        val playbackSpeedOverride = sPrefs.getString("playback_speed_override", null)?.split(" ")
            ?.map { it.toFloatOrNull() }?.mapNotNull { it }
            .run { if (this.isNullOrEmpty()) return@run null else this }?.toFloatArray()
        val defaultPlaybackSpeed =
            sPrefs.getString("default_playback_speed", null)?.toFloatOrNull()
        val standaloneDanmakuSpeed = sPrefs.getBoolean("standalone_danmaku_speed", false)
        val danmakuOnEventList: MutableList<String> = emptyList<String>().toMutableList()
        val danmakuOnPreparedList: MutableList<String> = emptyList<String>().toMutableList()
        val purifyEndpage = sPrefs.getBoolean("purify_endpage", false)
    }

    override fun startHook() {
        Log.d("startHook: ijk")

        if (purifyEndpage) {
            "tv.danmaku.bili.ui.video.creator.AvPlayerConfiguration".hookAfterMethod(mClassLoader, "getCustomFeatures") {
                it.result = (it.result as ArrayList<*>).filterNot { feature -> feature.getObjectFieldAs<String>("mName") == "EndPageAdapter" || feature.getObjectFieldAs<Class<*>>("mClass").name.startsWith("tv.danmaku.biliplayer.features.endpage") }
            }

            "com.bilibili.bangumi.player.BangumiPlayerConfiguration".hookAfterMethod(mClassLoader, "getCustomFeatures") {
                it.result = (it.result as ArrayList<*>).filterNot { feature -> feature.getObjectFieldAs<String>("mName") == "EndPageBangumiAdapter" || feature.getObjectFieldAs<Class<*>>("mClass").name.startsWith("com.bilibili.bangumi.player.endpage") }
            }
        }

        val notDanmakuOptionsPlayerAdapterV2onPreparedHooker: Hooker = fun(it: MethodHookParam) {
            it.thisObject.callMethod(
                "onEvent",
                "DemandPlayerEventRequestPlaybackSpeed",
                arrayOf(defaultPlaybackSpeed ?: playbackSpeedOverride!![2])
            )
        }

        val notDanmakuOptionsPlayerAdapterV2onEventHooker: Hooker = fun(it: MethodHookParam) {
            if (it.args[0] == "DemandPlayerEventRequestPlaybackSpeed") {
                it.result = null
            }
        }

        instance.classesList.filter {
            it.startsWith("tv.danmaku.biliplayer.features.danmaku")
        }.forEach { c ->
            c.findClassOrNull(mClassLoader)?.run {
                declaredMethods.forEach { m ->
                    if (m.name == "onEvent" && Modifier.isPublic(m.modifiers) && m.parameterTypes.size == 2 && m.parameterTypes[0] == String::class.java && m.parameterTypes[1] == Array::class.java) {
                        danmakuOnEventList.add(c)
                    }
                    if (m.name == "onPrepared" && Modifier.isPublic(m.modifiers) && m.parameterTypes.size == 1 && m.parameterTypes[0].name == "tv.danmaku.ijk.media.player.IMediaPlayer") {
                        danmakuOnPreparedList.add(c)
                    }
                }
            }
        }

        if (standaloneDanmakuSpeed) {
            danmakuOnEventList.forEach {
                it.hookBeforeMethod(
                    mClassLoader,
                    "onEvent",
                    String::class.java,
                    Array::class.java,
                    hooker = notDanmakuOptionsPlayerAdapterV2onEventHooker
                )
            }
        }

        if (!standaloneDanmakuSpeed && (playbackSpeedOverride != null || defaultPlaybackSpeed != null)) {
            danmakuOnPreparedList.intersect(danmakuOnEventList).forEach {
                it.hookAfterMethod(
                    mClassLoader,
                    "onPrepared",
                    "tv.danmaku.ijk.media.player.IMediaPlayer",
                    hooker = notDanmakuOptionsPlayerAdapterV2onPreparedHooker
                )
            }
        }

        if (playbackSpeedOverride != null)
            instance.playerOptionsPanelHolderClass?.setStaticObjectField(
                instance.playbackSpeedList(),
                playbackSpeedOverride
            )

        if (playbackSpeedOverride != null || defaultPlaybackSpeed != null) {
            instance.playerParamsBundleClass?.hookBeforeMethod(
                instance.putSerializableToPlayerParamsBundle(),
                String::class.java,
                Serializable::class.java
            ) {
                if (it.args[0] == "bundle_key_playback_speed") {
                    if (Exception().stackTrace.map { it.className }.intersect(instance.classesList)
                            .first().startsWith("tv.danmaku.bili.ui.video.creator")
                    )
                        it.result = null
                }
            }
        }

        if (defaultPlaybackSpeed != null) {
            instance.playerCoreServiceV2Class?.hookBeforeMethod(
                instance.defaultSpeed(),
                Boolean::class.java
            ) {
                it.result = defaultPlaybackSpeed
            }
        }
    }
}
