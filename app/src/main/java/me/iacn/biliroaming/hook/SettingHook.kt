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

        instance.splashActivityClass?.hookBeforeMethod("onCreate", Bundle::class.java) { param ->
            val self = param.thisObject as Activity
            startSetting = self.intent.hasExtra(START_SETTING_KEY)
        }

        instance.mainActivityClass?.hookAfterMethod("onResume") { param ->
            if (startSetting) {
                startSetting = false
                SettingDialog.show(param.thisObject as Activity)
            }
        }

        instance.mainActivityClass?.hookBeforeMethod(
            "onCreate",
            Bundle::class.java
        ) { param ->
            val bundle = param.args[0] as? Bundle
            bundle?.remove("android:fragments")
        }

        instance.drawerClass?.hookAfterMethod(
            "onCreateView",
            LayoutInflater::class.java,
            ViewGroup::class.java,
            Bundle::class.java
        ) { param ->
            val navSettingId = getId("nav_settings")
            val nav =
                param.thisObject.javaClass.declaredFields.first { it.type.name == "android.support.design.widget.NavigationView" }.name
            (param.thisObject.getObjectField(nav)
                ?: param.result).callMethodAs<View>("findViewById", navSettingId)
                .setOnLongClickListener {
                    SettingDialog.show(param.thisObject.callMethodAs<Activity>("getActivity"))
                    true
                }
        }

        instance.homeCenters().forEach { (c, m) ->
            c?.hookBeforeAllMethods(m) { param ->
                @Suppress("UNCHECKED_CAST")
                val list = param.args[1] as? MutableList<Any>
                    ?: param.args[1]?.getObjectFieldOrNullAs<MutableList<Any>>("moreSectionList")
                    ?: return@hookBeforeAllMethods

                val itemList = list.lastOrNull()?.let {
                    if (it.javaClass != instance.menuGroupItemClass) it.getObjectFieldOrNullAs<MutableList<Any>>(
                        "itemList"
                    ) else list
                } ?: list

                val item = makeSettingItem() ?: return@hookBeforeAllMethods
                itemList.forEach {
                    if (try {
                            it.getIntField("id") == SETTING_ID
                        } catch (t: Throwable) {
                            it.getLongField("id") == SETTING_ID.toLong()
                        }
                    ) return@hookBeforeAllMethods
                }
                itemList.add(item)
            }
        }

        instance.settingRouterClass?.hookBeforeAllConstructors { param ->
            if (param.args[1] != SETTING_URI) return@hookBeforeAllConstructors
            val routerType = (param.method as Constructor<*>).parameterTypes[3]
            param.args[3] = Proxy.newProxyInstance(
                routerType.classLoader,
                arrayOf(routerType)
            ) { _, method, _ ->
                val returnType = method.returnType
                Proxy.newProxyInstance(
                    returnType.classLoader,
                    arrayOf(returnType)
                ) { _, method2, args ->
                    when (method2.returnType) {
                        Boolean::class.javaPrimitiveType -> false
                        else -> {
                            if (method2.parameterTypes.isNotEmpty() &&
                                method2.parameterTypes[0].name == "android.app.Activity"
                            ) {
                                val currentActivity = args[0] as Activity
                                SettingDialog.show(currentActivity)
                            }
                            null
                        }
                    }
                }
            }
        }

        // 8.97.0+: hook 菜单适配器 notify* 方法注入设置项
        instance.mineAdapterClass?.let { hookAdapterFallback(it) }
    }

    private fun hookAdapterFallback(adapterClass: Class<*>) {
        val menuGroupItemClass = instance.menuGroupItemClass ?: return
        val dataField = adapterClass.declaredFields.firstOrNull {
            List::class.java.isAssignableFrom(it.type)
        } ?: return
        dataField.isAccessible = true

        fun injectSettingItem(data: MutableList<*>) {
            if (data.isEmpty()) return

            // 去重：检查 data 中所有 MenuGroup 的 itemList 是否已含设置项
            for (group in data) {
                group?.getObjectFieldOrNullAs<MutableList<Any>>("itemList")?.forEach { item ->
                    if (item.javaClass == menuGroupItemClass &&
                        (try {
                            item.getIntField("id") == SETTING_ID
                        } catch (_: Throwable) {
                            item.getLongField("id") == SETTING_ID.toLong()
                        })
                    ) return
                }
            }

            val targetList = data.lastOrNull()
                ?.getObjectFieldOrNullAs<MutableList<Any>>("itemList") ?: return

            val item = makeSettingItem() ?: return
            targetList.add(if(targetList.isEmpty()) 0 else targetList.lastIndex, item)
        }

        // hook RecyclerView.Adapter 多个通知方法代替单一 notifyDataSetChanged
        // 8.97.0 的适配器使用 notifyItemRangeChanged 刷新数据
        listOf(
            "notifyDataSetChanged", "notifyItemRangeChanged",
            "notifyItemRangeInserted", "notifyItemRangeRemoved", "notifyItemChanged"
        ).forEach { methodName ->
            runCatching {
                adapterClass.getMethod(methodName).hookBeforeMethod { param ->
                    if (!adapterClass.isInstance(param.thisObject)) return@hookBeforeMethod
                    val data = dataField.get(param.thisObject) as? MutableList<*>
                        ?: return@hookBeforeMethod
                    injectSettingItem(data)
                }
            }
        }

        // 为注入的设置项绑定点击监听
        // 8.97.0+ 为双层 RecyclerView：外层分组适配器 → 内层条目适配器
        // 从外层 data 定位包含注入项的 MenuGroup，post 后遍历 View 树找到内层 RecyclerView
        fun bindSettingClick(holder: Any, position: Int, adapter: Any) {
            val data = dataField.get(adapter) as? List<*> ?: return
            val group = data.getOrNull(position) ?: return
            val itemList = group.getObjectFieldOrNullAs<List<*>>("itemList") ?: return
            itemList.find {
                it?.javaClass == menuGroupItemClass && it.getObjectField("uri") == SETTING_URI
            } ?: return
            val itemView = holder.getObjectFieldOrNullAs<View>("itemView") ?: return
            itemView.post {
                val rv = findFirstRecyclerView(itemView) ?: return@post
                val innerAdapter = rv.callMethodAs<Any?>("getAdapter") ?: return@post

                @Suppress("UNCHECKED_CAST")
                val innerData = innerAdapter.javaClass.declaredFields
                    .firstOrNull { List::class.java.isAssignableFrom(it.type) }
                    ?.also { it.isAccessible = true }
                    ?.get(innerAdapter) as? List<Any> ?: return@post
                val idx = innerData.indexOfFirst {
                    it.javaClass == menuGroupItemClass &&
                            it.getObjectField("uri") == SETTING_URI
                }
                if (idx < 0) return@post
                val lm = rv.callMethodAs<Any?>("getLayoutManager") ?: return@post
                val child = lm.callMethodAs<View?>("findViewByPosition", idx) ?: return@post
                child.setOnClickListener {
                    val ctx = it.context
                    if (ctx is Activity) SettingDialog.show(ctx)
                }
            }
        }

        // 2-param onBindViewHolder (首次 bind、全量刷新)
        adapterClass.methods.firstOrNull { m ->
            m.name == "onBindViewHolder" && m.parameterTypes.size == 2
        }?.hookAfterMethod { param ->
            bindSettingClick(param.args[0], param.args[1] as Int, param.thisObject)
        }
    }

    /**
     * 递归遍历 View 树，查找第一个 RecyclerView 实例。
     */
    private fun findFirstRecyclerView(root: View): View? {
        if (rvClass?.isInstance(root) == true) return root
        if (root !is ViewGroup) return null
        for (i in 0 until root.childCount) {
            findFirstRecyclerView(root.getChildAt(i))?.let { return it }
        }
        return null
    }

    private val rvClass: Class<*>? by lazy {
        "androidx.recyclerview.widget.RecyclerView".findClassOrNull(mClassLoader)
            ?: "android.support.v7.widget.RecyclerView".findClassOrNull(mClassLoader)
    }

    companion object {
        const val START_SETTING_KEY = "biliroaming_start_setting"
        const val SETTING_URI = "bilibili://biliroaming"
        const val SETTING_ID = 114514

        fun makeSettingItem(): Any? {
            val item = instance.menuGroupItemClass?.new() ?: return null
            try {
                item.setIntField("id", SETTING_ID)
            } catch (_: Throwable) {
                item.setLongField("id", SETTING_ID.toLong())
            }
            item.setObjectField("title", "哔哩漫游设置")
                .setObjectField(
                    "icon",
                    "https://i0.hdslb.com/bfs/album/276769577d2a5db1d9f914364abad7c5253086f6.png"
                )
                .setObjectField("uri", SETTING_URI)
            item.setIntField("visible", 1)
            return item
        }
    }
}
