package me.iacn.biliroaming.hook.json

import me.iacn.biliroaming.hook.hometab.HomeTabHandler
import me.iacn.biliroaming.utils.*

/** 处理旧版 MainResourceManager.TabResponse（FastJSON 反序列化结果，默认激活路径）。
 *  由 JsonHook.earlyHook 在 onPackageReady 注册的 parseObject hook 分发调用。 */
class HomeTabProcessor : JsonProcessor {
    override val targetClassName =
        "tv.danmaku.bili.ui.main2.resource.MainResourceManager\$TabResponse"

    private lateinit var tabClass: Class<*>

    override fun shouldEnable() = true

    override fun init(classLoader: ClassLoader): Boolean {
        tabClass = "tv.danmaku.bili.ui.main2.resource.MainResourceManager\$Tab"
            .findClassOrNull(classLoader) ?: return false
        return true
    }

    override fun process(result: Any, classLoader: ClassLoader) {
        // earlyHook 时机（onPackageReady）可能早于 Application 就绪（attachBaseContext）；
        // HomeTabHandler 读 sPrefs 会触发 currentContext（依赖 Application），未就绪时静默跳过——
        runCatching { currentContext }.getOrNull() ?: return
        HomeTabHandler.mutate(FastJsonAccess(result, tabClass))
    }

    private class FastJsonAccess(
        result: Any,
        private val tabClass: Class<*>?,
    ) : HomeTabHandler.TabDataAccess {
        private val tabData = result.getObjectField("tabData")
        override val bottom: HomeTabHandler.TabListAccess? = tabData?.listOf("bottom")?.let { TabList(it, null) }
        override val tab: HomeTabHandler.TabListAccess? = tabData?.listOf("tab")?.let { TabList(it, tabClass) }
        override val top: HomeTabHandler.TabListAccess? = tabData?.listOf("top")?.let { TabList(it, null) }

        @Suppress("UNCHECKED_CAST")
        private fun Any.listOf(name: String): MutableList<Any>? =
            getObjectFieldAs<MutableList<*>?>(name) as? MutableList<Any>
    }

    private class TabList(
        override val list: MutableList<Any>,
        private val itemClass: Class<*>?,
    ) : HomeTabHandler.TabListAccess {
        override fun uri(item: Any) = item.getObjectFieldAs<String?>("uri")
        override fun id(item: Any) = item.getObjectFieldAs<String?>("tabId")
        override fun name(item: Any) = item.getObjectFieldAs<String?>("name")
        override fun addNewItem(tabId: String, name: String, uri: String, reportId: String, pos: Int) {
            val item = itemClass?.new()
                ?.setObjectField("tabId", tabId)
                ?.setObjectField("name", name)
                ?.setObjectField("uri", uri)
                ?.setObjectField("reportId", reportId)
                ?.setIntField("pos", pos) ?: return
            list.add(item)
        }
    }
}
