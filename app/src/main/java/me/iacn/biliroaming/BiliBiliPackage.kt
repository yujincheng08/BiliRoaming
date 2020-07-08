@file:Suppress("DEPRECATION")

package me.iacn.biliroaming

import android.app.Activity
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

data class OkHttpResult(val fieldName: String?, val methodName: String?)

/**
 * Created by iAcn on 2019/4/5
 * Email i@iacn.me
 */
class BiliBiliPackage constructor(private val mClassLoader: ClassLoader, mContext: Context) {
    private val mHookInfo: MutableMap<String, String?> = readHookInfo(mContext)
    private var bangumiApiResponseClass: WeakReference<Class<*>?>? = null
    private var fastJsonClass: WeakReference<Class<*>?>? = null
    private var bangumiUniformSeasonClass: WeakReference<Class<*>?>? = null
    private var sectionClass: WeakReference<Class<*>?>? = null

    init {
        try {
            if (checkHookInfo()) {
                writeHookInfo(mContext)
            }
        } catch (e: Throwable) {
            Log.d(e)
        }
        instance = this
    }

//    private fun getAccessKey(): String {
//        val helperClass = findClass("com.bilibili.bangumi.ui.page.detail.pay.BangumiPayHelperV2\$accessKey\$2", mClassLoader)
//        val instance = getStaticObjectField(helperClass, "INSTANCE")
//        return callMethod(instance, "invoke") as String
//    }

    fun retrofitResponse(): String? {
        return mHookInfo["class_retrofit_response"]
    }

    fun fastJsonParse(): String? {
        return mHookInfo["method_fastjson_parse"]
    }

    fun colorArray(): String? {
        return mHookInfo["field_color_array"]
    }

    fun themeListClickListener(): String? {
        return mHookInfo["class_theme_list_click"]
    }

    fun routeParams(): String? {
        return mHookInfo["class_route_params"]
    }

    fun themeName(): String? {
        return mHookInfo["class_theme_name"]
    }

    fun videoDetailName(): String? {
        return mHookInfo["field_video_detail"]
    }

    fun signQueryName(): String? {
        return mHookInfo["method_sign_query"]
    }

    fun themeHelper(): String? {
        return mHookInfo["class_theme_helper"]
    }

    fun skinList(): String? {
        return mHookInfo["method_skin_list"]
    }

    fun saveSkinList(): String? {
        return mHookInfo["method_save_skin"]
    }

    fun invalidateSkin(): String? {
        return mHookInfo["methods_invalidate_skin"]
    }

    fun themeProcessor(): String? {
        return mHookInfo["class_theme_processor"]
    }

    fun themeReset(): String? {
        return mHookInfo["methods_theme_reset"]
    }

    fun bangumiApiResponse(): Class<*>? {
        bangumiApiResponseClass = checkNullOrReturn(bangumiApiResponseClass,
                "com.bilibili.bangumi.data.common.api.BangumiApiResponse")
        return bangumiApiResponseClass!!.get()
    }

    fun bangumiUniformSeason(): Class<*>? {
        bangumiUniformSeasonClass = checkNullOrReturn(bangumiUniformSeasonClass,
                "com.bilibili.bangumi.data.page.detail.entity.BangumiUniformSeason")
        return bangumiUniformSeasonClass!!.get()
    }

    fun fastJson(): Class<*>? {
        fastJsonClass = checkNullOrReturn(fastJsonClass, mHookInfo["class_fastjson"])
        return fastJsonClass!!.get()
    }

    fun section(): Class<*>? {
        sectionClass = checkNullOrReturn(sectionClass, "tv.danmaku.bili.ui.video.section.b")
        return sectionClass!!.get()
    }

    fun requestField(): String? {
        return mHookInfo["field_request"]
    }

    fun urlMethod(): String? {
        return mHookInfo["method_url"]
    }

    private fun checkNullOrReturn(clazz: WeakReference<Class<*>?>?, className: String?): WeakReference<Class<*>?> {
        @Suppress("NAME_SHADOWING")
        var clazz = clazz
        if (clazz?.get() == null) {
            clazz = WeakReference(findClass(className, mClassLoader))
        }
        return clazz
    }

    private fun readHookInfo(context: Context): MutableMap<String, String?> {
        try {
            val hookInfoFile = File(context.cacheDir, Constant.HOOK_INFO_FILE_NAME)
            Log.d("Reading hook info: $hookInfoFile")
            val startTime = System.currentTimeMillis()
            if (hookInfoFile.isFile && hookInfoFile.canRead()) {
                val lastUpdateTime = context.packageManager.getPackageInfo(Constant.BILIBILI_PACKAGENAME, 0).lastUpdateTime
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
        if (!mHookInfo.containsKey("methods_invalidate_skin")) {
            mHookInfo["methods_invalidate_skin"] = findInvalidateSkinMethods()
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
        if (!mHookInfo.containsKey("field_video_detail")) {
            mHookInfo["field_video_detail"] = findVideoDetailField()
            needUpdate = true
        }
        if (!mHookInfo.containsKey("method_sign_query")) {
            mHookInfo["method_sign_query"] = findSignQueryMethod()
            needUpdate = true
        }
        Log.d(mHookInfo)
        Log.d("Check hook info completed: needUpdate = $needUpdate")
        return needUpdate
    }

    private fun writeHookInfo(context: Context) {
        try {
            val hookInfoFile = File(context.cacheDir, Constant.HOOK_INFO_FILE_NAME)
            val lastUpdateTime = context.packageManager.getPackageInfo(Constant.BILIBILI_PACKAGENAME, 0).lastUpdateTime
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
        val methods = bangumiApiResponse()!!.methods
        for (method in methods) {
            if ("extractResult" == method.name) {
                val responseClass = method.parameterTypes[0]
                return responseClass.name
            }
        }
        return null
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
        val themeHelperName = themeHelper() ?: return null
        val fields = findClass(themeHelperName, mClassLoader).declaredFields
        for (field in fields) {
            if (field.type == SparseArray::class.java) {
                val genericType = field.genericType as ParameterizedType
                val types = genericType.actualTypeArguments
                if ("int[]" == types[0].toString()) {
                    return field.name
                }
            }
        }
        return null
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
        val themeHelperName = themeHelper() ?: return null
        return findClass(themeHelperName, mClassLoader).declaredMethods.first {
            it.parameterTypes.size == 2 && it.parameterTypes[0] == Context::class.java &&
                    it.parameterTypes[1] == Int::class.javaPrimitiveType
        }?.name
    }

    private fun findInvalidateSkinMethods(): String {
        val themeHelperName = themeHelper() ?: return ""
        return findClass(themeHelperName, mClassLoader).declaredMethods.filter {
            it.parameterTypes.size == 1 && it.parameterTypes[0] == Activity::class.java
        }.joinToString(";") { it.name }
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
        return section()?.declaredFields?.first {
            it.type == detailClass
        }?.name
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
        val themeProcessorName = themeProcessor() ?: return ""
        return findClass(themeProcessorName, mClassLoader).declaredMethods.filter {
            it.parameterTypes.isEmpty() && it.modifiers == 0
        }.joinToString(";") { it.name }
    }

    companion object {
        @Volatile
        var instance: BiliBiliPackage? = null
    }
}