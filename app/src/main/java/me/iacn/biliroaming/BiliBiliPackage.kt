@file:Suppress("DEPRECATION")

package me.iacn.biliroaming

import android.app.AndroidAppHelper
import android.content.Context
import android.util.SparseArray
import android.view.View
import dalvik.system.DexFile
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError
import de.robv.android.xposed.XposedHelpers.findClass
import me.iacn.biliroaming.utils.Log
import java.io.*
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KProperty

data class OkHttpResult(val fieldName: String?, val methodName: String?)

/**
 * Created by iAcn on 2019/4/5
 * Email i@iacn.me
 */
class BiliBiliPackage constructor(private val mClassLoader: ClassLoader, mContext: Context) {
    private val mHookInfo: MutableMap<String, String?> = readHookInfo(mContext)
    val bangumiApiResponseClass by Weak { findClass("com.bilibili.bangumi.data.common.api.BangumiApiResponse", mClassLoader) }
    val fastJsonClass by Weak { findClass(mHookInfo["class_fastjson"], mClassLoader) }
    val bangumiUniformSeasonClass by Weak { findClass("com.bilibili.bangumi.data.page.detail.entity.BangumiUniformSeason", mClassLoader) }
    val sectionClass by Weak { findClass(mHookInfo["class_section"], mClassLoader) }
    val retrofitResponseClass by Weak { findClass(mHookInfo["class_retrofit_response"], mClassLoader) }
    val themeHelperClass by Weak { findClass(mHookInfo["class_theme_helper"], mClassLoader) }
    val settingRouteClass by Weak { findClass(mHookInfo["class_setting_route"], mClassLoader) }
    val themeListClickClass by Weak { findClass(mHookInfo["class_theme_list_click"], mClassLoader) }
    val routeParamsClass by Weak { findClass(mHookInfo["class_route_params"], mClassLoader) }
    val themeNameClass by Weak { findClass(mHookInfo["class_theme_name"], mClassLoader) }
    val themeProcessorClass by Weak { findClass(mHookInfo["class_theme_processor"], mClassLoader) }

    init {
        try {
            if (checkHookInfo()) {
                writeHookInfo(mContext)
            }
        } catch (e: Throwable) {
            Log.e(e)
        }
        instance = this
    }

//    private fun getAccessKey(): String {
//        val helperClass = findClass("com.bilibili.bangumi.ui.page.detail.pay.BangumiPayHelperV2\$accessKey\$2", mClassLoader)
//        val instance = getStaticObjectField(helperClass, "INSTANCE")
//        return callMethod(instance, "invoke") as String
//    }

    fun fastJsonParse(): String? {
        return mHookInfo["method_fastjson_parse"]
    }

    fun colorArray(): String? {
        return mHookInfo["field_color_array"]
    }

    fun videoDetailName(): String? {
        return mHookInfo["field_video_detail"]
    }

    fun signQueryName(): String? {
        return mHookInfo["method_sign_query"]
    }

    fun skinList(): String? {
        return mHookInfo["method_skin_list"]
    }

    fun saveSkinList(): String? {
        return mHookInfo["method_save_skin"]
    }

    fun themeReset(): String? {
        return mHookInfo["methods_theme_reset"]
    }

    fun addSetting(): String? {
        return mHookInfo["method_add_setting"]
    }

    fun getSettingRoute(): String? {
        return mHookInfo["method_get_setting_route"]
    }

    fun requestField(): String? {
        return mHookInfo["field_request"]
    }

    fun urlMethod(): String? {
        return mHookInfo["method_url"]
    }

    fun likeMethod(): String? {
        return mHookInfo["method_like"]
    }

    private fun readHookInfo(context: Context): MutableMap<String, String?> {
        try {
            val hookInfoFile = File(context.cacheDir, Constant.HOOK_INFO_FILE_NAME)
            Log.d("Reading hook info: $hookInfoFile")
            val startTime = System.currentTimeMillis()
            if (hookInfoFile.isFile && hookInfoFile.canRead()) {
                val lastUpdateTime = context.packageManager.getPackageInfo(AndroidAppHelper.currentPackageName(), 0).lastUpdateTime
                val stream = ObjectInputStream(FileInputStream(hookInfoFile))
                @Suppress("UNCHECKED_CAST")
                if (stream.readLong() == lastUpdateTime) return stream.readObject() as MutableMap<String, String?>
            }
            val endTime = System.currentTimeMillis()
            Log.d("Read hook info completed: take ${endTime - startTime} ms")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return HashMap()
    }

    /**
     * @return Whether to update the serialization file.
     */
    private fun checkHookInfo(): Boolean {
        var needUpdate = false
        if (!mHookInfo.containsKey("class_retrofit_response")) {
            mHookInfo["class_retrofit_response"] = findRetrofitResponseClass()
            needUpdate = true
        }
        if (!mHookInfo.containsKey("field_request") || !mHookInfo.containsKey("method_url")) {
            val (fieldName, methodName) = findUrlField(mHookInfo["class_retrofit_response"]!!)
            mHookInfo["field_request"] = fieldName
            mHookInfo["method_url"] = methodName
            needUpdate = true
        }
        if (!mHookInfo.containsKey("class_fastjson")) {
            val fastJsonClass = findFastJsonClass()
            val notObfuscated = "JSON" == fastJsonClass.simpleName
            mHookInfo["class_fastjson"] = fastJsonClass.name
            mHookInfo["method_fastjson_parse"] = if (notObfuscated) "parseObject" else "a"
            needUpdate = true
        }
        if (!mHookInfo.containsKey("class_theme_helper")) {
            mHookInfo["class_theme_helper"] = findThemeHelper()
            needUpdate = true
        }
        if (!mHookInfo.containsKey("field_color_array")) {
            mHookInfo["field_color_array"] = findColorArrayField()
            needUpdate = true
        }
        if (!mHookInfo.containsKey("method_skin_list")) {
            mHookInfo["method_skin_list"] = findSkinListMethod()
            needUpdate = true
        }
        if (!mHookInfo.containsKey("method_save_skin")) {
            mHookInfo["method_save_skin"] = findSaveSkinMethod()
            needUpdate = true
        }
        if (!mHookInfo.containsKey("class_theme_processor")) {
            mHookInfo["class_theme_processor"] = findThemeProcessor()
            needUpdate = true
        }
        if (!mHookInfo.containsKey("methods_theme_reset")) {
            mHookInfo["methods_theme_reset"] = findThemeResetMethods()
            needUpdate = true
        }
        if (!mHookInfo.containsKey("class_theme_list_click")) {
            mHookInfo["class_theme_list_click"] = findThemeListClickClass()
            needUpdate = true
        }
        if (!mHookInfo.containsKey("class_route_params")) {
            mHookInfo["class_route_params"] = findRouteParamsClass()
            needUpdate = true
        }
        if (!mHookInfo.containsKey("class_theme_name")) {
            mHookInfo["class_theme_name"] = findThemeNameClass()
            needUpdate = true
        }
        if (!mHookInfo.containsKey("class_section")) {
            mHookInfo["class_section"] = findSectionClass()
            needUpdate = true
        }
        if (!mHookInfo.containsKey("field_video_detail")) {
            mHookInfo["field_video_detail"] = findVideoDetailField()
            needUpdate = true
        }
        if (!mHookInfo.containsKey("method_sign_query")) {
            mHookInfo["method_sign_query"] = findSignQueryMethod()
            needUpdate = true
        }
        if (!mHookInfo.containsKey("method_add_setting")) {
            mHookInfo["method_add_setting"] = findAddSettingMethod()
            needUpdate = true
        }
        if (!mHookInfo.containsKey("class_setting_route")) {
            mHookInfo["class_setting_route"] = findSettingRouteClass()
            needUpdate = true
        }
        if (!mHookInfo.containsKey("method_get_setting_route")) {
            mHookInfo["method_get_setting_route"] = findGetSettingRouteMethod()
            needUpdate = true
        }
        if (!mHookInfo.containsKey("method_like")) {
            mHookInfo["method_like"] = findLikeMethod()
            needUpdate = true
        }
        Log.d(mHookInfo)
        Log.d("Check hook info completed: needUpdate = $needUpdate")
        return needUpdate
    }

    private fun writeHookInfo(context: Context) {
        try {
            val hookInfoFile = File(context.cacheDir, Constant.HOOK_INFO_FILE_NAME)
            val lastUpdateTime = context.packageManager.getPackageInfo(AndroidAppHelper.currentPackageName(), 0).lastUpdateTime
            if (hookInfoFile.exists()) hookInfoFile.delete()
            val stream = ObjectOutputStream(FileOutputStream(hookInfoFile))
            stream.writeLong(lastUpdateTime)
            stream.writeObject(mHookInfo)
            stream.flush()
            stream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Log.d("Write hook info completed")
    }

    private fun findRetrofitResponseClass(): String? {
        return bangumiApiResponseClass.methods.filter {
            "extractResult" == it.name
        }.map {
            it.parameterTypes[0]
        }.first().name
    }

    private fun findUrlField(responseClassName: String): OkHttpResult {
        val responseClass = findClass(responseClassName, mClassLoader)
        for (constructor in responseClass.declaredConstructors) {
            for (field in constructor.parameterTypes[0].declaredFields) {
                for (method in field.type.declaredMethods) {
                    if (method.returnType.name == "okhttp3.HttpUrl") {
                        return OkHttpResult(field.name, method.name)
                    }
                }
            }
        }
        return OkHttpResult(null, null)
    }

    private fun findFastJsonClass(): Class<*> {
        return try {
            findClass("com.alibaba.fastjson.JSON", mClassLoader)
        } catch (e: ClassNotFoundError) {
            findClass("com.alibaba.fastjson.a", mClassLoader)
        }
    }

    private fun findColorArrayField(): String? {
        return themeHelperClass.declaredFields.first {
            it.type == SparseArray::class.java &&
                    (it.genericType as ParameterizedType).actualTypeArguments[0].toString() == "int[]"
        }.name
    }

    private fun findSkinListMethod(): String? {
        val themeStoreClass = findClass("tv.danmaku.bili.ui.theme.ThemeStoreActivity", mClassLoader)
        val biliSkinListClass = findClass("tv.danmaku.bili.ui.theme.api.BiliSkinList", mClassLoader)
        return themeStoreClass.declaredMethods.first {
            it.parameterTypes.size == 2 && it.parameterTypes[0] == biliSkinListClass &&
                    it.parameterTypes[1] == Boolean::class.javaPrimitiveType
        }?.name
    }

    private fun findSaveSkinMethod(): String? {
        return themeHelperClass.declaredMethods.first {
            it.parameterTypes.size == 2 && it.parameterTypes[0] == Context::class.java &&
                    it.parameterTypes[1] == Int::class.javaPrimitiveType
        }?.name
    }

    private fun findThemeListClickClass(): String? {
        val themeStoreActivityClass = findClass("tv.danmaku.bili.ui.theme.ThemeStoreActivity", mClassLoader)
        for (innerClass in themeStoreActivityClass.declaredClasses) {
            for (interfaceClass in innerClass.interfaces) {
                if (interfaceClass == View.OnClickListener::class.java) {
                    return innerClass.name
                }
            }
        }
        return null
    }

    private fun findThemeNameClass(): String? {
        return try {
            val classes = DexFile(AndroidAppHelper.currentApplication().packageCodePath).entries().toList().filter {
                it.startsWith("tv.danmaku.bili.ui.garb")
            }.filter { c ->
                findClass(c, mClassLoader).declaredFields.filter {
                    Modifier.isStatic(it.modifiers)
                }.filter {
                    it.type == Map::class.java
                }.count() == 1
            }
            if (classes.size == 1) classes[0] else null
        } catch (e: Throwable) {
            null
        }
    }

    private fun findRouteParamsClass(): String? {
        val actionClass = findClass("com.bilibili.userfeedback.laserreport.UploadFeedbackUploadAction", mClassLoader)
        return actionClass.methods.first {
            it.name == "act"
        }?.parameterTypes?.get(0)?.name
    }

    private fun findVideoDetailField(): String? {
        val detailClass = findClass("tv.danmaku.bili.ui.video.api.BiliVideoDetail", mClassLoader)
        return sectionClass.declaredFields.first {
            it.type == detailClass
        }.name
    }

    private fun findSignQueryMethod(): String? {
        val libBiliClass = findClass("com.bilibili.nativelibrary.LibBili", mClassLoader)
        val signedQueryClass = findClass("com.bilibili.nativelibrary.SignedQuery", mClassLoader)
        return libBiliClass.declaredMethods.first {
            it.parameterTypes.size == 1 && it.parameterTypes[0] == MutableMap::class.java &&
                    it.returnType == signedQueryClass
        }?.name
    }

    private fun findThemeHelper(): String? {
        return try {
            val classes = DexFile(AndroidAppHelper.currentApplication().packageCodePath).entries().toList().filter {
                it.startsWith("tv.danmaku.bili.ui.theme")
            }.filter { c ->
                findClass(c, mClassLoader).declaredFields.filter {
                    Modifier.isStatic(it.modifiers)
                }.filter {
                    it.type == SparseArray::class.java
                }.count() > 1
            }
            if (classes.size == 1) classes[0] else null
        } catch (e: Throwable) {
            null
        }
    }

    private fun findThemeProcessor(): String? {
        return try {
            val biliSkinListClass = findClass("tv.danmaku.bili.ui.theme.api.BiliSkinList", mClassLoader)
            val classes = DexFile(AndroidAppHelper.currentApplication().packageCodePath).entries().toList().filter {
                it.startsWith("tv.danmaku.bili.ui.theme")
            }.filter { c ->
                findClass(c, mClassLoader).declaredFields.filter {
                    it.type == biliSkinListClass
                }.count() > 1
            }
            if (classes.size == 1) classes[0] else null
        } catch (e: Throwable) {
            null
        }
    }

    private fun findThemeResetMethods(): String {
        return themeProcessorClass.declaredMethods.filter {
            it.parameterTypes.isEmpty() && it.modifiers == 0
        }.joinToString(";") { it.name }
    }

    private fun findAddSettingMethod(): String? {
        val homeFragmentClass = findClass("tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment", mClassLoader)
        return homeFragmentClass.declaredMethods.first {
            it.parameterTypes.size == 2 && it.parameterTypes[0] == Context::class.java && it.parameterTypes[1] == List::class.java
        }?.name
    }

    private fun findSettingRouteClass(): String? {
        return try {
            val menuGroupItemClass = findClass("com.bilibili.lib.homepage.mine.MenuGroup\$Item", mClassLoader)
            val accountMineClass = findClass("tv.danmaku.bili.ui.main2.api.AccountMine", mClassLoader)
            val classes = DexFile(AndroidAppHelper.currentApplication().packageCodePath).entries().toList().filter {
                it.startsWith("tv.danmaku.bili.ui.main2")
            }.filter { c ->
                findClass(c, mClassLoader).run {
                    declaredFields.filter {
                        it.type == accountMineClass
                    }.count() > 0 && declaredMethods.filter {
                        it.parameterTypes.size == 1 && it.parameterTypes[0] == menuGroupItemClass
                    }.count() > 0
                }
            }
            if (classes.size == 1) classes[0] else null
        } catch (e: Throwable) {
            null
        }
    }

    private fun findSectionClass(): String? {
        return try {
            val progressBarClass = findClass("tv.danmaku.biliplayer.view.RingProgressBar", mClassLoader)
            val classes = DexFile(AndroidAppHelper.currentApplication().packageCodePath).entries().toList().filter {
                it.startsWith("tv.danmaku.bili.ui.video.section")
            }.filter { c ->
                findClass(c, mClassLoader).declaredFields.filter {
                    it.type == progressBarClass
                }.count() > 0
            }
            if (classes.size == 1) classes[0] else null
        } catch (e: Throwable) {
            null
        }
    }

    private fun findGetSettingRouteMethod(): String? {
        val menuGroupItemClass = findClass("com.bilibili.lib.homepage.mine.MenuGroup\$Item", mClassLoader)
        return settingRouteClass.declaredMethods.first {
            it.parameterTypes.size == 1 && it.parameterTypes[0] == menuGroupItemClass
        }?.name
    }

    private fun findLikeMethod(): String? {
        return sectionClass.declaredMethods.first {
            it.parameterTypes.size == 1 && it.parameterTypes[0] == Object::class.java
        }.name
    }

    companion object {
        @Volatile
        lateinit var instance: BiliBiliPackage
    }

    class Weak(val initializer: () -> Class<*>?) {
        private var weakReference: WeakReference<Class<*>?>? = null

        operator fun getValue(thisRef: Any?, property: KProperty<*>): Class<*> {
            weakReference?.get() ?: let {
                weakReference = WeakReference(initializer())
            }
            return weakReference!!.get()!!
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Class<*>) {
            weakReference = WeakReference(value)
        }
    }
}