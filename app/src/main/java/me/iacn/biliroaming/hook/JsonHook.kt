package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.*
import java.lang.reflect.Type

class JsonHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        Log.d("startHook: Json")

        val tabResponseClass = "tv.danmaku.bili.ui.main2.resource.MainResourceManager\$TabResponse".findClass(mClassLoader)
        val accountMineClass = "tv.danmaku.bili.ui.main2.api.AccountMine".findClass(mClassLoader)
        val splashClass = "tv.danmaku.bili.ui.splash.SplashData".findClass(mClassLoader)
        val tabClass = "tv.danmaku.bili.ui.main2.resource.MainResourceManager\$Tab".findClass(mClassLoader)
        val defaultWordClass = "tv.danmaku.bili.ui.main2.api.SearchDefaultWord".findClass(mClassLoader)
        val defaultKeywordClass = "com.bilibili.search.api.DefaultKeyword".findClass(mClassLoader)

        instance.fastJsonClass?.hookAfterMethod(instance.fastJsonParse(), String::class.java, Type::class.java, Int::class.javaPrimitiveType, "com.alibaba.fastjson.parser.Feature[]") { param ->
            var result = param.result ?: return@hookAfterMethod
            if (result.javaClass == instance.generalResponseClass) {
                result = result.getObjectField("data") ?: return@hookAfterMethod
            }

            when (result.javaClass) {
                tabResponseClass -> {
                    val data = result.getObjectField("tabData")
                    if (XposedInit.sPrefs.getBoolean("purify_mall", false) &&
                            XposedInit.sPrefs.getBoolean("hidden", false)) {
                        data?.getObjectFieldAs<MutableList<*>?>("bottom")?.removeAll {
                            it?.getObjectFieldAs<String?>("uri")?.startsWith("bilibili://mall/home")
                                    ?: false
                        }
                    }

                    if (XposedInit.sPrefs.getBoolean("add_live", false)) {
                        val tab = data?.getObjectFieldAs<MutableList<Any>>("tab")
                        val hasLive = tab?.fold(false) { acc, it ->
                            val uri = it.getObjectFieldAs<String>("uri")
                            acc || uri.startsWith("bilibili://live/home")
                        }
                        if (hasLive != null && !hasLive) {
                            val live = tabClass?.new()
                                    ?.setObjectField("tabId", "20")
                                    ?.setObjectField("name", "直播")
                                    ?.setObjectField("uri", "bilibili://live/home")
                                    ?.setObjectField("reportId", "直播tab")
                                    ?.setIntField("pos", 1)
                            live?.let { l ->
                                tab.forEach {
                                    it.setIntField("pos", it.getIntField("pos") + 1)
                                }
                                tab.add(0, l)
                            }
                        }
                    }
                    if (XposedInit.sPrefs.getBoolean("purify_game", false) &&
                            XposedInit.sPrefs.getBoolean("hidden", false)) {
                        val top = data?.getObjectFieldAs<MutableList<*>?>("top")
                        top?.removeAll {
                            val uri = it?.getObjectFieldAs<String?>("uri")
                            uri?.startsWith("bilibili://game_center/home") ?: false
                        }
                    }
                }
                accountMineClass -> {
                    if (XposedInit.sPrefs.getBoolean("purify_drawer", false) &&
                            XposedInit.sPrefs.getBoolean("hidden", false)) {
                        result.javaClass.findFieldOrNull("sectionList")?.run {
                            val sections = get(result) as MutableList<*>?
                            sections?.removeAt(sections.size - 1)
                        }
                        result.javaClass.findFieldOrNull("sectionListV2")?.run {
                            val sections = get(result) as MutableList<*>?
                            sections?.removeAt(sections.size - 2)
                        }
                    }
                }
                splashClass -> {
                    if (XposedInit.sPrefs.getBoolean("purify_splash", false) &&
                            XposedInit.sPrefs.getBoolean("hidden", false)) {
                        result.getObjectFieldAs<MutableList<*>?>("splashList")?.clear()
                        result.getObjectFieldAs<MutableList<*>?>("strategyList")?.clear()
                    }
                }
                defaultWordClass, defaultKeywordClass -> {
                    if (XposedInit.sPrefs.getBoolean("purify_search", false) && XposedInit.sPrefs.getBoolean("hidden", false)) {
                        result.javaClass.fields.forEach {
                            if (it.type != Int::class.javaPrimitiveType)
                                result.setObjectField(it.name, null)
                        }
                    }
                }
            }
        }

        val searchRankClass = "com.bilibili.search.api.SearchRank".findClass(mClassLoader)
        val searchGuessClass = "com.bilibili.search.api.SearchReferral\$Guess".findClass(mClassLoader)

        instance.fastJsonClass?.hookAfterMethod("parseArray", String::class.java, Class::class.java) { param ->
            val result = param.result as MutableList<*>?
            when (param.args[1] as Class<*>) {
                searchRankClass, searchGuessClass -> {
                    if (XposedInit.sPrefs.getBoolean("purify_search", false) && XposedInit.sPrefs.getBoolean("hidden", false)) {
                        result?.clear()
                    }
                }
            }
        }
    }
}