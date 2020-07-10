package me.iacn.biliroaming.hook

import android.app.Activity
import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.SettingDialog
import me.iacn.biliroaming.utils.Log
import java.lang.reflect.Method
import java.lang.reflect.Proxy


class SettingHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val settingUri = "bilibili://biliroaming"

    override fun startHook() {
        Log.d("startHook: setting")
        findAndHookMethod("tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment",
                mClassLoader, instance.addSetting(), Context::class.java, List::class.java, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val itemClass = findClass("com.bilibili.lib.homepage.mine.MenuGroup\$Item", mClassLoader)
                    val item = newInstance(itemClass)
                    setIntField(item, "id", 114514)
                    setObjectField(item, "title", "哔哩漫游设置")
                    setObjectField(item, "icon", "https://i0.hdslb.com/bfs/album/cd6512ea4b8cf337253202f80f84f8b24fc9f485.png")
                    setObjectField(item, "uri", settingUri)
                    val lastGroup = (param.args[1] as MutableList<*>).last()
                    @Suppress("UNCHECKED_CAST")
                    (getObjectField(lastGroup, "itemList") as MutableList<Any>).add(item)
                }catch (e:Throwable) {
                    Log.e(e)
                }
            }
        })

        findAndHookMethod(instance.settingRouteClass, instance.getSettingRoute(), "com.bilibili.lib.homepage.mine.MenuGroup\$Item", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val item = param.args[0]
                val uri = getObjectField(item, "uri") as String
                if (uri != settingUri) return
                val returnType = (param.method as Method).returnType
                param.result = Proxy.newProxyInstance(returnType.classLoader, arrayOf(returnType)) { _, method, _ ->
                    val returnType2 = method.returnType
                    Proxy.newProxyInstance(returnType2.classLoader, arrayOf(returnType2)) { _, method2, args ->
                        when (method2.returnType) {
                            Boolean::class.javaPrimitiveType -> false
                            else -> {
                                if (method2.parameterTypes.isNotEmpty() &&
                                        method2.parameterTypes[0].name == "android.app.Activity") {
                                    val currentActivity = args[0] as Activity
                                    val settingDialog = SettingDialog(currentActivity)
                                    settingDialog.show()
                                }
                                null
                            }
                        }
                    }
                }
            }
        })
    }
}