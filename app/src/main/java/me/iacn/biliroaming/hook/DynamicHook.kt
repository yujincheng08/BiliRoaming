package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.*

class DynamicHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private val purifyTypes by lazy {
        sPrefs.getStringSet("customize_dynamic_type", null)
            ?.map { it.toInt() } ?: listOf()
    }
    private val purifyContents by lazy {
        sPrefs.getStringSet("customize_dynamic_keyword_content", null) ?: setOf()
    }
    private val purifyUpNames by lazy {
        sPrefs.getStringSet("customize_dynamic_keyword_upname", null) ?: setOf()
    }
    private val purifyUidList by lazy {
        sPrefs.getStringSet("customize_dynamic_keyword_uid", null)
            ?.mapNotNull { it.toLongOrNull() } ?: setOf()
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

                if (purifyContents.isNotEmpty()) {
                    val modulesText = e?.callMethodAs<List<*>>("getModulesList")
                        ?.joinToString(separator = "") {
                            it?.callMethod("getModuleDesc")
                                ?.callMethodAs<String>("getText") ?: ""
                        } ?: ""
                    if (modulesText.isNotEmpty() && purifyContents.any { modulesText.contains(it) }) {
                        idxList.add(idx)
                    }
                }

                val extend = e?.callMethod("getExtend")

                if (purifyContents.isNotEmpty()) {
                    val contentOrig = extend
                        ?.callMethodAs<List<*>>("getOrigDescList")
                        ?.joinToString(separator = "") {
                            it?.callMethodAs<String>("getOrigText") ?: ""
                        } ?: ""
                    if (contentOrig.isNotEmpty() && purifyContents.any { contentOrig.contains(it) }) {
                        idxList.add(idx)
                    }
                }

                if (purifyContents.isNotEmpty()) {
                    val content = extend
                        ?.callMethodAs<List<*>>("getDescList")
                        ?.joinToString(separator = "") {
                            it?.callMethodAs<String>("getOrigText") ?: ""
                        } ?: ""
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
