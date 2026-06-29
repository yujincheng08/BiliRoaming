package me.iacn.biliroaming.hook.kotlinx

import me.iacn.biliroaming.hook.hometab.HomeTabHandler
import me.iacn.biliroaming.utils.Log

class KotlinxHomeTabProcessor : KotlinxProcessor {
    override val targetSerialName =
        "tv.danmaku.bili.khomeapi.frame.HomeTabResponse"

    override fun shouldEnable() = true

    override fun process(result: Any, deserializer: Any?) {
        val deser = deserializer ?: return
        runCatching {
            HomeTabHandler.mutate(KotlinxAccess(result, deser))
        }.onFailure { Log.e("KotlinxHomeTabProcessor error: $it") }
    }

    private class KotlinxAccess(result: Any, deserializer: Any) : HomeTabHandler.TabDataAccess {
        private val tabData = KotlinxNavigator(result, deserializer).child("data")
        override val bottom = tabData?.listAccess("bottom")
        override val tab = tabData?.listAccess("tab")
        override val top = tabData?.listAccess("top")

        @Suppress("UNCHECKED_CAST")
        private fun KotlinxNavigator.listAccess(name: String): HomeTabHandler.TabListAccess? {
            val list = get(name) as? MutableList<Any> ?: return null
            val desc = listElementDescriptor(name) ?: return null
            return TabList(list, desc)
        }
    }

    private class TabList(
        override val list: MutableList<Any>,
        private val desc: Any,
    ) : HomeTabHandler.TabListAccess {
        private fun nav(item: Any) = KotlinxNavigator.of(item, desc)
        override fun uri(item: Any) = nav(item).get("uri") as? String
        override fun id(item: Any) = nav(item).get("id") as? String
        override fun name(item: Any) = nav(item).get("name") as? String
        override fun addNewItem(tabId: String, name: String, uri: String, reportId: String, pos: Int) {
            val itemClass = list.firstOrNull()?.javaClass ?: return
            val item = itemClass.getDeclaredConstructor().newInstance()
            KotlinxNavigator.of(item, desc).apply {
                set("id", tabId)
                set("name", name)
                set("uri", uri)
                set("tab_id", reportId)
                setInt("pos", pos)
            }
            list.add(item)
        }
    }
}
