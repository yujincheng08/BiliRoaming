package me.iacn.biliroaming.hook

import android.os.Bundle
import android.view.MotionEvent
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class LiveRoomHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (sPrefs.getBoolean("forbid_switch_live_room", false)) {
            instance.livePagerRecyclerViewClass?.hookMethod(
                "onInterceptTouchEvent",
                MotionEvent::class.java
            ) { false }
        }
        if (sPrefs.getBoolean("disable_live_room_double_click", false)) {
            instance.liveRoomPlayerViewClass?.declaredMethods?.find { it.name == "onDoubleTap" }
                ?.hookMethod { chain ->
                    runCatching {
                        val player = chain.thisObject!!.callMethod("getPlayerCommonBridge")
                            ?: return@hookMethod chain.proceed()
                        val method = if (player.callMethodAs("isPlaying"))
                            "pause" else "resume"
                        player.callMethod(method)
                    }.onSuccess { return@hookMethod true }
                    chain.proceed()
                }
            val lastTouchUpTimeField =
                instance.liveRoomPlayerViewClass?.findFirstFieldByExactTypeOrNull(Long::class.javaPrimitiveType!!)
            if (lastTouchUpTimeField != null) {
                instance.liveRoomPlayerViewClass?.declaredMethods?.filter { m ->
                    m.isPublic && m.returnType == Void.TYPE && m.parameterTypes.let { it.size == 1 && it[0] == MotionEvent::class.java }
                }?.forEach { m ->
                    m.hookMethod { chain ->
                        val result = chain.proceed()
                        lastTouchUpTimeField.setLong(chain.thisObject, 0L)
                        result
                    }
                }
            }
        }
        if (!sPrefs.getBoolean("revert_live_room_feed", false)) {
            return
        }

        instance.liveKvConfigHelperClass?.hookMethod(
            "getLocalValue",
            String::class.java
        ) { chain ->
            var result = chain.proceed()
            if (chain.args[0] == "live_new_room_setting") {
                if (result != null) {
                    val obj = (result as String).toJSONObject()
                    if (obj.get("all_new_room_enable") == "2") {
                        obj.put("all_new_room_enable", "0")
                        result = obj.toString()
                    }
                }
            }
            result
        }
        instance.liveRoomActivityClass?.hookMethod(
            "onCreate",
            Bundle::class.java
        ) { chain ->
            val intent = (chain.thisObject as android.app.Activity).intent
            if (intent.getStringExtra("is_room_feed") == "1") {
                intent.putExtra("is_room_feed", "0")
                Log.toast("已强制直播间使用旧版样式")
            }
            chain.proceed()
        }
    }
}
