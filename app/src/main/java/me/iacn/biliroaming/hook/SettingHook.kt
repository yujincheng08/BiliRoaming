package me.iacn.biliroaming.hook

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.SettingDialog
import me.iacn.biliroaming.utils.*
import java.lang.reflect.Method
import java.lang.reflect.Proxy


class SettingHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val settingUri = "bilibili://biliroaming"
    private var startSetting = false

    override fun startHook() {
        Log.d("startHook: Setting")

        "tv.danmaku.bili.ui.splash.SplashActivity".hookBeforeMethod(mClassLoader, "onCreate", Bundle::class.java) { param ->
            val self = param.thisObject as Activity
            startSetting = self.intent.hasExtra(START_SETTING_KEY)
        }

        "tv.danmaku.bili.MainActivityV2".hookAfterMethod(mClassLoader, "onResume") {param->
            if(startSetting) {
                startSetting = false
                SettingDialog(param.thisObject as Activity).show()
            }
        }

        instance.drawerClass?.hookAfterMethod("onCreateView", LayoutInflater::class.java, ViewGroup::class.java, Bundle::class.java) { param ->
            val activity = param.thisObject.callMethodAs<Activity>("getActivity")
            val navSettingId = activity.resources.getIdentifier("${activity.packageName}:id/nav_settings", null, null)
            val nav = param.thisObject.javaClass.declaredFields.first { it.type.name == "android.support.design.widget.NavigationView" }.name
            param.thisObject.getObjectField(nav)?.callMethodAs<View>("findViewById", navSettingId)?.setOnLongClickListener {
                SettingDialog(param.thisObject.callMethodAs<Activity>("getActivity")).show()
                true
            }
        }

        "tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment".findClassOrNull(mClassLoader)?.hookBeforeMethod(
                instance.addSetting(), Context::class.java, List::class.java) { param ->
            val item = "com.bilibili.lib.homepage.mine.MenuGroup\$Item".findClass(mClassLoader)?.new()
                    ?: return@hookBeforeMethod
            item.setIntField("id", 114514)
                    .setObjectField("title", "哔哩漫游设置")
                    .setObjectField("icon", "https://i0.hdslb.com/bfs/album/cd6512ea4b8cf337253202f80f84f8b24fc9f485.png")
                    .setObjectField("uri", settingUri)
            val lastGroup = (param.args[1] as MutableList<*>).last() ?: return@hookBeforeMethod
            lastGroup.getObjectFieldAs<MutableList<Any>>("itemList").add(item)
        }

        instance.settingRouteClass?.hookAfterMethod(instance.getSettingRoute(), "com.bilibili.lib.homepage.mine.MenuGroup\$Item") { param ->
            val item = param.args[0]
            val uri = item.getObjectFieldAs<String>("uri")
            if (uri != settingUri) return@hookAfterMethod
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
                                SettingDialog(currentActivity).show()
                            }
                            null
                        }
                    }
                }
            }
        }
    }
    companion object{
        const val START_SETTING_KEY = "biliroaming_start_setting"
    }
}