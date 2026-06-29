package me.iacn.biliroaming.hook.hometab

/** 底栏项 / 我的页面项的展示数据（设置界面「自定义底栏」「自定义我的页面」共用） */
data class BottomItem(
    val name: String?,
    val uri: String?,
    val id: String?,
    var showing: Boolean,
)
