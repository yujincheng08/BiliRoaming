package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class DynamicHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private val dynamicMossV1 by Weak { "com.bapis.bilibili.app.dynamic.v1.DynamicMoss" from mClassLoader }
    private val dynamicMossV2 by Weak { "com.bapis.bilibili.app.dynamic.v2.DynamicMoss" from mClassLoader }

    private val purifyTypes = run {
        sPrefs.getStringSet("customize_dynamic_type", null)
            ?.map { it.toInt() }.orEmpty()
    }
    private val purifyContents = run {
        sPrefs.getStringSet("customize_dynamic_keyword_content", null).orEmpty()
    }
    private val purifyContentRegexes by lazy { purifyContents.map { it.toRegex() } }
    private val contentRegexMode = sPrefs.getBoolean("dynamic_content_regex_mode", false)
    private val purifyUpNames = run {
        sPrefs.getStringSet("customize_dynamic_keyword_upname", null).orEmpty()
    }
    private val purifyUidList = run {
        sPrefs.getStringSet("customize_dynamic_keyword_uid", null)
            ?.mapNotNull { it.toLongOrNull() }.orEmpty()
    }
    private val removeTopicOfAll = sPrefs.getBoolean("customize_dynamic_all_rm_topic", false)
    private val removeUpOfAll = sPrefs.getBoolean("customize_dynamic_all_rm_up", false)
    private val removeUpOfVideo = sPrefs.getBoolean("customize_dynamic_video_rm_up", false)
    private val preferVideoTab = sPrefs.getBoolean("prefer_video_tab", false)
    private val filterApplyToVideo = sPrefs.getBoolean("filter_apply_to_video", false)
    private val rmBlocked = sPrefs.getBoolean("customize_dynamic_rm_blocked", false)
    private val rmAdLink = sPrefs.getBoolean("customize_dynamic_rm_ad_link", false)

    private val needFilterDynamic = purifyTypes.isNotEmpty() || purifyContents.isNotEmpty()
            || purifyUpNames.isNotEmpty() || purifyUidList.isNotEmpty() || rmBlocked

    override fun startHook() {
        val hidden = sPrefs.getBoolean("hidden", false)
        if (hidden && (needFilterDynamic || removeTopicOfAll || removeUpOfAll)) {
            dynamicMossV2?.hookAfterMethod(
                if (instance.useNewMossFunc) "executeDynAll" else "dynAll",
                "com.bapis.bilibili.app.dynamic.v2.DynAllReq"
            ) { param ->
                param.result?.let {
                    if (removeTopicOfAll)
                        it.callMethod("clearTopicList")
                    if (removeUpOfAll)
                        it.callMethod("clearUpList")
                    if (needFilterDynamic)
                        filterDynamic(it)
                }
            }
            dynamicMossV2?.hookBeforeMethod(
                "dynAll",
                "com.bapis.bilibili.app.dynamic.v2.DynAllReq",
                instance.mossResponseHandlerClass
            ) {
                it.args[1] = it.args[1].mossResponseHandlerProxy { result ->
                    result?.let {
                        if (removeTopicOfAll)
                            it.callMethod("clearTopicList")
                        if (removeUpOfAll)
                            it.callMethod("clearUpList")
                        if (needFilterDynamic)
                            filterDynamic(it)
                    }
                }
            }
        }
        if (hidden && ((filterApplyToVideo && needFilterDynamic) || removeUpOfVideo)) {
            dynamicMossV2?.hookAfterMethod(
                if (instance.useNewMossFunc) "executeDynVideo" else "dynVideo",
                "com.bapis.bilibili.app.dynamic.v2.DynVideoReq"
            ) { param ->
                param.result?.let {
                    if (removeUpOfVideo)
                        it.callMethod("clearVideoUpList")
                    if (filterApplyToVideo && needFilterDynamic)
                        filterDynamic(it)
                }
            }
            dynamicMossV2?.hookBeforeMethod(
                if (instance.useNewMossFunc) "executeDynVideo" else "dynVideo",
                "com.bapis.bilibili.app.dynamic.v2.DynVideoReq",
                instance.mossResponseHandlerClass
            ) {
                it.args[1] = it.args[1].mossResponseHandlerProxy { result ->
                    result?.let {
                        if (removeUpOfVideo)
                            it.callMethod("clearVideoUpList")
                        if (filterApplyToVideo && needFilterDynamic)
                            filterDynamic(it)
                    }
                }
            }
        }
        if (hidden && preferVideoTab) {
            dynamicMossV1?.hookAfterMethod(
                if (instance.useNewMossFunc) "executeDynRed" else "dynRed",
                "com.bapis.bilibili.app.dynamic.v1.DynRedReq"
            ) { param ->
                param.result?.callMethod("setDefaultTab", "video")
            }
            dynamicMossV2?.hookBeforeMethod(
                if (instance.useNewMossFunc) "executeDynTab" else "dynTab",
                "com.bapis.bilibili.app.dynamic.v2.DynTabReq",
                instance.mossResponseHandlerClass
            ) {
                it.args[1] = it.args[1].mossResponseHandlerReplaceProxy { reply ->
                    reply?.callMethod("ensureScreenTabIsMutable")
                    reply?.callMethodAs<MutableList<Any>>("getScreenTabList")?.map {
                        it.callMethod(
                            "setDefaultTab",
                            it.callMethodAs<String>("getName") == "video"
                        )
                    }
                    reply
                }
            }
        }
    }

    private fun filterDynamic(reply: Any) {
        val dynamicList = reply.callMethod("getDynamicList") ?: return
        dynamicList.callMethod("ensureListIsMutable")
        val contentList = dynamicList.callMethodAs<MutableList<Any>>("getListList")
        val idxList = mutableSetOf<Int>()
        for ((idx, e) in contentList.withIndex()) {
            if (purifyTypes.isNotEmpty()) {
                val cardType = e.callMethodAs<Int>("getCardTypeValue")
                if (purifyTypes.contains(cardType)) {
                    idxList.add(idx)
                    continue
                }
            }

            val extend = e.callMethod("getExtend") ?: continue
            if (rmBlocked
                && extend.callMethodOrNull("hasOnlyFansProperty") == true
                && extend.callMethod("getOnlyFansProperty")?.callMethod("getHasPrivilege") == false
            ) {
                idxList.add(idx)
                continue
            }

            if (rmAdLink || purifyContents.isNotEmpty()) {
                val moduleList = e.callMethodAs<List<Any>>("getModulesList")
                if (rmAdLink && moduleList.any {
                        it.callMethodOrNull("hasModuleAdditional") == true
                                && it.callMethod("getModuleAdditional")
                            ?.callMethod("getTypeValue").let {
                                it == 2 || it == 6
                            } // additional_type_goods, additional_type_up_rcmd
                    }) {
                    idxList.add(idx)
                    continue
                }
                val modulesText = moduleList.joinToString(separator = "") {
                    it.callMethod("getModuleDesc")
                        ?.callMethodAs<String>("getText").orEmpty()
                }
                if (modulesText.isNotEmpty() && if (contentRegexMode)
                        purifyContentRegexes.any { modulesText.contains(it) }
                    else purifyContents.any { modulesText.contains(it) }
                ) {
                    idxList.add(idx)
                    continue
                }
            }

            if (purifyContents.isNotEmpty()) {
                val contentOrig = extend
                    .callMethodAs<List<Any>>("getOrigDescList")
                    .joinToString(separator = "") {
                        it.callMethodAs<String>("getOrigText")
                    }
                if (contentOrig.isNotEmpty() && if (contentRegexMode)
                        purifyContentRegexes.any { contentOrig.contains(it) }
                    else purifyContents.any { contentOrig.contains(it) }
                ) {
                    idxList.add(idx)
                    continue
                }
            }

            if (purifyContents.isNotEmpty()) {
                val content = extend
                    .callMethodAs<List<Any>>("getDescList")
                    .joinToString(separator = "") {
                        it.callMethodAs<String>("getOrigText")
                    }
                if (content.isNotEmpty() && if (contentRegexMode)
                        purifyContentRegexes.any { content.contains(it) }
                    else purifyContents.any { content.contains(it) }
                ) {
                    idxList.add(idx)
                    continue
                }
            }

            if (purifyUpNames.isNotEmpty()) {
                val origName = extend.callMethodAs<String>("getOrigName")
                if (origName.isNotEmpty() && purifyUpNames.any { origName == it }) {
                    idxList.add(idx)
                    continue
                }
            }

            if (purifyUidList.isNotEmpty()) {
                val uid = extend.callMethodAs<Long>("getUid")
                if (uid > 0L && purifyUidList.any { uid == it }) {
                    idxList.add(idx)
                    continue
                }
            }
        }
        idxList.reversed().forEach {
            contentList.removeAt(it)
        }
    }
}
