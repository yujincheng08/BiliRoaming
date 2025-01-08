package me.iacn.biliroaming.hook

import android.graphics.Color
import android.util.SparseArray
import android.view.View
import androidx.annotation.ColorInt
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.ColorChooseDialog
import me.iacn.biliroaming.Constant.CURRENT_COLOR_KEY
import me.iacn.biliroaming.Constant.CUSTOM_COLOR_KEY
import me.iacn.biliroaming.Constant.DEFAULT_CUSTOM_COLOR
import me.iacn.biliroaming.utils.*

/**
 * Created by iAcn on 2019/7/14
 * Email i@iacn.me
 */
class CustomThemeHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("custom_theme", false)) return
        Log.d("startHook: CustomTheme")

        instance.themeNameClass?.getStaticObjectFieldAs<MutableMap<String, Int>>(instance.themeName())
            ?.run {
                put("custom1", CUSTOM_THEME_ID1)
                put("custom2", CUSTOM_THEME_ID2)
            }

        val colorArray =
            instance.themeHelperClass?.getStaticObjectFieldAs<SparseArray<IntArray>>(instance.colorArray())
        val primaryColor = customColor
        colorArray?.put(CUSTOM_THEME_ID1, generateColorArray(primaryColor))
        colorArray?.put(CUSTOM_THEME_ID2, generateColorArray(primaryColor))

        val allThemes =
            instance.builtInThemesClass?.getStaticObjectFieldAs<MutableMap<Long, Any>>(instance.allThemesField())

        instance.skinList()?.let {
            "tv.danmaku.bili.ui.theme.ThemeStoreActivity".hookBeforeMethod(
                mClassLoader, it,
                "tv.danmaku.bili.ui.theme.api.BiliSkinList", Boolean::class.javaPrimitiveType
            ) { param ->
                val biliSkinList = param.args[0]

                val mList = biliSkinList.getObjectFieldAs<MutableList<Any>>("mList")
                val biliSkin =
                    "tv.danmaku.bili.ui.theme.api.BiliSkin".findClassOrNull(mClassLoader)?.new()
                        ?: return@hookBeforeMethod
                biliSkin.setIntField(
                    "mId",
                    if (currentKey == CUSTOM_THEME_ID2) CUSTOM_THEME_ID2 else CUSTOM_THEME_ID1
                )
                    .setObjectField("mName", "自选颜色")
                    .setBooleanField("mIsFree", true)
                // Under the night mode item
                mList.add(3, biliSkin)
                Log.d("Add a theme item: size = " + mList.size)
            }
        }
        instance.themeListClickClass?.hookBeforeMethod("onClick", View::class.java) { param ->
            val view = param.args[0] as View
            val idName = view.resources.getResourceEntryName(view.id)
            if ("list_item" != idName) return@hookBeforeMethod
            val biliSkin = view.tag ?: return@hookBeforeMethod
            val mId = biliSkin.getIntField("mId")
            // Make colors updated immediately
            if (mId == CUSTOM_THEME_ID1 || mId == CUSTOM_THEME_ID2) {
                view.context.addModuleAssets()
                val colorDialog = ColorChooseDialog(view.context, customColor)
                colorDialog.setPositiveButton("确定") { _, _ ->
                    val color = colorDialog.color
                    val colors = generateColorArray(color)
                    colorArray?.put(CUSTOM_THEME_ID1, colors)
                    colorArray?.put(CUSTOM_THEME_ID2, colors)
                    color.toTheme()?.let {
                        allThemes?.put(CUSTOM_THEME_ID1.toLong(), it)
                        allThemes?.put(CUSTOM_THEME_ID2.toLong(), it)
                    }
                    val newId = if (mId == CUSTOM_THEME_ID1) CUSTOM_THEME_ID2 else CUSTOM_THEME_ID1
                    biliSkin.setIntField("mId", newId)
                    customColor = color
                    try {
                        param.invokeOriginalMethod()
                    } catch (e: Exception) {
                        Log.w(e)
                    }
                }
                colorDialog.show()

                // Stop executing the original method
                param.result = null
            }
        }

        // No reset when not logged in
        val replacer: Replacer = { param ->
            if (Thread.currentThread().stackTrace.any { s ->
                    s.className == "tv.danmaku.bili.MainActivityV2" && s.methodName == "onPostCreate"
                }) null else param.invokeOriginalMethod()
        }
        instance.themeReset().forEach {
            instance.themeProcessorClass?.replaceMethod(it, replacer = replacer)
        }
    }

    override fun lateInitHook() {
        if (!sPrefs.getBoolean("custom_theme", false)) return

        val primaryColor = customColor

        val allThemes =
            instance.builtInThemesClass?.getStaticObjectFieldAs<MutableMap<Long, Any>>(instance.allThemesField())
        primaryColor.toTheme()?.let {
            allThemes?.put(CUSTOM_THEME_ID1.toLong(), it)
            allThemes?.put(CUSTOM_THEME_ID2.toLong(), it)
        }
    }

    fun insertColorForWebProcess() {
        if (!sPrefs.getBoolean("custom_theme", false)) return

        try {
            var cacheColor = customColor
            var generatedColorArray = generateColorArray(cacheColor)
            instance.themeNameClass?.getStaticObjectFieldAs<MutableMap<String, Int>>(instance.themeName())
                ?.run {
                    put("custom1", CUSTOM_THEME_ID1)
                    put("custom2", CUSTOM_THEME_ID2)
                }
            instance.columnHelperClass?.getStaticObjectFieldAs<SparseArray<IntArray>>(instance.columnColorArray())
                ?.run {
                    put(CUSTOM_THEME_ID1, generatedColorArray)
                    put(CUSTOM_THEME_ID2, generatedColorArray)
                }
            instance.themeHelperClass?.getStaticObjectFieldAs<SparseArray<IntArray>>(instance.colorArray())
                ?.run {
                    put(CUSTOM_THEME_ID1, generatedColorArray)
                    put(CUSTOM_THEME_ID2, generatedColorArray)
                }
            instance.themeIdHelperClass?.getStaticObjectFieldAs<SparseArray<Int>>(instance.colorId())
                ?.run {
                    put(CUSTOM_THEME_ID1, CUSTOM_THEME_ID1)
                    put(CUSTOM_THEME_ID2, CUSTOM_THEME_ID2)
                }

            SparseArray::class.java.hookAfterMethod(
                "get",
                Int::class.javaPrimitiveType,
                Object::class.java
            ) { param ->
                if (param.args[0] != CUSTOM_THEME_ID1 || param.args[0] != CUSTOM_THEME_ID2) return@hookAfterMethod
                if (param.result?.javaClass == generatedColorArray.javaClass && param.result == generatedColorArray) {
                    val newColor = customColor
                    if (newColor != cacheColor) {
                        generatedColorArray = generateColorArray(newColor)
                        cacheColor = newColor
                        @Suppress("UNCHECKED_CAST")
                        (param.thisObject as SparseArray<IntArray>).run {
                            put(CUSTOM_THEME_ID1, generatedColorArray)
                            put(CUSTOM_THEME_ID2, generatedColorArray)
                        }
                        param.result = generatedColorArray
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(e)
        }
    }

    companion object {
        private const val CUSTOM_THEME_ID1 = 114514 // ん？
        private const val CUSTOM_THEME_ID2 = 1919810 // ん？

        private var customColor: Int
            get() = biliPrefs.getInt(CUSTOM_COLOR_KEY, DEFAULT_CUSTOM_COLOR)
            set(value) = biliPrefs.edit().putInt(CUSTOM_COLOR_KEY, value).apply()

        private val currentKey: Int
            get() = blkvPrefs.getInt(CURRENT_COLOR_KEY, 0)

        /**
         * Color Array
         *
         *
         * index0: color primary        e.g. global main color.
         * index1: color primary dark   e.g. tint when button be pressed.
         * index2: color primary light  e.g. temporarily not used.
         * index3: color primary trans  e.g. mini-tv cover on drawer.
         */
        @JvmStatic
        private fun generateColorArray(primaryColor: Int): IntArray {
            val colors = IntArray(4)
            val hsv = FloatArray(3)
            val result = FloatArray(3)
            Color.colorToHSV(primaryColor, hsv)
            colors[0] = primaryColor

            // Decrease brightness
            hsv.copyInto(result)
            result[2] -= result[2] * 0.2f
            colors[1] = Color.HSVToColor(result)

            // Increase brightness
            hsv.copyInto(result)
            result[2] += result[2] * 0.1f
            colors[2] = Color.HSVToColor(result)

            // Increase transparency
            colors[3] = -0x4c000000 or 0xFFFFFF and colors[1]
            return colors
        }

        private fun @receiver:ColorInt Int.pack() = toLong() and 0xFFFFFFFF shl 32
        private fun @receiver:ColorInt Int.toTheme() = instance.themeColorsConstructor?.run {
            parameterTypes.let {
                val garb = it[0].let { cls ->
                    try {
                        cls.new()
                    } catch (err: NoSuchMethodError) {
                        // GarbInfo in play v3.20.0
                        cls.new(
                            0L,     // id
                            true,   // showDarkContent
                            true,   // isPure
                            "",     // homePrimaryBgPath
                            0L,     // primary
                            0L,     // secondary
                            0L,     // background
                            0L,     // textTitle
                            0L      // actionIcon
                        )
                    }
                }
                newInstance(
                    garb,                   // garb
                    it[1].enumConstants[0], // currentDayNight ThemeDayNight#Day
                    pack(),                 // primary !!important
                    pack(),                 // secondary !!important
                    Color.WHITE.pack(),     // background !!important
                    Color.WHITE.pack(),     // textTitle !!important
                    Color.WHITE.pack(),     // textSubtitle
                    Color.WHITE.pack(),     // textOther
                    Color.WHITE.pack(),     // actionIcon !!important
                    true,                   // isPure
                )
            }
        }
    }
}
