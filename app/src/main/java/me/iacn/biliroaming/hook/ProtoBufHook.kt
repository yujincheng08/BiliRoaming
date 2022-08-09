package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.*

class ProtoBufHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        val hidden = sPrefs.getBoolean("hidden", false)
        val purifyCity = sPrefs.getBoolean("purify_city", false)
        val removeRelatePromote = sPrefs.getBoolean("remove_video_relate_promote", false)
        val removeRelateOnlyAv = sPrefs.getBoolean("remove_video_relate_only_av", false)
        val removeCmdDms = sPrefs.getBoolean("remove_video_cmd_dms", false)
        val purifySearch = sPrefs.getBoolean("purify_search", false)
        val purifyCampus = sPrefs.getBoolean("purify_campus", false)
        val blockWordSearch = sPrefs.getBoolean("block_word_search", false)

        if (hidden && (purifyCity || purifyCampus)) {
            listOf(
                "com.bapis.bilibili.app.dynamic.v1.DynTabReply",
                "com.bapis.bilibili.app.dynamic.v2.DynTabReply"
            ).forEach { clazz ->
                clazz.hookAfterMethod(
                    mClassLoader,
                    "getDynTabList"
                ) { param ->
                    param.result = (param.result as List<*>).filterNot {
                        purifyCity && it?.callMethodAs<Long>("getCityId") != 0L
                                || purifyCampus && it?.callMethodAs<String>("getAnchor") == "campus"
                    }
                }
            }
        }

        "com.bapis.bilibili.app.view.v1.ViewMoss".hookAfterMethod(
            mClassLoader,
            "view",
            "com.bapis.bilibili.app.view.v1.ViewReq"
        ) { param ->
            param.result ?: return@hookAfterMethod
            buildSet {
                param.result.callMethodAs<List<*>?>("getRelatesList")
                    ?.onEachIndexed { idx, r ->
                        if (hidden && removeRelatePromote
                            && (r?.callMethodAs<Long?>("getFromSourceType") == 2L ||
                                    r?.callMethodAs<String?>("getGoto") == "cm")
                        ) add(idx)
                        if (hidden && removeRelatePromote && removeRelateOnlyAv
                            && r?.callMethodAs<String?>("getGoto").let { it != "av" }
                        ) add(idx)
                    }
            }.reversed().forEach {
                param.result.callMethod("removeRelates", it)
            }
        }

        if (hidden && removeCmdDms) {
            "com.bapis.bilibili.app.view.v1.ViewMoss".hookAfterMethod(
                mClassLoader,
                "viewProgress",
                "com.bapis.bilibili.app.view.v1.ViewProgressReq"
            ) { param ->
                param.result?.callMethod("getVideoGuide")?.run {
                    callMethod("clearAttention")
                    callMethod("clearCommandDms")
                }
            }
        }
        if (hidden && purifySearch) {
            "com.bapis.bilibili.app.interfaces.v1.SearchMoss".hookAfterMethod(
                mClassLoader,
                "defaultWords",
                "com.bapis.bilibili.app.interfaces.v1.DefaultWordsReq"
            ) { param ->
                param.result = null
            }
        }
        if (hidden && blockWordSearch) {
            "com.bapis.bilibili.main.community.reply.v1.Content".hookAfterMethod(
                mClassLoader,
                "internalGetUrls"
            ) { param ->
                (param.result as LinkedHashMap<*, *>?)?.let { m ->
                    val iterator = m.iterator()
                    while (iterator.hasNext()) {
                        iterator.next().value.callMethodAs<String?>("getAppUrlSchema")
                            ?.takeIf {
                                it.startsWith("bilibili://search?from=appcommentline_search")
                            }?.run {
                                iterator.remove()
                            }
                    }
                }
            }
        }
    }
}
