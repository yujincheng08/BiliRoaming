package me.iacn.biliroaming.hook

import android.app.Activity
import android.app.AndroidAppHelper
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.graphics.Color
import android.util.SparseArray
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.ColorChooseDialog
import me.iacn.biliroaming.Constant.CUSTOM_COLOR_KEY
import me.iacn.biliroaming.Constant.DEFAULT_CUSTOM_COLOR
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.Log
import java.util.*

/**
 * Created by iAcn on 2019/7/14
 * Email i@iacn.me
 */
class CustomThemeHook(classLoader: ClassLoader?) : BaseHook(classLoader!!) {
    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("custom_theme", false)) return
        Log.d("startHook: CustomTheme")

        val instance = instance!!
        val helperClassName = instance.themeHelper() ?: return
        val helperClass = findClass(helperClassName, mClassLoader)

        instance.themeName()?.let {
            val themeNameClass = findClass(it, mClassLoader)

            @Suppress("UNCHECKED_CAST")
            val m = getStaticObjectField(themeNameClass, "a") as MutableMap<String, Int>
            m["custom"] = CUSTOM_THEME_ID
        }

        @Suppress("UNCHECKED_CAST")
        val colorArray: SparseArray<IntArray> = getStaticObjectField(helperClass, instance.colorArray()) as SparseArray<IntArray>
        val primaryColor = customColor
        colorArray.put(CUSTOM_THEME_ID, generateColorArray(primaryColor))
        instance.skinList()?.let {
            findAndHookMethod("tv.danmaku.bili.ui.theme.ThemeStoreActivity", mClassLoader, it,
                    "tv.danmaku.bili.ui.theme.api.BiliSkinList", Boolean::class.javaPrimitiveType, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val biliSkinList = param.args[0]

                    @Suppress("UNCHECKED_CAST")
                    val mList = getObjectField(biliSkinList, "mList") as MutableList<Any>
                    val biliSkinClass = findClass("tv.danmaku.bili.ui.theme.api.BiliSkin", mClassLoader)
                    val biliSkin = biliSkinClass.newInstance()
                    setIntField(biliSkin, "mId", CUSTOM_THEME_ID)
                    setObjectField(biliSkin, "mName", "自选颜色")
                    setBooleanField(biliSkin, "mIsFree", true)
                    // Under the night mode item
                    mList.add(3, biliSkin)
                    Log.d("Add a theme item: size = " + mList.size)
                }
            })
        }
        findAndHookMethod(instance.themeListClickListener(), mClassLoader, "onClick", View::class.java, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                val view = param.args[0] as View
                val idName = view.resources.getResourceEntryName(view.id)
                if ("list_item" != idName) return
                val biliSkin = view.tag ?: return
                val mId = getIntField(biliSkin, "mId")
                // Make colors updated immediately
                if (mId == CUSTOM_THEME_ID || mId == -1) {
                    Log.d("Custom theme item has been clicked")
                    val colorDialog = ColorChooseDialog(view.context, customColor)
                    colorDialog.setPositiveButton("确定") { _: DialogInterface?, _: Int ->
                        val color = colorDialog.color
                        val colors = generateColorArray(color)
                        colorArray.put(CUSTOM_THEME_ID, colors)
                        colorArray.put(-1, colors) // Add a new color id but it won't be saved

                        // If it is currently use the custom theme, it will use temporary id
                        // To make the theme color take effect immediately
                        val newId = if (mId == CUSTOM_THEME_ID) -1 else CUSTOM_THEME_ID
                        setIntField(biliSkin, "mId", newId)
                        putCustomColor(color)
                        Log.d("Update new color: mId = $newId, " +
                                "color = 0x ${Integer.toHexString(color).toUpperCase(Locale.getDefault())}")
                        try {
                            XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    colorDialog.show()

                    // Stop executing the original method
                    param.result = null
                }
            }
        })
        instance.saveSkinList()?.let {
            findAndHookMethod(helperClass, it, Context::class.java, Int::class.javaPrimitiveType, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val currentThemeKey = param.args[1] as Int
                    if (currentThemeKey == -1) param.args[1] = CUSTOM_THEME_ID
                }
            })
        }

        // No invalidation when not logged in
        instance.invalidateSkin()?.let { its ->
            val hooker = object : XC_MethodReplacement() {
                var called = 0
                override fun replaceHookedMethod(param: MethodHookParam): Any? {
                    return if (called++ == 1) {
                        called = 0
                        XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                    } else {
                        null
                    }
                }
            }
            its.split(";").map {
                findAndHookMethod(helperClass, it, Activity::class.java, hooker)
            }
        }
        // No reset when not logged in
        instance.themeReset()?.let { its ->
            val hooker = object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? {
                    for (s in Thread.currentThread().stackTrace) {
                        if(s.className=="tv.danmaku.bili.MainActivityV2" && s.methodName == "onPostCreate")
                            return null
                    }
                    return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                }

            }
            its.split(";").map {
                findAndHookMethod(instance.themeProcessor()!!, mClassLoader, it, hooker)
            }
        }
    }

    /**
     * Color Array
     *
     *
     * index0: color primary        e.g. global main color.
     * index1: color primary dark   e.g. tint when button be pressed.
     * index2: color primary light  e.g. temporarily not used.
     * index3: color primary trans  e.g. mini-tv cover on drawer.
     */
    private fun generateColorArray(primaryColor: Int): IntArray {
        val colors = IntArray(4)
        val hsv = FloatArray(3)
        val result = FloatArray(3)
        Color.colorToHSV(primaryColor, hsv)
        colors[0] = primaryColor

        // Decrease brightness
        System.arraycopy(hsv, 0, result, 0, hsv.size)
        result[2] -= result[2] * 0.2f
        colors[1] = Color.HSVToColor(result)

        // Increase brightness
        System.arraycopy(hsv, 0, result, 0, hsv.size)
        result[2] += result[2] * 0.1f
        colors[2] = Color.HSVToColor(result)

        // Increase transparency
        colors[3] = -0x4c000000 or 0xFFFFFF and colors[1]
        return colors
    }

    private val customColor: Int
        get() = biliPrefs.getInt(CUSTOM_COLOR_KEY, DEFAULT_CUSTOM_COLOR)

    private fun putCustomColor(color: Int) {
        biliPrefs.edit().putInt(CUSTOM_COLOR_KEY, color).apply()
    }

    private val biliPrefs: SharedPreferences
        get() = AndroidAppHelper.currentApplication().getSharedPreferences("bili_preference", Context.MODE_PRIVATE)

    fun insertColorForWebProcess() {
        if (!XposedInit.sPrefs.getBoolean("custom_theme", false)) return
        val helperClass = findClass("com.bilibili.column.helper.k", mClassLoader)

        @Suppress("UNCHECKED_CAST")
        val colorArray: SparseArray<IntArray> = getStaticObjectField(helperClass, "l") as SparseArray<IntArray>
        val primaryColor = customColor
        colorArray.put(CUSTOM_THEME_ID, generateColorArray(primaryColor))
    }

    companion object {
        private const val CUSTOM_THEME_ID = 114514 // ん？
    }
}