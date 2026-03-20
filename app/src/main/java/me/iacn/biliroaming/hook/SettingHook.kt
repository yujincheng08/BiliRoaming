package me.iacn.biliroaming.hook

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.SettingDialog
import me.iacn.biliroaming.utils.*
import java.lang.reflect.Constructor
import java.lang.reflect.Proxy


class SettingHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private var startSetting = false

    override fun startHook() {
        Log.d("startHook: Setting")

        instance.splashActivityClass?.hookMethod("onCreate", Bundle::class.java) { chain ->
            val self = chain.thisObject as Activity
            startSetting = self.intent.hasExtra(START_SETTING_KEY)
            chain.proceed()
        }

        instance.mainActivityClass?.hookMethod("onResume") { chain ->
            val result = chain.proceed()
            if (startSetting) {
                startSetting = false
                SettingDialog.show(chain.thisObject as Activity)
            }
            result
        }

        instance.mainActivityClass?.hookMethod(
            "onCreate",
            Bundle::class.java
        ) { chain ->
            val bundle = chain.args[0] as? Bundle
            bundle?.remove("android:fragments")
            chain.proceed()
        }

        instance.drawerClass?.hookMethod(
            "onCreateView",
            LayoutInflater::class.java,
            ViewGroup::class.java,
            Bundle::class.java
        ) { chain ->
            val result = chain.proceed()
            val navSettingId = getId("nav_settings")
            val nav =
                chain.thisObject!!.javaClass.declaredFields.first { it.type.name == "android.support.design.widget.NavigationView" }.name
            (chain.thisObject!!.getObjectField(nav)
                ?: result)!!.callMethodAs<View>("findViewById", navSettingId)
                .setOnLongClickListener {
                    SettingDialog.show(chain.thisObject!!.callMethodAs<Activity>("getActivity"))
                    true
                }
            result
        }

        instance.homeCenters().forEach { (c, m) ->
            c?.hookAllMethods(m) { chain ->
                @Suppress("UNCHECKED_CAST")
                val list = chain.args[1] as? MutableList<Any>
                    ?: chain.args[1]?.getObjectFieldOrNullAs<MutableList<Any>>("moreSectionList")
                    ?: return@hookAllMethods chain.proceed()

                val itemList = list.lastOrNull()?.let {
                    if (it.javaClass != instance.menuGroupItemClass) it.getObjectFieldOrNullAs<MutableList<Any>>(
                        "itemList"
                    ) else list
                } ?: list

                val item = instance.menuGroupItemClass?.new() ?: return@hookAllMethods chain.proceed()
                item.setIntField("id", SETTING_ID)
                    .setObjectField("title", "哔哩漫游设置")
                    .setObjectField(
                        "icon",
                        "https://i0.hdslb.com/bfs/album/276769577d2a5db1d9f914364abad7c5253086f6.png"
                    )
                    .setObjectField("uri", SETTING_URI)
                    .setIntField("visible", 1)
                itemList.forEach {
                    if (try {
                            it.getIntField("id") == SETTING_ID
                        } catch (t: Throwable) {
                            it.getLongField("id") == SETTING_ID.toLong()
                        }
                    ) return@hookAllMethods chain.proceed()
                }
                itemList.add(item)
                chain.proceed()
            }
        }

        instance.settingRouterClass?.hookAllConstructors { chain ->
            if (chain.args[1] != SETTING_URI) return@hookAllConstructors chain.proceed()
            val routerType = (chain.executable as Constructor<*>).parameterTypes[3]
            val args = chain.args.toTypedArray()
            args[3] = Proxy.newProxyInstance(
                routerType.classLoader,
                arrayOf(routerType)
            ) { _, method, _ ->
                val returnType = method.returnType
                Proxy.newProxyInstance(
                    returnType.classLoader,
                    arrayOf(returnType)
                ) { _, method2, args2 ->
                    when (method2.returnType) {
                        Boolean::class.javaPrimitiveType -> false
                        else -> {
                            if (method2.parameterTypes.isNotEmpty() &&
                                method2.parameterTypes[0].name == "android.app.Activity"
                            ) {
                                val currentActivity = args2[0] as Activity
                                SettingDialog.show(currentActivity)
                            }
                            null
                        }
                    }
                }
            }
            chain.proceed(args)
        }
    }

    companion object {
        const val START_SETTING_KEY = "biliroaming_start_setting"
        const val SETTING_URI = "bilibili://biliroaming"
        const val SETTING_ID = 114514
    }
}
