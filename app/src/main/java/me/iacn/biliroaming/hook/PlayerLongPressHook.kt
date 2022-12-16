package me.iacn.biliroaming.hook

import android.view.MotionEvent
import me.iacn.biliroaming.utils.*

class PlayerLongPressHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("forbid_player_long_click_accelerate", false)) return

        Log.d("startHook: PlayerLongPress");

        val hooker: Hooker = { param -> param.result = true }
        // pre 6.59.0
        "tv.danmaku.biliplayerimpl.gesture.GestureService\$mTouchListener\$1".findClassOrNull(
            mClassLoader
        )?.hookBeforeMethod(
            "onLongPress",
            MotionEvent::class.java,
            hooker = hooker
        )
        // post 6.59.0
        arrayOf(
            "tv.danmaku.biliplayerimpl.gesture.GestureService\$initInnerLongPressListener\$1\$onLongPress\$1",
            "tv.danmaku.biliplayerimpl.gesture.GestureService\$initInnerLongPressListener\$1\$onLongPressEnd\$1"
        ).forEach { className ->
            className.hookBeforeMethod(mClassLoader, "invoke", Object::class.java, hooker = hooker)
        }
    }
}
