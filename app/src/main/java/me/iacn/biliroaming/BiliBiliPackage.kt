package me.iacn.biliroaming

import android.content.Context
import android.util.Log
import android.util.SparseArray
import android.view.View
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError
import me.iacn.biliroaming.Constant.TAG
import java.io.*
import java.lang.ref.WeakReference
import java.lang.reflect.ParameterizedType
import java.util.*

/**
 * Created by iAcn on 2019/4/5
 * Email i@iacn.me
 */
class BiliBiliPackage private constructor() {
    private var mClassLoader: ClassLoader? = null
    private var mHookInfo: MutableMap<String, String?>? = null
    private var bangumiApiResponseClass: WeakReference<Class<*>?>? = null
    private var fastJsonClass: WeakReference<Class<*>?>? = null
    private var bangumiUniformSeasonClass: WeakReference<Class<*>?>? = null
    private var themeHelperClass: WeakReference<Class<*>?>? = null
    fun init(classLoader: ClassLoader?, context: Context) {
        mClassLoader = classLoader
        readHookInfo(context)
        if (checkHookInfo()) {
            writeHookInfo(context)
        }
    }

    fun retrofitResponse(): String? {
        return mHookInfo!!["class_retrofit_response"]
    }

    fun fastJsonParse(): String? {
        return mHookInfo!!["method_fastjson_parse"]
    }

    fun colorArray(): String? {
        return mHookInfo!!["field_color_array"]
    }

    fun themeListClickListener(): String? {
        return mHookInfo!!["class_theme_list_click"]
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
        fastJsonClass = checkNullOrReturn(fastJsonClass, mHookInfo!!["class_fastjson"])
        return fastJsonClass!!.get()
    }

    fun themeHelper(): Class<*>? {
        themeHelperClass = checkNullOrReturn(themeHelperClass, "tv.danmaku.bili.ui.theme.a")
        return themeHelperClass!!.get()
    }

    private fun checkNullOrReturn(clazz: WeakReference<Class<*>?>?, className: String?): WeakReference<Class<*>?> {
        var clazz = clazz
        if (clazz?.get() == null) {
            clazz = WeakReference(XposedHelpers.findClass(className, mClassLoader))
        }
        return clazz
    }

    private fun readHookInfo(context: Context) {
        try {
            val hookInfoFile = File(context.cacheDir, Constant.HOOK_INFO_FILE_NAME)
            Log.d(TAG, "Reading hook info: $hookInfoFile")
            val startTime = System.currentTimeMillis()
            if (hookInfoFile.isFile && hookInfoFile.canRead()) {
                val lastUpdateTime = context.packageManager.getPackageInfo(Constant.BILIBILI_PACKAGENAME, 0).lastUpdateTime
                val stream = ObjectInputStream(FileInputStream(hookInfoFile))
                if (stream.readLong() == lastUpdateTime) mHookInfo = stream.readObject() as MutableMap<String, String?>
            }
            val endTime = System.currentTimeMillis()
            Log.d(TAG, "Read hook info completed: take " + (endTime - startTime) + " ms")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * @return Whether to update the serialization file.
     */
    private fun checkHookInfo(): Boolean {
        var needUpdate = false
        if (mHookInfo == null) {
            mHookInfo = HashMap()
            needUpdate = true
        }
        if (!mHookInfo!!.containsKey("class_retrofit_response")) {
            mHookInfo!!["class_retrofit_response"] = findRetrofitResponseClass()
            needUpdate = true
        }
        if (!mHookInfo!!.containsKey("class_fastjson")) {
            val fastJsonClass = findFastJsonClass()
            val notObfuscated = "JSON" == fastJsonClass.simpleName
            mHookInfo!!["class_fastjson"] = fastJsonClass.name
            mHookInfo!!["method_fastjson_parse"] = if (notObfuscated) "parseObject" else "a"
            needUpdate = true
        }
        if (!mHookInfo!!.containsKey("field_color_array")) {
            mHookInfo!!["field_color_array"] = findColorArrayField()
            needUpdate = true
        }
        if (!mHookInfo!!.containsKey("class_theme_list_click")) {
            mHookInfo!!["class_theme_list_click"] = findThemeListClickClass()
            needUpdate = true
        }
        Log.d(TAG, "Check hook info completed: needUpdate = $needUpdate")
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
        Log.d(TAG, "Write hook info completed")
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

    private fun findFastJsonClass(): Class<*> {
        val clazz: Class<*>
        clazz = try {
            XposedHelpers.findClass("com.alibaba.fastjson.JSON", mClassLoader)
        } catch (e: ClassNotFoundError) {
            XposedHelpers.findClass("com.alibaba.fastjson.a", mClassLoader)
        }
        return clazz
    }

    private fun findColorArrayField(): String? {
        val fields = themeHelper()!!.declaredFields
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

    private fun findThemeListClickClass(): String? {
        val themeStoreActivityClass = XposedHelpers.findClass("tv.danmaku.bili.ui.theme.ThemeStoreActivity", mClassLoader)
        for (innerClass in themeStoreActivityClass.declaredClasses) {
            for (interfaceClass in innerClass.interfaces) {
                if (interfaceClass == View.OnClickListener::class.java) {
                    return innerClass.name
                }
            }
        }
        return null
    }

    companion object {
        @Volatile
        private var sInstance: BiliBiliPackage? = null

        @JvmStatic
        val instance: BiliBiliPackage?
            get() {
                sInstance?.let{} ?: run{
                synchronized(BiliBiliPackage::class.java) {
                    if (sInstance == null) {
                        sInstance = BiliBiliPackage()
                    }
                }
            }
        return sInstance
    }
}
}