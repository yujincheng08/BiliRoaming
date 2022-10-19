package me.iacn.biliroaming.hook

import android.view.MotionEvent
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import me.iacn.biliroaming.utils.sPrefs

class PlayerLongPressHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private val enabled = sPrefs.getBoolean("forbid_player_long_click_accelerate", false)

    private val replacement = object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam): Any = true
    }

    override fun startHook() {
        if(!enabled) return
        //6.59.0以前
        val className = "tv.danmaku.biliplayerimpl.gesture" +
                ".GestureService\$mTouchListener\$1"
        var clazz = XposedHelpers.findClass(className, mClassLoader)
        XposedHelpers.findAndHookMethod(clazz, "onLongPress",
                MotionEvent::class.java, replacement)
        //6.59.0
        val classNames = arrayOf(
                "tv.danmaku.biliplayerimpl.gesture.GestureService\$" +
                        "initInnerLongPressListener\$1\$onLongPress\$1",
                "tv.danmaku.biliplayerimpl.gesture.GestureService\$" +
                        "initInnerLongPressListener\$1\$onLongPressEnd\$1"
        )
        classNames.forEach outer@{
            clazz = XposedHelpers.findClassIfExists(it, mClassLoader)
            clazz?.declaredMethods?.forEach inner@{ method ->
                if(method.name != "invoke") return@inner
                if(method.returnType == java.lang.Boolean::class.java ||
                   method.returnType == java.lang.Boolean::class.javaPrimitiveType) {
                    XposedBridge.hookMethod(method, replacement)
                }
            }
        }
    }
}
