package me.iacn.biliroaming.hook

import android.os.Bundle
import android.view.MotionEvent
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import java.lang.reflect.Proxy

class LiveRoomHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (sPrefs.getBoolean("high_live_quality", false)) {
            instance.liveServiceGeneratorClass?.hookAfterMethod(
                instance.liveCreateService(), Class::class.java
            ) { param ->
                val type = param.args[0] as Class<*>
                val service = param.result
                param.result = Proxy.newProxyInstance(
                    service.javaClass.classLoader, arrayOf(type),
                ) { _, m, args ->
                    if (m.name == "getRoomPlayInfoV2") {
                        val noPlayUrl = args[1] as Int
                        val qn = args[2] as Int
                        if (noPlayUrl == 1 && qn == 0) {
                            args[1] = 0
                            args[2] = 10000 // 原画
                        }
                        m(service, *args)
                    } else if (args == null) {
                        m(service)
                    } else {
                        m(service, *args)
                    }
                }
            }
        }
        if (sPrefs.getBoolean("forbid_switch_live_room", false)) {
            instance.livePagerRecyclerViewClass?.replaceMethod(
                "onInterceptTouchEvent",
                MotionEvent::class.java
            ) { false }
        }
        if (sPrefs.getBoolean("disable_live_room_double_click", false)) {
            instance.liveRoomPlayerViewClass?.declaredMethods?.find { it.name == "onDoubleTap" }
                ?.hookBeforeMethod { param ->
                    runCatching {
                        val player = param.thisObject.callMethod("getPlayerCommonBridge")
                            ?: return@hookBeforeMethod
                        val method = if (player.callMethodAs("isPlaying"))
                            "pause" else "resume"
                        player.callMethod(method)
                    }.onSuccess { param.result = true }
                }
            val lastTouchUpTimeField =
                instance.liveRoomPlayerViewClass?.findFirstFieldByExactTypeOrNull(Long::class.javaPrimitiveType!!)
            if (lastTouchUpTimeField != null) {
                instance.liveRoomPlayerViewClass?.declaredMethods?.filter { m ->
                    m.isPublic && m.returnType == Void.TYPE && m.parameterTypes.let { it.size == 1 && it[0] == MotionEvent::class.java }
                }?.forEach { m ->
                    m.hookAfterMethod { lastTouchUpTimeField.setLong(it.thisObject, 0L) }
                }
            }
        }
        if (!sPrefs.getBoolean("revert_live_room_feed", false)) {
            return
        }

        instance.liveKvConfigHelperClass?.hookAfterMethod(
            "getLocalValue",
            String::class.java
        ) { param ->
            if (param.args[0] == "live_new_room_setting") {
                if (param.result != null) {
                    val obj = (param.result as String).toJSONObject()
                    if (obj.get("all_new_room_enable") == "2") {
                        obj.put("all_new_room_enable", "0")
                        param.result = obj.toString()
                    }
                }
            }
        }
        instance.liveRoomActivityClass?.hookBeforeMethod(
            "onCreate",
            Bundle::class.java
        ) { param ->
            val intent = (param.thisObject as android.app.Activity).intent
            if (intent.getStringExtra("is_room_feed") == "1") {
                intent.putExtra("is_room_feed", "0")
                Log.toast("已强制直播间使用旧版样式")
            }
        }
    }
}
