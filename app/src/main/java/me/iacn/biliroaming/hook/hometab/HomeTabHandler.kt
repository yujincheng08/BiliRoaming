package me.iacn.biliroaming.hook.hometab

import me.iacn.biliroaming.utils.sPrefs

/** 底栏+顶栏业务规则（与序列化器无关）。FastJSON 与 kotlinx 两条路径共用。
 *  逻辑来源：原 JsonHook.processTabResponse/configTab + KotlinxHomeTabProcessor，合并去重。 */
object HomeTabHandler {
    /** 底栏列表状态（FastJSON/kotlinx 两条路径都填充；SettingDialog 读取） */
    val bottomItems = mutableListOf<BottomItem>()

    /** 单个 tab 列表（bottom/tab/top）及其元素的字段访问能力。
     *  FastJSON 用 reflect(@JSONField) 实现，kotlinx 用 descriptor 导航实现。 */
    interface TabListAccess {
        val list: MutableList<Any>
        fun uri(item: Any): String?
        fun id(item: Any): String?
        fun name(item: Any): String?
        /** 创建新顶栏 tab 项并加入列表（仅 tab 列表用到） */
        fun addNewItem(tabId: String, name: String, uri: String, reportId: String, pos: Int)
    }

    /** TabResponse 反序列化结果的数据访问入口，按列表分段。 */
    interface TabDataAccess {
        val bottom: TabListAccess?
        val tab: TabListAccess?
        val top: TabListAccess?
    }

    fun mutate(access: TabDataAccess) {
        handleBottom(access.bottom)
        access.tab?.let { configTab(it) }
        if (sPrefs.getBoolean("purify_game", false) && sPrefs.getBoolean("hidden", false)) {
            access.top?.let { top ->
                top.list.removeAll { item ->
                    top.uri(item)?.startsWith("bilibili://game_center/home") ?: false
                }
            }
        }
    }

    private fun handleBottom(bottom: TabListAccess?) {
        val b = bottom ?: return
        bottomItems.clear()
        val hides = sPrefs.getStringSet("hided_bottom_items", mutableSetOf())!!
        b.list.removeAll { item ->
            val id = b.id(item)
            val uri = b.uri(item)
            val name = b.name(item)
            val showing = id !in hides
            bottomItems.add(BottomItem(name, uri, id, showing))
            showing.not()
        }
        if (sPrefs.getBoolean("drawer", false) && !sPrefs.getBoolean("hidden", false)) {
            b.list.removeAll { item ->
                b.uri(item)?.startsWith("bilibili://user_center/mine") ?: false
            }
        }
    }

    private fun configTab(tab: TabListAccess) {
        var hasBangumiCN = false
        var hasBangumiTW = false
        var hasMovieCN = false
        var hasMovieTW = false
        var hasKoreaHK = false
        var hasKoreaTW = false
        tab.list.forEach { item ->
            when (tab.uri(item)) {
                "bilibili://pgc/bangumi_v2", "bilibili://pgc/home" -> hasBangumiCN = true
                "bilibili://following/home_activity_tab/6544" -> hasBangumiTW = true
                "bilibili://pgc/cinema_v2", "bilibili://pgc/home?home_flow_type=2" -> hasMovieCN = true
                "bilibili://following/home_activity_tab/168644" -> hasMovieTW = true
                "bilibili://following/home_activity_tab/163541" -> hasKoreaHK = true
                "bilibili://following/home_activity_tab/95636" -> hasKoreaTW = true
            }
        }
        if (sPrefs.getBoolean("add_bangumi", false)) {
            if (!hasBangumiCN) tab.addNewItem("50", "追番（大陸）", "bilibili://pgc/home", "bangumi", 50)
            if (!hasBangumiTW) tab.addNewItem("60", "追番（港澳台）", "bilibili://following/home_activity_tab/6544", "bangumi", 60)
        }
        if (sPrefs.getBoolean("add_movie", false)) {
            if (!hasMovieCN) tab.addNewItem("70", "影視（大陸）", "bilibili://pgc/home?home_flow_type=2", "film", 70)
            if (!hasMovieTW) tab.addNewItem("80", "戏剧（港澳台）", "bilibili://following/home_activity_tab/168644", "jptv", 80)
        }
        if (sPrefs.getBoolean("add_korea", false)) {
            if (!hasKoreaHK) tab.addNewItem("803", "韩综（港澳）", "bilibili://following/home_activity_tab/163541", "koreavhk", 803)
            if (!hasKoreaTW) tab.addNewItem("804", "韩综（台湾）", "bilibili://following/home_activity_tab/95636", "koreavtw", 804)
        }
        val purifytabset = sPrefs.getStringSet("customize_home_tab", emptySet())!!
        if (purifytabset.isEmpty()) return
        tab.list.removeAll { item ->
            when (tab.uri(item)) {
                "bilibili://live/home" -> purifytabset.contains("live")
                "bilibili://pegasus/promo" -> purifytabset.contains("promo")
                "bilibili://pegasus/hottopic" -> purifytabset.contains("hottopic")
                "bilibili://pgc/bangumi_v2", "bilibili://pgc/home",
                "bilibili://following/home_activity_tab/6544" -> purifytabset.contains("bangumi")
                "bilibili://pgc/cinema_v2", "bilibili://pgc/home?home_flow_type=2",
                "bilibili://following/home_activity_tab/168644" -> purifytabset.contains("movie")
                "bilibili://following/home_activity_tab/95636",
                "bilibili://following/home_activity_tab/163541" -> purifytabset.contains("korea")
                else -> purifytabset.contains("other_tabs")
            }
        }
    }
}
