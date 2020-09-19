@file:Suppress("DEPRECATION")

package me.iacn.biliroaming

import android.app.AndroidAppHelper
import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.SparseArray
import android.view.View
import android.widget.TextView
import dalvik.system.DexFile
import de.robv.android.xposed.XposedHelpers.findClassIfExists
import me.iacn.biliroaming.utils.*
import java.io.*
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.net.ProxySelector
import kotlin.reflect.KProperty

data class OkHttpResult(val fieldName: String?, val methodName: String?)

/**
 * Created by iAcn on 2019/4/5
 * Email i@iacn.me
 */
class BiliBiliPackage constructor(private val mClassLoader: ClassLoader, mContext: Context) {
    private val mHookInfo: MutableMap<String, String?> = readHookInfo(mContext)
    val bangumiApiResponseClass by Weak { "com.bilibili.bangumi.data.common.api.BangumiApiResponse".findClass(mClassLoader) }
    val rxGeneralResponseClass by Weak { "com.bilibili.okretro.call.rxjava.RxGeneralResponse".findClassOrNull(mClassLoader) }
    val fastJsonClass by Weak { mHookInfo["class_fastjson"]?.findClassOrNull(mClassLoader) }
    val bangumiUniformSeasonClass by Weak { "com.bilibili.bangumi.data.page.detail.entity.BangumiUniformSeason".findClass(mClassLoader) }
    val sectionClass by Weak { mHookInfo["class_section"]?.findClassOrNull(mClassLoader) }
    val retrofitResponseClass by Weak { mHookInfo["class_retrofit_response"]?.findClassOrNull(mClassLoader) }
    val themeHelperClass by Weak { mHookInfo["class_theme_helper"]?.findClassOrNull(mClassLoader) }
    val themeIdHelperClass by Weak { mHookInfo["class_theme_id_helper"]?.findClassOrNull(mClassLoader) }
    val columnHelperClass by Weak { mHookInfo["class_column_helper"]?.findClassOrNull(mClassLoader) }
    val settingRouteClass by Weak { mHookInfo["class_setting_route"]?.findClassOrNull(mClassLoader) }
    val themeListClickClass by Weak { mHookInfo["class_theme_list_click"]?.findClassOrNull(mClassLoader) }
    val shareWrapperClass by Weak { mHookInfo["class_share_wrapper"]?.findClassOrNull(mClassLoader) }
    val themeNameClass by Weak { mHookInfo["class_theme_name"]?.findClassOrNull(mClassLoader) }
    val themeProcessorClass by Weak { mHookInfo["class_theme_processor"]?.findClassOrNull(mClassLoader) }
    val drawerClass by Weak { mHookInfo["class_drawer"]?.findClassOrNull(mClassLoader) }
    val generalResponseClass by Weak { "com.bilibili.okretro.GeneralResponse".findClass(mClassLoader) }
    val seasonParamsMapClass by Weak { "com.bilibili.bangumi.data.page.detail.BangumiDetailApiService\$UniformSeasonParamsMap".findClass(mClassLoader) }
    val brandSplashClass by Weak { "tv.danmaku.bili.ui.splash.brand.ui.BaseBrandSplashFragment".findClassOrNull(mClassLoader) }
    val urlConnectionClass by Weak { "com.bilibili.lib.okhttp.huc.OkHttpURLConnection".findClass(mClassLoader) }
    val okHttpClientClass by Weak { mHookInfo["class_http_client"]?.findClass(mClassLoader) }
    val okHttpClientBuilderClass by Weak { mHookInfo["class_http_client_builder"]?.findClass(mClassLoader) }
    val downloadThreadListenerClass by Weak { mHookInfo["class_download_thread_listener"]?.findClass(mClassLoader) }
    val downloadingActivityClass by Weak { "tv.danmaku.bili.ui.offline.DownloadingActivity".findClassOrNull(mClassLoader) }
    val reportDownloadThreadClass by Weak { mHookInfo["class_report_download_thread"]?.findClass(mClassLoader) }
    val libBiliClass by Weak { "com.bilibili.nativelibrary.LibBili".findClass(mClassLoader) }
    val splashActivityClass by Weak { "tv.danmaku.bili.ui.splash.SplashActivity".findClass(mClassLoader) }
    val mainActivityClass by Weak { "tv.danmaku.bili.MainActivityV2".findClass(mClassLoader) }
    val homeUserCenterClass by Weak { "tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment".findClass(mClassLoader) }
    val garbHelperClass by Weak { mHookInfo["class_garb_helper"]?.findClass(mClassLoader) }
    val musicNotificationManagerClass by Weak { "tv.danmaku.bili.ui.player.notification.MusicNotificationManager".findClass(mClassLoader) }
    val musicNotificationHelperClass by Weak { mHookInfo["class_music_notification_helper"]?.findClass(mClassLoader) }
    val notificationCompatBuilderClass by Weak { "androidx.core.app.NotificationCompat\$Builder".findClass(mClassLoader) }

    private val classesList by lazy { DexFile(AndroidAppHelper.currentApplication().packageCodePath).entries().toList() }
    private val accessKeyInstance by lazy { "com.bilibili.bangumi.ui.page.detail.pay.BangumiPayHelperV2\$accessKey\$2".findClass(mClassLoader)?.getStaticObjectField("INSTANCE") }

    val accessKey
        get() = accessKeyInstance?.callMethodAs<String>("invoke")

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

    fun fastJsonParse(): String? {
        return mHookInfo["method_fastjson_parse"]
    }

    fun colorArray(): String? {
        return mHookInfo["field_color_array"]
    }

    fun colorId(): String? {
        return mHookInfo["field_color_id"]
    }

    fun columnColorArray(): String? {
        return mHookInfo["field_column_color_array"]
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

    fun themeName(): String? {
        return mHookInfo["field_theme_name"]
    }

    fun shareWrapper(): String? {
        return mHookInfo["method_share_wrapper"]
    }

    fun httpClientBuild(): String? {
        return mHookInfo["method_http_client_build"]
    }

    fun proxySelector(): String? {
        return mHookInfo["field_proxy_selector"]
    }

    fun downloadingThread(): String? {
        return mHookInfo["field_download_thread"]
    }

    fun reportDownloadThread(): String? {
        return mHookInfo["method_report_download_thread"]
    }

    fun garb(): String? {
        return mHookInfo["method_garb"]
    }

    fun setNotification(): String? {
        return mHookInfo["methods_set_notification"]
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

        fun <K, V> MutableMap<K, V>.checkOrPut(key: K, defaultValue: () -> V): MutableMap<K, V> {
            if (!containsKey(key)) {
                put(key, defaultValue())
                needUpdate = true
            }
            return this
        }

        fun <K, V> MutableMap<K, V>.checkOrPut(vararg keys: K, checker: (map: MutableMap<K, V>, keys: Array<out K>) -> Boolean, defaultValue: () -> Array<V>): MutableMap<K, V> {
            if (!checker(this, keys)) {
                putAll(keys.zip(defaultValue()))
                needUpdate = true
            }
            return this
        }

        fun <K, V> MutableMap<K, V>.checkConjunctiveOrPut(vararg keys: K, defaultValue: () -> Array<V>): MutableMap<K, V> {
            return checkOrPut(keys = keys, checker = { m, ks -> ks.fold(true) { acc, k -> acc && m.containsKey(k) } }, defaultValue = defaultValue)
        }

        @Suppress("unused")
        fun <K, V> MutableMap<K, V>.checkDisjunctiveOrPut(vararg keys: K, defaultValue: () -> Array<V>): MutableMap<K, V> {
            return checkOrPut(keys = keys, checker = { m, ks -> ks.fold(false) { acc, k -> acc || m.containsKey(k) } }, defaultValue = defaultValue)
        }

        mHookInfo.checkOrPut("class_retrofit_response") {
            findRetrofitResponseClass()
        }.checkConjunctiveOrPut("field_request", "method_url") {
            val (fieldName, methodName) = findUrlField()
            arrayOf(fieldName, methodName)
        }.checkConjunctiveOrPut("class_fastjson", "method_fastjson_parse") {
            val fastJsonClass = findFastJsonClass()
            val notObfuscated = "JSON" == fastJsonClass?.simpleName
            arrayOf(fastJsonClass?.name, if (notObfuscated) "parseObject" else "a")
        }.checkOrPut("class_theme_helper") {
            findThemeHelper()
        }.checkOrPut("class_theme_id_helper") {
            findThemeIdHelper()
        }.checkOrPut("field_color_array") {
            findColorArrayField()
        }.checkOrPut("field_color_id") {
            findColorIdField()
        }.checkOrPut("class_column_helper") {
            findColumnHelper()
        }.checkOrPut("field_column_color_array") {
            findColumnColorArrayField()
        }.checkOrPut("method_skin_list") {
            findSkinListMethod()
        }.checkOrPut("method_save_skin") {
            findSaveSkinMethod()
        }.checkOrPut("class_theme_processor") {
            findThemeProcessor()
        }.checkOrPut("methods_theme_reset") {
            findThemeResetMethods()
        }.checkOrPut("class_theme_list_click") {
            findThemeListClickClass()
        }.checkOrPut("class_share_wrapper") {
            findShareWrapperClass()
        }.checkOrPut("method_share_wrapper") {
            findShareWrapperMethod()
        }.checkOrPut("class_theme_name") {
            findThemeNameClass()
        }.checkOrPut("field_theme_name") {
            findThemeNameField()
        }.checkOrPut("class_section") {
            findSectionClass()
        }.checkOrPut("field_video_detail") {
            findVideoDetailField()
        }.checkOrPut("method_sign_query") {
            findSignQueryMethod()
        }.checkOrPut("class_setting_route", "method_add_setting", checker = { m, keys ->
            keys.fold(true) { acc, s -> acc && m.containsKey(s) } ||
                    findClassIfExists("tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment", mClassLoader) == null
        }) {
            arrayOf(findSettingRouteClass(), findAddSettingMethod())
        }.checkOrPut("class_drawer", checker = { m, keys ->
            keys.fold(true) { acc, s -> acc && m.containsKey(s) } ||
                    findClassIfExists("tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment", mClassLoader) != null
        }) {
            arrayOf(findDrawerClass())
        }.checkOrPut("method_get_setting_route") {
            findGetSettingRouteMethod()
        }.checkOrPut("method_like") {
            findLikeMethod()
        }.checkOrPut("class_http_client") {
            findOkHttpClientClass()
        }.checkOrPut("class_http_client_builder") {
            findOkHttpClientBuilderClass()
        }.checkOrPut("method_http_client_build") {
            findHttpClientBuildMethod()
        }.checkOrPut("field_proxy_selector") {
            findProxySelectorField()
        }.checkOrPut("class_download_thread_listener") {
            findDownloadThreadListener()
        }.checkOrPut("field_download_thread") {
            findDownloadThreadField()
        }.checkConjunctiveOrPut("class_report_download_thread", "method_report_download_thread") {
            findReportDownloadThread()
        }.checkConjunctiveOrPut("class_garb_helper", "method_garb") {
            findGarbHelper()
        }.checkOrPut("class_music_notification_helper") {
            findMusicNotificationHelper()
        }.checkOrPut("methods_set_notification") {
            findSetNotificationMethods()
        }

        Log.d(mHookInfo)
        Log.d("Check hook info completed: needUpdate = $needUpdate")
        return needUpdate
    }

    private fun findSetNotificationMethods(): String? {
        return musicNotificationHelperClass?.declaredMethods?.lastOrNull {
            it.parameterTypes.size == 1 && it.parameterTypes[0] == notificationCompatBuilderClass
        }?.name
    }

    private fun findMusicNotificationHelper(): String? {
        return musicNotificationManagerClass?.declaredFields?.firstOrNull {
            it.type.name.startsWith("tv.danmaku.bili.ui.player.notification.") &&
                    !it.type.name.endsWith("AbsMusicService")
        }?.type?.name
    }

    private fun findGarbHelper(): Array<String?> {
        val garbClass = "com.bilibili.lib.ui.garb.Garb".findClass(mClassLoader)
        classesList.filter {
            it.startsWith("com.bilibili.lib.ui.garb")
        }.forEach { c ->
            c.findClassOrNull(mClassLoader)?.declaredMethods?.forEach { m ->
                if (Modifier.isStatic(m.modifiers) && m.returnType == garbClass)
                    return arrayOf(c, m.name)
            }
        }
        return arrayOfNulls(2)
    }

    private fun findReportDownloadThread(): Array<String?> {
        classesList.filter {
            it.startsWith("tv.danmaku.bili.ui.offline.api")
        }.forEach { c ->
            c.findClassOrNull(mClassLoader)?.declaredMethods?.forEach { m ->
                if (m.parameterTypes.size == 2 && m.parameterTypes[0] == Context::class.java && m.parameterTypes[1] == Int::class.javaPrimitiveType) {
                    return arrayOf(c, m.name)
                }
            }
        }
        return arrayOfNulls(2)
    }

    private fun findProxySelectorField(): String? {
        return okHttpClientBuilderClass?.declaredFields?.firstOrNull {
            it.type == ProxySelector::class.java
        }?.name
    }

    private fun findHttpClientBuildMethod(): String? {
        return okHttpClientBuilderClass?.declaredMethods?.firstOrNull {
            it.parameterTypes.isEmpty() && it.returnType == okHttpClientClass
        }?.name
    }

    private fun findOkHttpClientClass(): String? {
        return urlConnectionClass?.declaredConstructors?.filter {
            it.parameterTypes.size == 2
        }?.map { it.parameterTypes[1] }?.firstOrNull()?.name
    }

    private fun findOkHttpClientBuilderClass(): String? {
        return okHttpClientClass?.declaredConstructors?.filter {
            it.parameterTypes.size == 1
        }?.map { it.parameterTypes[0] }?.firstOrNull()?.name
    }

    private fun findShareWrapperMethod(): String? {
        return shareWrapperClass?.declaredMethods?.firstOrNull {
            it.parameterTypes.size == 2 && it.parameterTypes[0] == String::class.java && it.parameterTypes[1] == Bundle::class.java
        }?.name
    }

    private fun findShareWrapperClass(): String? {
        val reg = Regex("^com\\.bilibili\\.lib\\.sharewrapper\\.[^.]*$")
        return classesList.filter {
            it.matches(reg)
        }.firstOrNull { c ->
            c.findClass(mClassLoader)?.declaredMethods?.filter {
                it.parameterTypes.size == 2 && it.parameterTypes[0] == String::class.java && it.parameterTypes[1] == Bundle::class.java
            }?.count()?.let { it > 0 } ?: false
        }
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
        return bangumiApiResponseClass?.methods?.filter {
            "extractResult" == it.name
        }?.map {
            it.parameterTypes[0]
        }?.firstOrNull()?.name
    }

    private fun findUrlField(): OkHttpResult {
        val responseClass = retrofitResponseClass
                ?: return OkHttpResult(null, null)
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

    private fun findFastJsonClass(): Class<*>? {
        return "com.alibaba.fastjson.JSON".findClassOrNull(mClassLoader)
                ?: "com.alibaba.fastjson.a".findClass(mClassLoader)
    }

    private fun findColorArrayField(): String? {
        return themeHelperClass?.declaredFields?.firstOrNull {
            it.type == SparseArray::class.java &&
                    (it.genericType as ParameterizedType).actualTypeArguments[0].toString() == "int[]"
        }?.name
    }

    private fun findColorIdField(): String? {
        return themeIdHelperClass?.declaredFields?.firstOrNull {
            it.type == SparseArray::class.java
        }?.name
    }

    private fun findColumnColorArrayField(): String? {
        return columnHelperClass?.declaredFields?.firstOrNull {
            it.type == SparseArray::class.java &&
                    (it.genericType as ParameterizedType).actualTypeArguments[0].toString() == "int[]"
        }?.name
    }

    private fun findSkinListMethod(): String? {
        val biliSkinListClass = "tv.danmaku.bili.ui.theme.api.BiliSkinList".findClass(mClassLoader)
                ?: return null
        return "tv.danmaku.bili.ui.theme.ThemeStoreActivity".findClass(mClassLoader)?.declaredMethods?.firstOrNull {
            it.parameterTypes.size == 2 && it.parameterTypes[0] == biliSkinListClass &&
                    it.parameterTypes[1] == Boolean::class.javaPrimitiveType
        }?.name
    }

    private fun findSaveSkinMethod(): String? {
        return themeHelperClass?.declaredMethods?.firstOrNull {
            it.parameterTypes.size == 2 && it.parameterTypes[0] == Context::class.java &&
                    it.parameterTypes[1] == Int::class.javaPrimitiveType
        }?.name
    }

    private fun findThemeListClickClass(): String? {
        val themeStoreActivityClass = "tv.danmaku.bili.ui.theme.ThemeStoreActivity".findClass(mClassLoader)
                ?: return null
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
            val classes = classesList.filter {
                it.startsWith("tv.danmaku.bili.ui.garb")
            }.filter { c ->
                c.findClassOrNull(mClassLoader)?.declaredFields?.filter {
                    Modifier.isStatic(it.modifiers) && it.type == Map::class.java
                }?.count() == 1
            }
            if (classes.size == 1) classes[0] else null
        } catch (e: Throwable) {
            null
        }
    }

    private fun findThemeNameField(): String? {
        return themeNameClass?.declaredFields?.firstOrNull {
            it.type == Map::class.java
                    && Modifier.isStatic(it.modifiers)
        }?.name
    }

    private fun findVideoDetailField(): String? {
        val detailClass = "tv.danmaku.bili.ui.video.api.BiliVideoDetail".findClass(mClassLoader)
                ?: return null
        return sectionClass?.declaredFields?.firstOrNull {
            it.type == detailClass
        }?.name
    }

    private fun findSignQueryMethod(): String? {
        val signedQueryClass = "com.bilibili.nativelibrary.SignedQuery".findClass(mClassLoader)
                ?: return null
        return libBiliClass?.declaredMethods?.firstOrNull {
            it.parameterTypes.size == 1 && it.parameterTypes[0] == Map::class.java &&
                    it.returnType == signedQueryClass
        }?.name
    }

    private fun findThemeHelper(): String? {
        return try {
            val classes = classesList.filter {
                it.startsWith("tv.danmaku.bili.ui.theme")
            }.filter { c ->
                c.findClassOrNull(mClassLoader)?.declaredFields?.filter {
                    Modifier.isStatic(it.modifiers)
                }?.filter {
                    it.type == SparseArray::class.java
                }?.count()?.let { it > 1 } ?: false
            }
            if (classes.size == 1) classes[0] else null
        } catch (e: Throwable) {
            null
        }
    }

    private fun findThemeIdHelper(): String? {
        return try {
            val classes = classesList.filter {
                it.startsWith("tv.danmaku.bili.ui.theme")
            }.filter { c ->
                c.findClassOrNull(mClassLoader)?.declaredFields?.filter {
                    Modifier.isStatic(it.modifiers)
                }?.filter {
                    it.type == SparseArray::class.java
                }?.count()?.let { it == 1 } ?: false
            }
            if (classes.size == 1) classes[0] else null
        } catch (e: Throwable) {
            null
        }
    }

    private fun findColumnHelper(): String? {
        return try {
            val classes = classesList.filter {
                it.startsWith("com.bilibili.column.helper")
            }.filter { c ->
                c.findClassOrNull(mClassLoader)?.declaredFields?.filter {
                    Modifier.isStatic(it.modifiers)
                }?.filter {
                    it.type == SparseArray::class.java
                }?.count()?.let { it > 1 } ?: false
            }
            if (classes.size == 1) classes[0] else null
        } catch (e: Throwable) {
            null
        }
    }

    private fun findThemeProcessor(): String? {
        return try {
            val biliSkinListClass = "tv.danmaku.bili.ui.theme.api.BiliSkinList".findClass(mClassLoader)
                    ?: return null
            val classes = classesList.filter {
                it.startsWith("tv.danmaku.bili.ui.theme")
            }.filter { c ->
                c.findClassOrNull(mClassLoader)?.declaredFields?.filter {
                    it.type == biliSkinListClass
                }?.count()?.let { it > 1 } ?: false
            }
            if (classes.size == 1) classes[0] else null
        } catch (e: Throwable) {
            null
        }
    }

    private fun findThemeResetMethods(): String? {
        return themeProcessorClass?.declaredMethods?.filter {
            it.parameterTypes.isEmpty() && it.modifiers == 0
        }?.joinToString(";") { it.name }
    }

    private fun findAddSettingMethod(): String? {
        return "tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment".findClass(mClassLoader)?.declaredMethods?.firstOrNull {
            it.parameterTypes.size == 2 && it.parameterTypes[0] == Context::class.java && it.parameterTypes[1] == List::class.java
        }?.name
    }

    private fun findSettingRouteClass(): String? {
        return try {
            val menuGroupItemClass = "com.bilibili.lib.homepage.mine.MenuGroup\$Item".findClass(mClassLoader)
                    ?: return null
            val accountMineClass = "tv.danmaku.bili.ui.main2.api.AccountMine".findClass(mClassLoader)
                    ?: return null
            val classes = classesList.filter {
                it.startsWith("tv.danmaku.bili.ui.main2")
            }.filter { c ->
                c.findClass(mClassLoader)?.run {
                    declaredFields.filter {
                        it.type == accountMineClass
                    }.count() > 0 && declaredMethods.filter {
                        it.parameterTypes.size == 1 && it.parameterTypes[0] == menuGroupItemClass
                    }.count() > 0
                } ?: false
            }
            if (classes.size == 1) classes[0] else null
        } catch (e: Throwable) {
            null
        }
    }

    private fun findSectionClass(): String? {
        return try {
            val progressBarClass = "tv.danmaku.biliplayer.view.RingProgressBar".findClass(mClassLoader)
                    ?: return null
            val classes = classesList.filter {
                it.startsWith("tv.danmaku.bili.ui.video.section")
            }.filter { c ->
                c.findClassOrNull(mClassLoader)?.declaredFields?.filter {
                    it.type == progressBarClass
                }?.count()?.let { it > 0 } ?: false
            }
            if (classes.size == 1) classes[0] else null
        } catch (e: Throwable) {
            null
        }
    }

    private fun findGetSettingRouteMethod(): String? {
        val menuGroupItemClass = "com.bilibili.lib.homepage.mine.MenuGroup\$Item".findClass(mClassLoader)
                ?: return null
        return settingRouteClass?.declaredMethods?.firstOrNull {
            it.parameterTypes.size == 1 && it.parameterTypes[0] == menuGroupItemClass
        }?.name
    }

    private fun findLikeMethod(): String? {
        return sectionClass?.declaredMethods?.firstOrNull {
            it.parameterTypes.size == 1 && it.parameterTypes[0] == Object::class.java
        }?.name
    }

    private fun findDrawerClass(): String? {
        return try {
            val navigationViewClass = "android.support.design.widget.NavigationView".findClass(mClassLoader)
                    ?: return null
            val regex = Regex("^tv\\.danmaku\\.bili\\.ui\\.main2\\.[^.]*$")
            val classes = classesList.filter {
                it.matches(regex)
            }.filter { c ->
                c.findClassOrNull(mClassLoader)?.run {
                    declaredFields.filter {
                        it.type == navigationViewClass
                    }.count() > 0
                } ?: false
            }
            if (classes.size == 1) classes[0] else null
        } catch (e: Throwable) {
            null
        }
    }

    private fun findDownloadThreadListener(): String? {
        return classesList.filter {
            it.startsWith("tv.danmaku.bili.ui.offline")
        }.firstOrNull { c ->
            c.findClassOrNull(mClassLoader)?.run {
                declaredMethods.filter { m ->
                    m.name == "onClick"
                }.count() > 0 && declaredFields.filter {
                    it.type == TextView::class.java || it.type == downloadingActivityClass
                }.count() > 1
            } ?: false
        }
    }

    private fun findDownloadThreadField(): String? {
        return downloadingActivityClass?.declaredFields?.firstOrNull {
            it.type == Int::class.javaPrimitiveType
        }?.name
    }


    companion object {
        @Volatile
        lateinit var instance: BiliBiliPackage
    }

    class Weak(val initializer: () -> Class<*>?) {
        private var weakReference: WeakReference<Class<*>?>? = null

        operator fun getValue(thisRef: Any?, property: KProperty<*>): Class<*>? {
            return weakReference?.get() ?: let {
                weakReference = WeakReference(initializer())
                weakReference
            }?.get()
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Class<*>) {
            weakReference = WeakReference(value)
        }
    }
}