@file:Suppress("DEPRECATION")

package me.iacn.biliroaming

import android.app.AndroidAppHelper
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import me.iacn.biliroaming.utils.*
import java.io.*
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.net.URL

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
    val settingRouterClass by Weak { mHookInfo["class_setting_router"]?.findClassOrNull(mClassLoader) }
    val themeListClickClass by Weak { mHookInfo["class_theme_list_click"]?.findClassOrNull(mClassLoader) }
    val shareWrapperClass by Weak { mHookInfo["class_share_wrapper"]?.findClassOrNull(mClassLoader) }
    val themeNameClass by Weak { mHookInfo["class_theme_name"]?.findClassOrNull(mClassLoader) }
    val themeProcessorClass by Weak { mHookInfo["class_theme_processor"]?.findClassOrNull(mClassLoader) }
    val drawerClass by Weak { mHookInfo["class_drawer"]?.findClassOrNull(mClassLoader) }
    val generalResponseClass by Weak { "com.bilibili.okretro.GeneralResponse".findClass(mClassLoader) }
    val seasonParamsMapClass by Weak { "com.bilibili.bangumi.data.page.detail.BangumiDetailApiService\$UniformSeasonParamsMap".findClassOrNull(mClassLoader) }
    val seasonParamsClass by Weak { mHookInfo["class_bangumi_params_map"]?.findClassOrNull(mClassLoader) }
    val brandSplashClass by Weak { "tv.danmaku.bili.ui.splash.brand.ui.BaseBrandSplashFragment".findClassOrNull(mClassLoader) }
    val urlConnectionClass by Weak { "com.bilibili.lib.okhttp.huc.OkHttpURLConnection".findClass(mClassLoader) }
    val downloadThreadListenerClass by Weak { mHookInfo["class_download_thread_listener"]?.findClass(mClassLoader) }
    val downloadingActivityClass by Weak { "tv.danmaku.bili.ui.offline.DownloadingActivity".findClassOrNull(mClassLoader) }
    val reportDownloadThreadClass by Weak { mHookInfo["class_report_download_thread"]?.findClass(mClassLoader) }
    val libBiliClass by Weak { "com.bilibili.nativelibrary.LibBili".findClass(mClassLoader) }
    val splashActivityClass by Weak { "tv.danmaku.bili.ui.splash.SplashActivity".findClass(mClassLoader) }
    val mainActivityClass by Weak { "tv.danmaku.bili.MainActivityV2".findClass(mClassLoader) }
    val homeUserCenterClass by Weak { "tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment".findClassOrNull(mClassLoader) }
    val garbHelperClass by Weak { mHookInfo["class_garb_helper"]?.findClass(mClassLoader) }
    val musicNotificationHelperClass by Weak { mHookInfo["class_music_notification_helper"]?.findClass(mClassLoader) }
    val notificationBuilderClass by Weak { mHookInfo["class_notification_builder"]?.findClass(mClassLoader) }
    val absMusicServiceClass by Weak { mHookInfo["class_abs_music_service"]?.findClass(mClassLoader) }
    val menuGroupItemClass by Weak { "com.bilibili.lib.homepage.mine.MenuGroup\$Item".findClassOrNull(mClassLoader) }
    val drawerLayoutClass by Weak {
        "androidx.drawerlayout.widget.DrawerLayout".findClassOrNull(mClassLoader)
                ?: "android.support.v4.widget.DrawerLayout".findClass(mClassLoader)
    }
    val drawerLayoutParamsClass by Weak { mHookInfo["class_drawer_layout_params"]?.findClass(mClassLoader) }
    val splashInfoClass by Weak { "tv.danmaku.bili.ui.splash.brand.BrandShowInfo".findClassOrNull(mClassLoader) }
    val commentRpcClass by Weak { "com.bilibili.app.comm.comment2.model.rpc.CommentRpcKt".findClassOrNull(mClassLoader) }
    val checkBlueClass by Weak { mHookInfo["class_check_blue"]?.findClass(mClassLoader) }
    val kotlinJsonClass by Weak { "kotlinx.serialization.json.Json".findClassOrNull(mClassLoader) }
    val gsonConverterClass by Weak { mHookInfo["class_gson_converter"]?.findClassOrNull(mClassLoader) }
    val playerOptionsPanelHolderClass by Weak { mHookInfo["class_player_options_panel_holder"]?.findClass(mClassLoader) }
    val playerParamsBundleClass by Weak { mHookInfo["class_playerparams_bundle"]?.findClassOrNull(mClassLoader) }
    val playerCoreServiceV2Class by Weak { mHookInfo["class_player_core_service_v2"]?.findClassOrNull(mClassLoader) }
    val hostRequestInterceptorClass by Weak { "com.bililive.bililive.infra.hybrid.interceptor.HostRequestInterceptor".findClass(mClassLoader) }

    val classesList by lazy { mClassLoader.allClassesList() }
    private val accessKeyInstance by lazy {
        ("com.bilibili.cheese.ui.detail.pay.v2.CheesePayHelperV2\$accessKey\$2".findClass(mClassLoader)
                ?: "com.bilibili.bangumi.ui.page.detail.pay.BangumiPayHelperV2\$accessKey\$2".findClass(mClassLoader))?.getStaticObjectField("INSTANCE")
    }

    @Suppress("UNCHECKED_CAST")
    val ids by lazy {
        ObjectInputStream(ByteArrayInputStream(Base64.decode(mHookInfo["map_ids"], Base64.DEFAULT))).readObject() as Map<String, Int>
    }


    val accessKey: String?
        get() {
            var key = sPrefs.getString("customize_accessKey", null)
            if (key.isNullOrBlank()) key = accessKeyInstance?.callMethodAs<String>("invoke")
            return key
        }

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

    fun checkBlue() = mHookInfo["method_check_blue"]

    fun fastJsonParse() = mHookInfo["method_fastjson_parse"]

    fun colorArray() = mHookInfo["field_color_array"]

    fun colorId() = mHookInfo["field_color_id"]

    fun columnColorArray() = mHookInfo["field_column_color_array"]

    fun videoDetailName() = mHookInfo["field_video_detail"]

    fun signQueryName() = mHookInfo["method_sign_query"]

    fun skinList() = mHookInfo["method_skin_list"]

    fun themeReset() = mHookInfo["methods_theme_reset"]

    fun addSetting() = mHookInfo["method_add_setting"]

    fun requestField() = mHookInfo["field_req"]

    fun likeMethod() = mHookInfo["method_like"]

    fun themeName() = mHookInfo["field_theme_name"]

    fun shareWrapper() = mHookInfo["method_share_wrapper"]

    fun downloadingThread() = mHookInfo["field_download_thread"]

    fun reportDownloadThread() = mHookInfo["method_report_download_thread"]

    fun garb() = mHookInfo["method_garb"]

    fun setNotification() = mHookInfo["methods_set_notification"]

    fun mediaSessionToken() = mHookInfo["method_media_session_token"]

    fun absMusicService() = mHookInfo["field_abs_music_service"]

    fun openDrawer() = mHookInfo["method_open_drawer"]

    fun closeDrawer() = mHookInfo["method_close_drawer"]

    fun isDrawerOpen() = mHookInfo["method_is_drawer_open"]

    fun paramsToMap() = mHookInfo["method_params_to_map"]

    fun gson() = mHookInfo["field_gson"]

    fun playbackspeedlist() = mHookInfo["field_playback_speed_list"]

    fun putSerializabletoPlayerParamsBundle() = mHookInfo["method_put_serializable_to_playerparams_bundle"]

    fun getdefaultspeed() = mHookInfo["method_get_default_speed"]

    fun urlField() = mHookInfo["field_url"]

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

        fun <K, V> MutableMap<K, V>.checkOrPut(key: K, checkOption: String? = null, defaultValue: () -> V): MutableMap<K, V> {
            if (checkOption != null) {
                if (!sPrefs.getBoolean(checkOption, false)) return this
            }
            if (!containsKey(key)) {
                put(key, defaultValue())
                needUpdate = true
            }
            return this
        }

        fun <K, V> MutableMap<K, V>.checkOrPut(keys: Array<out K>, checkOption: String? = null, checker: (map: MutableMap<K, V>, keys: Array<out K>) -> Boolean, defaultValue: () -> Array<V>): MutableMap<K, V> {
            if (checkOption != null) {
                if (!sPrefs.getBoolean(checkOption, false)) return this
            }
            if (!checker(this, keys)) {
                putAll(keys.zip(defaultValue()))
                needUpdate = true
            }
            return this
        }

        fun <K, V> MutableMap<K, V>.checkConjunctiveOrPut(vararg keys: K, defaultValue: () -> Array<V>) =
                checkOrPut(keys, null, { m, ks -> ks.fold(true) { acc, k -> acc && m.containsKey(k) } }, defaultValue)

        @Suppress("unused")
        fun <K, V> MutableMap<K, V>.checkDisjunctiveOrPut(vararg keys: K, defaultValue: () -> Array<V>) =
                checkOrPut(keys, null, { m, ks -> ks.fold(false) { acc, k -> acc || m.containsKey(k) } }, defaultValue)

        mHookInfo.checkOrPut("class_retrofit_response") {
            findRetrofitResponseClass()
        }.checkConjunctiveOrPut("field_req", "field_url") {
            findOkHttp()
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
        }.checkOrPut("class_setting_router") {
            findSettingRouterClass()
        }.checkOrPut("method_add_setting") {
            findAddSettingMethod()
        }.checkOrPut("class_drawer") {
            findDrawerClass()
        }.checkOrPut("method_like") {
            findLikeMethod()
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
        }.checkConjunctiveOrPut("methods_set_notification", "class_notification_builder") {
            findSetNotificationMethods()
        }.checkOrPut("class_abs_music_service") {
            findAbsMusicService()
        }.checkOrPut("method_media_session_token") {
            findMediaSessionTokenMethod()
        }.checkOrPut("field_abs_music_service") {
            findAbsMusicServiceField()
        }.checkOrPut("class_drawer_layout_params") {
            findDrawerLayoutParams()
        }.checkConjunctiveOrPut("method_open_drawer", "method_close_drawer") {
            findDrawerMethod()
        }.checkOrPut("method_is_drawer_open") {
            findIsDrawerOpenMethod()
        }.checkConjunctiveOrPut("class_check_blue", "method_check_blue") {
            findCheckBlue()
        }.checkOrPut("map_ids") {
            getMapIds()
        }.checkConjunctiveOrPut("class_bangumi_params_map", "method_params_to_map") {
            findBangumiParamsMap()
        }.checkConjunctiveOrPut("class_gson_converter", "field_gson") {
            findGson()
        }.checkConjunctiveOrPut("class_player_options_panel_holder", "field_playback_speed_list") {
            findPlaybackSpeedList()
        }.checkConjunctiveOrPut("class_playerparams_bundle", "method_put_serializable_to_playerparams_bundle") {
            findPlayerParamsBundle()
        }.checkConjunctiveOrPut("class_player_core_service_v2", "method_get_default_speed") {
            findGetDefaultSpeed()
        }

        Log.d(mHookInfo.filterKeys { it != "map_ids" })
        Log.d("Check hook info completed: needUpdate = $needUpdate")
        return needUpdate
    }

    private fun findGetDefaultSpeed(): Array<String?> {
        val playerCoreServiceV2class = "tv.danmaku.biliplayerv2.service.core.PlayerCoreServiceV2".findClassOrNull(mClassLoader)
                ?: "tv.danmaku.biliplayerimpl.core.PlayerCoreServiceV2".findClassOrNull(mClassLoader)
                ?: classesList.filter {
                    it.startsWith("tv.danmaku.biliplayerv2.service") ||
                            it.startsWith("tv.danmaku.biliplayerimpl")
                }.firstOrNull { c ->
                    c.findClass(mClassLoader)?.declaredFields?.filter {
                        it.type.name == "tv.danmaku.ijk.media.player.IMediaPlayer\$OnErrorListener"
                    }?.count()?.let { it > 0 } ?: false
                }?.findClassOrNull(mClassLoader) ?: return arrayOfNulls(2)
        playerCoreServiceV2class.declaredMethods.forEach { m ->
            if (Modifier.isPublic(m.modifiers) && m.parameterTypes.size == 1 && m.parameterTypes[0] == Boolean::class.java && m.returnType == Float::class.javaPrimitiveType)
                return arrayOf(playerCoreServiceV2class.name, m.name)
        }
        return arrayOfNulls(2)
    }

    private fun findPlayerParamsBundle(): Array<String?> {
        val playerParamsBundleClass = "tv.danmaku.biliplayer.basic.context.c".findClassOrNull(mClassLoader)
                ?: return arrayOfNulls(2)
        playerParamsBundleClass.declaredMethods.forEach { m ->
            if (Modifier.isPublic(m.modifiers) && Modifier.isFinal(m.modifiers) && m.parameterTypes.size == 2 && m.parameterTypes[0] == String::class.java && m.parameterTypes[1] == Serializable::class.java)
                return arrayOf(playerParamsBundleClass.name, m.name)
        }
        return arrayOfNulls(2)
    }

    private fun findPlaybackSpeedList(): Array<String?> {
        classesList.filter {
            it.startsWith("tv.danmaku.biliplayer.features.options.PlayerOptionsPanelHolder") ||
                    it.startsWith("com.bilibili.playerbizcommon.widget.function.setting")
        }.forEach { c ->
            c.findClassOrNull(mClassLoader)?.run {
                declaredFields.forEach { f ->
                    if (Modifier.isStatic(f.modifiers) && f.type == FloatArray::class.java)
                        return arrayOf(c, f.name)
                }
            }
        }
        return arrayOfNulls(2)
    }

    private fun findGson(): Array<String?> {
        val gsonClass = "com.google.gson.Gson".findClassOrNull(mClassLoader)
                ?: return arrayOfNulls(2)
        classesList.filter {
            it.startsWith("com.bilibili.okretro.converter") || it.startsWith("com.bilibili.api.utils")
        }.forEach { c ->
            c.findClassOrNull(mClassLoader)?.run {
                declaredFields.forEach { f ->
                    if (Modifier.isStatic(f.modifiers) && f.type == gsonClass)
                        return arrayOf(c, f.name)
                }
            }
        }
        return arrayOfNulls(2)
    }

    private fun findBangumiParamsMap(): Array<String?> {
        val bangumiDetailApiServiceClass = classesList.filter {
            it.startsWith("com.bilibili.bangumi.data.page.detail")
        }.map { c ->
            c.findClass(mClassLoader)
        }.findLast { c ->
            c?.declaredMethods?.map { it.name }?.contains("getViewSeasonV2") == true
        }
        bangumiDetailApiServiceClass?.declaredClasses?.forEach { c ->
            c.declaredMethods.forEach { m ->
                if (m.returnType == Map::class.java && m.parameterTypes.isEmpty())
                    return arrayOf(c.name, m.name)
            }
        }
        return arrayOfNulls(2)
    }

    private fun getMapIds(): String? {
        val reg = Regex("^tv\\.danmaku\\.bili\\.[^.]*$")
        val mask = Modifier.STATIC or Modifier.PUBLIC or Modifier.FINAL
        val ids = classesList.filter {
            it.matches(reg)
        }.flatMap { c ->
            c.findClass(mClassLoader)?.declaredFields?.filter {
                it.modifiers == mask
                        && it.type == Int::class.javaPrimitiveType
            }.orEmpty()
        }.associate { it.name to it.get(null) as Int }
        val bao = ByteArrayOutputStream()
        ObjectOutputStream(bao).use {
            it.writeObject(ids)
        }
        return Base64.encodeToString(bao.toByteArray(), Base64.DEFAULT)
    }

    private fun findIsDrawerOpenMethod() = try {
        drawerLayoutClass?.getMethod("isDrawerOpen", View::class.java)?.name
    } catch (e: Throwable) {
        drawerLayoutClass?.declaredMethods?.firstOrNull {
            Modifier.isPublic(it.modifiers) &&
                    it.parameterTypes.size == 1 && it.parameterTypes[0] == View::class.java &&
                    it.returnType == Boolean::class.javaPrimitiveType
        }?.name
    }

    private fun findCheckBlue(): Array<String?> {
        if (platform != "android_b") return arrayOfNulls(2)
        classesList.filter {
            it.startsWith("tv.danmaku.android.util")
        }.forEach { c ->
            c.findClassOrNull(mClassLoader)?.declaredMethods?.forEach {
                if (!Modifier.isStatic(it.modifiers) && it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == Context::class.java &&
                        it.returnType == Boolean::class.javaPrimitiveType)
                    return arrayOf(c, it.name)
            }
        }
        return arrayOfNulls(2)
    }

    private fun findDrawerMethod(): Array<String?> = try {
        arrayOf(drawerLayoutClass?.getMethod("openDrawer", View::class.java, Boolean::class.javaPrimitiveType)?.name,
                drawerLayoutClass?.getMethod("closeDrawer", View::class.java, Boolean::class.javaPrimitiveType)?.name)
    } catch (e: Throwable) {
        drawerLayoutClass?.declaredMethods?.filter {
            Modifier.isPublic(it.modifiers) &&
                    it.parameterTypes.size == 2 && it.parameterTypes[0] == View::class.java &&
                    it.parameterTypes[1] == Boolean::class.javaPrimitiveType
        }?.map { it.name }?.toTypedArray() ?: arrayOfNulls(2)
    }

    private fun findDrawerLayoutParams() = drawerLayoutClass?.declaredClasses?.firstOrNull {
        it.superclass == ViewGroup.MarginLayoutParams::class.java
    }?.name

    private fun findAbsMusicServiceField() = musicNotificationHelperClass?.declaredFields?.firstOrNull {
        it.type == absMusicServiceClass
    }?.name

    private fun findMediaSessionTokenMethod() = absMusicServiceClass?.declaredMethods?.firstOrNull {
        it.returnType.name.endsWith("Token")
    }?.name

    private fun findAbsMusicService() = classesList.filter {
        it.startsWith("tv.danmaku.bili.ui.player.notification")
    }.firstOrNull { c ->
        c.findClassOrNull(mClassLoader)?.superclass == Service::class.java
    }

    private fun findSetNotificationMethods(): Array<String?> = musicNotificationHelperClass?.declaredMethods?.lastOrNull {
        it.parameterTypes.size == 1 && it.parameterTypes[0].name.run {
            startsWith("android.support.v4.app") ||
                    startsWith("androidx.core.app") ||
                    startsWith("androidx.core.app.NotificationCompat\$Builder")
        }
    }?.run {
        arrayOf(name, parameterTypes[0].name)
    } ?: arrayOfNulls(2)

    private fun findMusicNotificationHelper() = classesList.filter {
        it.startsWith("tv.danmaku.bili.ui.player.notification")
    }.firstOrNull { c ->
        c.findClassOrNull(mClassLoader)?.declaredFields?.filter {
            it.type == PendingIntent::class.java
        }?.count()?.let { it > 0 } ?: false
    }

    private fun findGarbHelper(): Array<String?> {
        val garbClass = "com.bilibili.lib.ui.garb.Garb".findClassOrNull(mClassLoader)
                ?: return arrayOfNulls(2)
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
                if (m.parameterTypes.size == 2 && m.parameterTypes[0] == Context::class.java && m.parameterTypes[1] == Int::class.javaPrimitiveType)
                    return arrayOf(c, m.name)
            }
        }
        return arrayOfNulls(2)
    }

    private fun findShareWrapperMethod() = shareWrapperClass?.declaredMethods?.firstOrNull {
        it.parameterTypes.size == 2 && it.parameterTypes[0] == String::class.java && it.parameterTypes[1] == Bundle::class.java
    }?.name

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
            ObjectOutputStream(FileOutputStream(hookInfoFile)).use { stream ->
                stream.writeLong(lastUpdateTime)
                stream.writeObject(mHookInfo)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Log.d("Write hook info completed")
    }

    private fun findRetrofitResponseClass() = bangumiApiResponseClass?.methods?.filter {
        "extractResult" == it.name
    }?.map {
        it.parameterTypes[0]
    }?.firstOrNull()?.name

    private fun findOkHttp(): Array<String?> {
        val okHttpRequestClass = hostRequestInterceptorClass?.declaredMethods?.firstOrNull {
            it.parameterTypes.size == 1 && it.parameterTypes[0] == it.returnType
        }?.returnType
        retrofitResponseClass?.declaredConstructors?.forEach { c ->
            c.parameterTypes[0].declaredFields.forEach { f1 ->
                if (f1.type == okHttpRequestClass) {
                    okHttpRequestClass?.declaredFields?.forEach { f2 ->
                        f2.type.declaredMethods.forEach { m ->
                            if (m.parameterTypes.isEmpty() && m.returnType == URL::class.java) {
                                return arrayOf(f1.name, f2.name)
                            }
                        }
                    }
                }
            }
        }
        return arrayOfNulls(2)
    }

    private fun findFastJsonClass(): Class<*>? = "com.alibaba.fastjson.JSON".findClassOrNull(mClassLoader)
            ?: "com.alibaba.fastjson.a".findClass(mClassLoader)

    private fun findColorArrayField() = themeHelperClass?.declaredFields?.firstOrNull {
        it.type == SparseArray::class.java &&
                (it.genericType as ParameterizedType).actualTypeArguments[0].toString() == "int[]"
    }?.name

    private fun findColorIdField() = themeIdHelperClass?.declaredFields?.firstOrNull {
        it.type == SparseArray::class.java
    }?.name

    private fun findColumnColorArrayField() = columnHelperClass?.declaredFields?.firstOrNull {
        it.type == SparseArray::class.java &&
                (it.genericType as ParameterizedType).actualTypeArguments[0].toString() == "int[]"
    }?.name

    private fun findSkinListMethod(): String? {
        val biliSkinListClass = "tv.danmaku.bili.ui.theme.api.BiliSkinList".findClass(mClassLoader)
                ?: return null
        return "tv.danmaku.bili.ui.theme.ThemeStoreActivity".findClass(mClassLoader)?.declaredMethods?.firstOrNull {
            it.parameterTypes.size == 2 && it.parameterTypes[0] == biliSkinListClass &&
                    it.parameterTypes[1] == Boolean::class.javaPrimitiveType
        }?.name
    }

    private fun findThemeListClickClass() = "tv.danmaku.bili.ui.theme.ThemeStoreActivity".findClassOrNull(mClassLoader)?.declaredClasses?.firstOrNull {
        it.interfaces.contains(View.OnClickListener::class.java)
    }?.name

    private fun findThemeNameClass() = classesList.filter {
        it.startsWith("tv.danmaku.bili.ui.garb")
    }.firstOrNull { c ->
        c.findClassOrNull(mClassLoader)?.declaredFields?.filter {
            Modifier.isStatic(it.modifiers) && it.type == Map::class.java
        }?.count() == 1
    }

    private fun findThemeNameField() = themeNameClass?.declaredFields?.firstOrNull {
        it.type == Map::class.java
                && Modifier.isStatic(it.modifiers)
    }?.name

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

    private fun findThemeHelper() = classesList.filter {
        it.startsWith("tv.danmaku.bili.ui.theme")
    }.firstOrNull { c ->
        c.findClassOrNull(mClassLoader)?.declaredFields?.filter {
            Modifier.isStatic(it.modifiers)
        }?.filter {
            it.type == SparseArray::class.java
        }?.count()?.let { it > 1 } ?: false
    }

    private fun findThemeIdHelper() = classesList.filter {
        it.startsWith("tv.danmaku.bili.ui.theme")
    }.firstOrNull { c ->
        c.findClassOrNull(mClassLoader)?.declaredFields?.filter {
            Modifier.isStatic(it.modifiers)
        }?.filter {
            it.type == SparseArray::class.java
        }?.count()?.let { it == 1 } ?: false
    }

    private fun findColumnHelper() = classesList.filter {
        it.startsWith("com.bilibili.column.helper")
    }.firstOrNull { c ->
        c.findClassOrNull(mClassLoader)?.declaredFields?.filter {
            Modifier.isStatic(it.modifiers)
        }?.filter {
            it.type == SparseArray::class.java
        }?.count()?.let { it > 1 } ?: false
    }

    private fun findThemeProcessor(): String? {
        val biliSkinListClass = "tv.danmaku.bili.ui.theme.api.BiliSkinList".findClassOrNull(mClassLoader)
        return classesList.filter {
            it.startsWith("tv.danmaku.bili.ui.theme")
        }.firstOrNull { c ->
            c.findClassOrNull(mClassLoader)?.declaredFields?.filter {
                it.type == biliSkinListClass
            }?.count()?.let { it > 1 } ?: false
        }
    }

    private fun findThemeResetMethods() = themeProcessorClass?.declaredMethods?.filter {
        it.parameterTypes.isEmpty() && it.modifiers == 0
    }?.joinToString(";") { it.name }

    private fun findAddSettingMethod() = homeUserCenterClass?.declaredMethods?.firstOrNull {
        it.parameterTypes.size == 2 && it.parameterTypes[0] == Context::class.java && it.parameterTypes[1] == List::class.java
    }?.name

    private fun findSettingRouterClass() = classesList.filter {
        it.startsWith("tv.danmaku.bili.ui.main2.mine")
    }.firstOrNull { c ->
        c.findClass(mClassLoader)?.run {
            declaredFields.filter {
                it.type == menuGroupItemClass && Modifier.isPublic(it.modifiers)
            }.count() > 0
        } ?: false
    }

    private fun findSectionClass(): String? {
        val progressBarClass = "tv.danmaku.biliplayer.view.RingProgressBar".findClass(mClassLoader)
        return classesList.filter {
            it.startsWith("tv.danmaku.bili.ui.video.section")
        }.firstOrNull { c ->
            c.findClassOrNull(mClassLoader)?.declaredFields?.filter {
                it.type == progressBarClass
            }?.count()?.let { it > 0 } ?: false
        }
    }

    private fun findLikeMethod() = sectionClass?.declaredMethods?.firstOrNull {
        it.parameterTypes.size == 1 && it.parameterTypes[0] == Object::class.java
    }?.name

    private fun findDrawerClass(): String? {
        val navigationViewClass = "android.support.design.widget.NavigationView".findClassOrNull(mClassLoader)
                ?: return null
        val regex = Regex("^tv\\.danmaku\\.bili\\.ui\\.main2\\.[^.]*$")
        return classesList.filter {
            it.matches(regex)
        }.firstOrNull { c ->
            c.findClassOrNull(mClassLoader)?.run {
                declaredFields.filter {
                    it.type == navigationViewClass
                }.count() > 0
            } ?: false
        }
    }

    private fun findDownloadThreadListener() = classesList.filter {
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

    private fun findDownloadThreadField() = downloadingActivityClass?.declaredFields?.firstOrNull {
        it.type == Int::class.javaPrimitiveType
    }?.name


    companion object {
        @Volatile
        lateinit var instance: BiliBiliPackage
    }
}
