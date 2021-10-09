package me.iacn.biliroaming.hook

import android.os.Bundle
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.sPrefs

class LiveRoomHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("anti_live_room_feed", false)) return
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
