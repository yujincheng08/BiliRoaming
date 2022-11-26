package me.iacn.biliroaming.hook

import android.os.Bundle
import android.view.MotionEvent
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class LiveRoomHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (sPrefs.getBoolean("forbid_switch_live_room", false)) {
            instance.livePagerRecyclerViewClass?.replaceMethod(
                "onInterceptTouchEvent",
                MotionEvent::class.java
            ) { false }
        }
        if (sPrefs.getBoolean("disable_live_room_double_click", false)) {
            instance.liveRoomPlayerViewClass?.hookBeforeMethod("onDoubleTap") { param ->
                runCatching {
                    val player = param.thisObject.callMethod("getPlayerCommonBridge")
                        ?: return@hookBeforeMethod
                    val method = if (player.callMethodAs("isPlaying"))
                        "pause" else "resume"
                    player.callMethod(method)
                }.onSuccess { param.result = true }
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
