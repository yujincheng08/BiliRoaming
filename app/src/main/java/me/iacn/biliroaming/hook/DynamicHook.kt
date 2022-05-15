package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.*

class DynamicHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private val purifyTypes by lazy {
        sPrefs.getStringSet("customize_dynamic_type", null)
            ?.flatMap { it.split(',') }
            ?.map { it.toInt() } ?: listOf()
    }
    private val purifyContents by lazy {
        sPrefs.getString("customize_dynamic_keywords_content", null)
            ?.split("|")?.filter { it.isNotBlank() } ?: listOf()
    }
    private val purifyUpNames by lazy {
        sPrefs.getString("customize_dynamic_keywords_upname", null)
            ?.split("|")?.filter { it.isNotBlank() } ?: listOf()
    }
    private val purifyUidList by lazy {
        sPrefs.getString("customize_dynamic_keywords_uid", null)
            ?.split("|")?.mapNotNull { it.toLongOrNull() } ?: listOf()
    }

    override fun startHook() {
        if (purifyTypes.isEmpty() && purifyContents.isEmpty()
            && purifyUpNames.isEmpty() && purifyUidList.isEmpty()
        ) return

        "com.bapis.bilibili.app.dynamic.v2.DynamicMoss".hookAfterMethod(
            mClassLoader,
            "dynAll",
            "com.bapis.bilibili.app.dynamic.v2.DynAllReq"
        ) { param ->
            val dnyAllReply = param.result
                ?: return@hookAfterMethod
            val dynamicList = dnyAllReply.callMethod("getDynamicList")
                ?: return@hookAfterMethod
            val contentList = dynamicList.callMethodAs<List<*>?>("getListList")
                ?: return@hookAfterMethod
            val idxList = mutableSetOf<Int>()
            contentList.forEachIndexed { idx, e ->
                if (purifyTypes.isNotEmpty()) {
                    val cardType = e?.callMethodAs<Int>("getCardTypeValue") ?: -1
                    if (purifyTypes.contains(cardType)) {
                        idxList.add(idx)
                    }
                }

                val extend = e?.callMethod("getExtend")

                if (purifyContents.isNotEmpty()) {
                    val contentOrig = extend
                        ?.callMethodAs<List<*>>("getOrigDescList")
                        ?.map {
                            it?.callMethodAs<String>("getOrigText") ?: ""
                        }?.filter { it.isNotEmpty() }
                        ?.joinToString(separator = "|") ?: ""
                    if (contentOrig.isNotEmpty() && purifyContents.any { contentOrig.contains(it) }) {
                        idxList.add(idx)
                    }
                }

                if (purifyContents.isNotEmpty()) {
                    val content = extend
                        ?.callMethodAs<List<*>>("getDescList")
                        ?.map {
                            it?.callMethodAs<String>("getOrigText") ?: ""
                        }?.filter { it.isNotEmpty() }
                        ?.joinToString(separator = "|") ?: ""
                    if (content.isNotEmpty() && purifyContents.any { content.contains(it) }) {
                        idxList.add(idx)
                    }
                }

                if (purifyUpNames.isNotEmpty()) {
                    val origName = extend?.callMethodAs<String>("getOrigName") ?: ""
                    if (origName.isNotEmpty() && purifyUpNames.any { origName == it }) {
                        idxList.add(idx)
                    }
                }

                if (purifyUidList.isNotEmpty()) {
                    val uid = extend?.callMethodAs<Long>("getUid") ?: 0L
                    if (uid > 0L && purifyUidList.any { uid == it }) {
                        idxList.add(idx)
                    }
                }
            }
            idxList.reversed().forEach {
                dynamicList.callMethod("removeList", it)
            }
        }
    }
}
