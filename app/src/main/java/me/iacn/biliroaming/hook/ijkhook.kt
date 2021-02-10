package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.*
import java.io.Serializable
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import java.lang.reflect.Modifier
import kotlin.Exception

class ijkhook(classLoader: ClassLoader) : BaseHook(classLoader) {
	companion object {
		val playback_speed_override = sPrefs.getString("playback_speed_override", null)?.split(" ")?.map { it.toFloatOrNull() }?.mapNotNull { it }.run { if (this.isNullOrEmpty()) return@run null else this  }?.toFloatArray()
		val default_playback_speed = sPrefs.getString("default_playback_speed", null)?.toFloatOrNull()
		val standalone_danmaku_speed = sPrefs.getBoolean("standalone_danmaku_speed", false)
		val danmakuonEventlist:MutableList<String> = emptyList<String>().toMutableList()
		val danmakuonPreparedlist:MutableList<String> = emptyList<String>().toMutableList()
	}

	override fun startHook() {
		Log.d("startHook: ijk")

		val notDanmakuOptionsPlayerAdapterV2onPreparedhooker : Hooker = fun(it: MethodHookParam) {
			it.thisObject.callMethod("onEvent", "DemandPlayerEventRequestPlaybackSpeed", arrayOf(default_playback_speed?: playback_speed_override!![2]))
		}

		val notDanmakuOptionsPlayerAdapterV2onEventhooker : Hooker = fun(it: MethodHookParam) {
			if (it.args[0] == "DemandPlayerEventRequestPlaybackSpeed") {
				it.result = null
			}
		}

		instance.classesList.filter {
			it.startsWith("tv.danmaku.biliplayer.features.danmaku")
		}.forEach { c->
			c.findClassOrNull(mClassLoader)?.run {
				declaredMethods.forEach { m->
					if(m.name == "onEvent" && Modifier.isPublic(m.modifiers) && m.parameterTypes.size == 2 && m.parameterTypes[0] == String::class.java && m.parameterTypes[1] == Array::class.java) {
						danmakuonEventlist.add(c)
					}
					if(m.name == "onPrepared" && Modifier.isPublic(m.modifiers) && m.parameterTypes.size == 1 && m.parameterTypes[0].name == "tv.danmaku.ijk.media.player.IMediaPlayer") {
						danmakuonPreparedlist.add(c)
					}
				}
			}
		}

		if (standalone_danmaku_speed) {
			danmakuonEventlist.forEach { it.hookBeforeMethod(mClassLoader, "onEvent", String::class.java, Array::class.java, hooker = notDanmakuOptionsPlayerAdapterV2onEventhooker) }
		}

		if (!standalone_danmaku_speed && (playback_speed_override != null || default_playback_speed != null)) {
			danmakuonPreparedlist.intersect(danmakuonEventlist).forEach { it.hookAfterMethod(mClassLoader,  "onPrepared", "tv.danmaku.ijk.media.player.IMediaPlayer", hooker = notDanmakuOptionsPlayerAdapterV2onPreparedhooker) }
		}

		if (playback_speed_override != null)
			instance.PlayerOptionsPanelHolderclass?.setStaticObjectField(instance.playbackspeedlist(), playback_speed_override)

		if (playback_speed_override != null || default_playback_speed != null) {
			instance.PlayerParamsBundleclass?.hookBeforeMethod(instance.putSerializabletoPlayerParamsBundle(), String::class.java, Serializable::class.java) {
				if (it.args[0] == "bundle_key_playback_speed") {
					if(Exception().stackTrace[4].className.startsWith("tv.danmaku.bili.ui.video.creator"))
						it.result = null
				}
			}
		}

		if (default_playback_speed != null) {
			instance.PlayerCoreServiceV2class?.hookBeforeMethod(instance.getdefaultspeed(), Boolean::class.java) {
				it.result = default_playback_speed
			}
		}
	}
}
