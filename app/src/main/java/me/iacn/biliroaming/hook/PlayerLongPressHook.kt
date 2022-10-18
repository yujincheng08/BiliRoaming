package me.iacn.biliroaming.hook

import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import me.iacn.biliroaming.utils.sPrefs
import java.lang.reflect.Method

class PlayerLongPressHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private val replacement: XC_MethodReplacement =
            object : XC_MethodReplacement() {

        override fun replaceHookedMethod(param: MethodHookParam): Any {
            if(sPrefs.getBoolean("forbid_player_long_click_accelerate", false)) {
                return true
            }
            return XposedBridge.invokeOriginalMethod(param.method,
                    param.thisObject, param.args)
        }
    }

    override fun startHook() {
        replace1()
        replace2()
    }

    private inline fun doIgnoreException(action: () -> Unit) {
        try {
            action()
        } catch(t: Throwable) {
            //ignore
        }
    }

    private fun findMethod(clazz: Class<*>, methodName: String): Method? {
        for(method in clazz.declaredMethods) {
            if(method.name == methodName) return method
        }
        return null
    }

    private fun classNameListToClassList(vararg classNameList: String):
            List<Class<*>> {
        val classes = ArrayList<Class<*>>()
        classNameList.forEach {
            classes.add(mClassLoader.loadClass(it))
        }
        return classes
    }

    private fun hookInvokeMethod(vararg classNameList: String) {
        doIgnoreException {
            for(aClass in classNameListToClassList(*classNameList)) {
                for(method in aClass.declaredMethods) {
                    if(method.name != "invoke") continue
                    if(method.returnType == java.lang.Boolean::class.java ||
                            method.returnType == java.lang.Boolean::class
                                    .javaPrimitiveType) {
                        XposedBridge.hookMethod(method, replacement)
                    }
                }
            }
        }
    }

    /**
     * 6.59.0以前
     */
    private fun replace1() {
        val className = "tv.danmaku.biliplayerimpl.gesture" +
                ".GestureService\$mTouchListener\$1"
        doIgnoreException {
            val clazz: Class<*> = mClassLoader.loadClass(className)
            val method: Method = findMethod(clazz, "onLongPress")!!
            XposedBridge.hookMethod(method, replacement)
        }
    }

    /**
     * 6.59.0
     */
    private fun replace2() {
        val classeNames = arrayOf(
                "tv.danmaku.biliplayerimpl.gesture.GestureService" +
                        "\$initInnerLongPressListener\$1\$onLongPress\$1",
                "tv.danmaku.biliplayerimpl.gesture.GestureService" +
                        "\$initInnerLongPressListener\$1\$onLongPressEnd\$1"
        )
        hookInvokeMethod(*classeNames)
    }
}