package me.iacn.biliroaming.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.*
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.Log
import java.lang.reflect.Type

class JsonHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        Log.d("startHook: Json")

        val jsonClass = findClass("com.alibaba.fastjson.JSON", mClassLoader)
        val tabResponseClass = findClass("tv.danmaku.bili.ui.main2.resource.MainResourceManager\$TabResponse", mClassLoader)
        val emotionClass = findClass("com.bilibili.app.comm.emoticon.model.EmoticonPackagesDetailData", mClassLoader)
        val generalResponseClass = findClass("com.bilibili.okretro.GeneralResponse", mClassLoader)
        val accountMineClass = findClass("tv.danmaku.bili.ui.main2.api.AccountMine", mClassLoader)

        findAndHookMethod(jsonClass, "parseObject", String::class.java, Type::class.java, Int::class.javaPrimitiveType, "com.alibaba.fastjson.parser.Feature[]", object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                var result = param.result
                result?.let {} ?: run {
                    return;
                }
                if (result.javaClass == generalResponseClass) {
                    result = getObjectField(result, "data")
                    result?.let {} ?: return
                }

                if (param.result.javaClass == tabResponseClass) {
                    val data = getObjectField(result, "tabData")
                    if (XposedInit.sPrefs.getBoolean("purify_mall", false)) {
                        val bottom = getObjectField(data, "bottom") as MutableList<*>
                        bottom.removeAll {
                            val uri = getObjectField(it, "uri") as String
                            uri.startsWith("bilibili://mall/home")
                        }
                    }

//                    val tab = getObjectField(data, "tab") as MutableList<Any>
//                    val hasLive = tab.fold(false) { acc, it ->
//                        val uri = getObjectField(it, "uri") as String;
//                        acc && uri.startsWith("bilibili://live/home")
//                    };
//                    val tabClass = findClass("tv.danmaku.bili.ui.main2.resource.MainResourceManager\$TabData", mClassLoader)
//                    if (!hasLive) {
//                        val live = newInstance(tabClass);
//                        setObjectField(live, "tabId", "20");
//                        setObjectField(live, "name", "直播");
//                        setObjectField(live, "uri", "bilibili://live/home");
//                        setObjectField(live, "reportId", "直播tab");
//                        setIntField(live, "pos", 1);
//                        tab.forEach{
//                            setIntField(it, "pos", getIntField(it, "pos") + 1);
//                        }
//                        tab.add(0, live);
//                    }
                    if (XposedInit.sPrefs.getBoolean("purify_game", false)) {
                        val top = getObjectField(data, "top") as MutableList<*>
                        top.removeAll {
                            val uri = getObjectField(it, "uri") as String
                            uri.startsWith("bilibili://game_center/home")
                        }
                    }
                } else if (result.javaClass == emotionClass) {
                    // Work around of bili blue
                    val packages = getObjectField(result, "packages") as MutableList<*>
                    packages.removeAll {
                        getObjectField(it, "emotes") == null
                    }
                } else if (result.javaClass == accountMineClass) {
                    if (XposedInit.sPrefs.getBoolean("purify_drawer", false)) {
                        val sections = getObjectField(result, "sectionList") as MutableList<*>?
                        sections?.removeAt(sections.size - 1)
                    }
                }
            }
        })

    }
}