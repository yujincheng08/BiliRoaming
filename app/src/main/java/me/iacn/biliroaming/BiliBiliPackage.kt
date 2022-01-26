@file:Suppress("DEPRECATION")

package me.iacn.biliroaming

import android.app.AndroidAppHelper
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.os.Bundle
import android.text.style.ClickableSpan
import android.text.style.LineBackgroundSpan
import android.util.Base64
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dalvik.system.BaseDexClassLoader
import me.iacn.biliroaming.utils.*
import java.io.*
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.net.URL
import java.nio.channels.ByteChannel
import kotlin.math.max

/**
 * Created by iAcn on 2019/4/5
 * Email i@iacn.me
 */
class BiliBiliPackage constructor(private val mClassLoader: ClassLoader, mContext: Context) {
    private val mHookInfo: MutableMap<String, String?> = readHookInfo(mContext)
    val bangumiApiResponseClass by Weak {
        "com.bilibili.bangumi.data.common.api.BangumiApiResponse".findClassOrNull(
            mClassLoader
        )
    }
    val rxGeneralResponseClass by Weak {
        "com.bilibili.okretro.call.rxjava.RxGeneralResponse".findClassOrNull(
            mClassLoader
        )
    }
    val fastJsonClass by Weak { mHookInfo["class_fastjson"]?.findClassOrNull(mClassLoader) }
    val bangumiUniformSeasonClass by Weak {
        "com.bilibili.bangumi.data.page.detail.entity.BangumiUniformSeason".findClass(
            mClassLoader
        )
    }
    val sectionClass by Weak { mHookInfo["class_section"]?.findClassOrNull(mClassLoader) }
    val partySectionClass by Weak { mHookInfo["class_party_section"]?.findClassOrNull(mClassLoader) }
    val retrofitResponseClass by Weak {
        mHookInfo["class_retrofit_response"]?.findClassOrNull(
            mClassLoader
        )
    }
    val themeHelperClass by Weak { mHookInfo["class_theme_helper"]?.findClassOrNull(mClassLoader) }
    val themeIdHelperClass by Weak {
        mHookInfo["class_theme_id_helper"]?.findClassOrNull(
            mClassLoader
        )
    }
    val columnHelperClass by Weak { mHookInfo["class_column_helper"]?.findClassOrNull(mClassLoader) }
    val settingRouterClass by Weak { mHookInfo["class_setting_router"]?.findClassOrNull(mClassLoader) }
    val themeListClickClass by Weak {
        mHookInfo["class_theme_list_click"]?.findClassOrNull(
            mClassLoader
        )
    }
    val shareWrapperClass by Weak { mHookInfo["class_share_wrapper"]?.findClassOrNull(mClassLoader) }
    val themeNameClass by Weak { mHookInfo["class_theme_name"]?.findClassOrNull(mClassLoader) }
    val themeProcessorClass by Weak {
        mHookInfo["class_theme_processor"]?.findClassOrNull(
            mClassLoader
        )
    }
    val drawerClass by Weak { mHookInfo["class_drawer"]?.findClassOrNull(mClassLoader) }
    val generalResponseClass by Weak {
        "com.bilibili.okretro.GeneralResponse".findClassOrNull(
            mClassLoader
        )
    }
    val seasonParamsMapClass by Weak {
        "com.bilibili.bangumi.data.page.detail.BangumiDetailApiService\$UniformSeasonParamsMap".findClassOrNull(
            mClassLoader
        )
    }
    val seasonParamsClass by Weak {
        mHookInfo["class_bangumi_params_map"]?.findClassOrNull(
            mClassLoader
        )
    }
    val brandSplashClass by Weak {
        "tv.danmaku.bili.ui.splash.brand.ui.BaseBrandSplashFragment".findClassOrNull(
            mClassLoader
        )
    }
    val urlConnectionClass by Weak {
        "com.bilibili.lib.okhttp.huc.OkHttpURLConnection".findClassOrNull(
            mClassLoader
        )
    }
    val downloadThreadListenerClass by Weak {
        mHookInfo["class_download_thread_listener"]?.findClassOrNull(
            mClassLoader
        )
    }
    val downloadingActivityClass by Weak {
        "tv.danmaku.bili.ui.offline.DownloadingActivity".findClassOrNull(
            mClassLoader
        )
    }
    val reportDownloadThreadClass by Weak {
        mHookInfo["class_report_download_thread"]?.findClassOrNull(
            mClassLoader
        )
    }
    val libBiliClass by Weak { "com.bilibili.nativelibrary.LibBili".findClassOrNull(mClassLoader) }
    val splashActivityClass by Weak {
        "tv.danmaku.bili.ui.splash.SplashActivity".findClassOrNull(
            mClassLoader
        ) ?: "tv.danmaku.bili.MainActivityV2".findClass(mClassLoader)
    }
    val mainActivityClass by Weak { "tv.danmaku.bili.MainActivityV2".findClass(mClassLoader) }
    val homeUserCenterClass by Weak {
        "tv.danmaku.bilibilihd.ui.main.mine.HdHomeUserCenterFragment".findClassOrNull(
            mClassLoader
        ) ?: "tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment".findClassOrNull(
            mClassLoader
        )
    }
    val musicNotificationHelperClass by Weak {
        mHookInfo["class_music_notification_helper"]?.findClassOrNull(
            mClassLoader
        )
    }
    val liveNotificationHelperClass by Weak {
        mHookInfo["class_live_notification_helper"]?.findClassOrNull(
            mClassLoader
        )
    }
    val notificationBuilderClass by Weak {
        mHookInfo["class_notification_builder"]?.findClassOrNull(
            mClassLoader
        )
    }
    val absMusicServiceClass by Weak {
        mHookInfo["class_abs_music_service"]?.findClassOrNull(
            mClassLoader
        )
    }
    val menuGroupItemClass by Weak {
        "com.bilibili.lib.homepage.mine.MenuGroup\$Item".findClassOrNull(
            mClassLoader
        )
    }
    val drawerLayoutClass by Weak {
        "androidx.drawerlayout.widget.DrawerLayout".findClassOrNull(mClassLoader)
            ?: "android.support.v4.widget.DrawerLayout".findClassOrNull(mClassLoader)
    }
    val drawerLayoutParamsClass by Weak {
        mHookInfo["class_drawer_layout_params"]?.findClassOrNull(
            mClassLoader
        )
    }
    val splashInfoClass by Weak {
        "tv.danmaku.bili.ui.splash.brand.BrandShowInfo".findClassOrNull(
            mClassLoader
        ) ?: "tv.danmaku.bili.ui.splash.brand.model.BrandShowInfo".findClassOrNull(mClassLoader)
    }
    val commentCopyClass by Weak {
        mHookInfo["class_comment_long_click"]?.findClassOrNull(
            mClassLoader
        )
    }
    val kotlinJsonClass by Weak { "kotlinx.serialization.json.Json".findClassOrNull(mClassLoader) }
    val gsonConverterClass by Weak { mHookInfo["class_gson_converter"]?.findClassOrNull(mClassLoader) }
    val playerCoreServiceV2Class by Weak {
        mHookInfo["class_player_core_service_v2"]?.findClassOrNull(
            mClassLoader
        )
    }
    val hostRequestInterceptorClass by Weak {
        "com.bililive.bililive.infra.hybrid.interceptor.HostRequestInterceptor".findClassOrNull(
            mClassLoader
        )
            ?: "com.bililive.bililive.liveweb.interceptor.a".findClassOrNull(mClassLoader)
    }
    val teenagersModeDialogActivityClass by Weak {
        "com.bilibili.teenagersmode.ui.TeenagersModeDialogActivity".findClassOrNull(
            mClassLoader
        )
    }
    val gsonClass by Weak { "com.google.gson.Gson".findClassOrNull(mClassLoader) }
    val pegasusFeedClass by Weak { mHookInfo["class_pegasus_feed"]?.findClassOrNull(mClassLoader) }
    val okhttpResponseClass by Weak {
        mHookInfo["class_okhttp_response"]?.findClassOrNull(
            mClassLoader
        )
    }
    val subtitleSpanClass by Weak { mHookInfo["class_subtitle_span"]?.findClassOrNull(mClassLoader) }
    val chronosSwitchClass by Weak { mHookInfo["class_chronos_switch"]?.findClassOrNull(mClassLoader) }
    val biliSpaceClass by Weak {
        "com.bilibili.app.authorspace.api.BiliSpace".findClassOrNull(
            mClassLoader
        )
    }
    val biliVideoDetailClass by Weak {
        "tv.danmaku.bili.ui.video.api.BiliVideoDetail".findClassOrNull(
            mClassLoader
        ) ?: "tv.danmaku.bili.videopage.data.view.model.BiliVideoDetail".findClassOrNull(
            mClassLoader
        )
    }
    val commentSpanTextViewClass by Weak {
        mHookInfo["class_comment_span"]?.findClassOrNull(
            mClassLoader
        )
    }
    val commentSpanEllipsisTextViewClass by Weak {
        "com.bilibili.app.comm.comment2.widget.CommentSpanEllipsisTextView".findClassOrNull(mClassLoader)
    }
    val liveRoomActivityClass by Weak {
        "com.bilibili.bililive.room.ui.roomv3.LiveRoomActivityV3".findClassOrNull(mClassLoader)
    }

    val storyVideoActivityClass by Weak {
        "com.bilibili.video.story.StoryVideoActivity".findClassOrNull(mClassLoader)
    }

    val okioWrapperClass by Weak { mHookInfo["class_okio_wrapper"]?.findClassOrNull(mClassLoader) }
    val progressBarClass by Weak {
        "tv.danmaku.biliplayer.view.RingProgressBar".findClassOrNull(
            mClassLoader
        ) ?: "com.bilibili.playerbizcommon.view.RingProgressBar".findClassOrNull(mClassLoader)
    }
    val videoUpperAdClass by Weak { mHookInfo["class_video_upper_ad"]?.findClassOrNull(mClassLoader) }

    val ellipsizingTextViewClass by Weak {
        "com.bilibili.bplus.followingcard.widget.EllipsizingTextView".findClassOrNull(
            mClassLoader
        )
    }

    val dynamicDescHolderListenerClass by Weak {
        mHookInfo["class_dyn_desc_holder_listener"]?.findClassOrNull(
            mClassLoader
        )
    }

    val videoSubtitleClass by Weak { mHookInfo["class_video_subtitle"]?.findClassOrNull(mClassLoader) }
    val subtitleItemClass by Weak { mHookInfo["class_subtitle_item"]?.findClassOrNull(mClassLoader) }

    val classesList by lazy {
        mClassLoader.allClassesList {
            val serviceField = it.javaClass.findFirstFieldByExactTypeOrNull(
                "com.bilibili.lib.tribe.core.internal.loader.DefaultClassLoaderService".findClassOrNull(
                    mClassLoader
                )
            )
            val delegateField = it.javaClass.findFirstFieldByExactTypeOrNull(
                "com.bilibili.lib.tribe.core.internal.loader.TribeLoaderDelegate".findClassOrNull(
                    mClassLoader
                )
            )
            if (serviceField != null) {
                serviceField.type.declaredFields.filter { f ->
                    f.type == ClassLoader::class.java
                }.map { f ->
                    it.getObjectFieldOrNull(serviceField.name)?.getObjectFieldOrNull(f.name)
                }.firstOrNull { o ->
                    o?.javaClass?.name?.startsWith("com.bilibili") == false
                } as? BaseDexClassLoader ?: it
            } else if (delegateField != null) {
                val loaderField =
                    delegateField.type.findFirstFieldByExactTypeOrNull(ClassLoader::class.java)
                val out = it.getObjectFieldOrNull(delegateField.name)
                    ?.getObjectFieldOrNull(loaderField?.name)
                if (BaseDexClassLoader::class.java.isInstance(out)) out as BaseDexClassLoader else it
            } else it
        }.also { Log.d("classlist size: ${it.size}") }
    }
    private val accessKeyInstance by lazy {
        ("com.bilibili.cheese.ui.detail.pay.v2.CheesePayHelperV2\$accessKey\$2".findClassOrNull(
            mClassLoader
        )
            ?: "com.bilibili.bangumi.ui.page.detail.pay.BangumiPayHelperV2\$accessKey\$2".findClassOrNull(
                mClassLoader
            ))?.getStaticObjectField("INSTANCE")
    }

    @Suppress("UNCHECKED_CAST")
    val ids by lazy {
        ObjectInputStream(
            ByteArrayInputStream(
                Base64.decode(
                    mHookInfo["map_ids"],
                    Base64.DEFAULT
                )
            )
        ).readObject() as Map<String, Int>
    }


    fun getCustomizeAccessKey(area: String): String? {
        var key = sPrefs.getString("${area}_accessKey", null)
        if (key.isNullOrBlank()) key = accessKey
        return key
    }

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


    fun fastJsonParse() = mHookInfo["method_fastjson_parse"]

    fun colorArray() = mHookInfo["field_color_array"]

    fun colorId() = mHookInfo["field_color_id"]

    fun columnColorArray() = mHookInfo["field_column_color_array"]

    fun signQueryName() = mHookInfo["method_sign_query"]

    fun skinList() = mHookInfo["method_skin_list"]

    fun themeReset() = mHookInfo["methods_theme_reset"]

    fun addSetting() = mHookInfo["method_add_setting"]

    fun requestField() = mHookInfo["field_req"]

    fun likeMethod() = mHookInfo["method_like"]

    fun partyLikeMethod() = mHookInfo["method_party_like"]

    fun themeName() = mHookInfo["field_theme_name"]

    fun shareWrapper() = mHookInfo["method_share_wrapper"]

    fun downloadingThread() = mHookInfo["field_download_thread"]

    fun reportDownloadThread() = mHookInfo["method_report_download_thread"]

    fun setNotification() = mHookInfo["methods_set_notification"]

    fun openDrawer() = mHookInfo["method_open_drawer"]

    fun closeDrawer() = mHookInfo["method_close_drawer"]

    fun isDrawerOpen() = mHookInfo["method_is_drawer_open"]

    fun paramsToMap() = mHookInfo["method_params_to_map"]

    fun gson() = mHookInfo["field_gson"]

    fun defaultSpeed() = mHookInfo["method_get_default_speed"]

    fun urlField() = mHookInfo["field_url"]

    fun gsonToJson() = mHookInfo["method_gson_tojson"]

    fun gsonFromJson() = mHookInfo["method_gson_fromjson"]

    fun pegasusFeed() = mHookInfo["method_pegasus_feed"]

    fun commentCopy() = mHookInfo["class_comment_long_click"]

    fun descCopy() = mHookInfo["method_desc_copy"]

    fun descCopyView() = mHookInfo["classes_desc_copy_view"]

    fun responseDataField() = lazy {
        try {
            rxGeneralResponseClass?.getDeclaredField("data")?.name
        } catch (e: NoSuchFieldException) {
            "_data"
        }
    }

    fun okioLength() = mHookInfo["field_okio_length"]

    fun okio() = mHookInfo["field_okio"]

    fun okioInputStream() = mHookInfo["method_okio_buffer_input_stream"]

    fun okioReadFrom() = mHookInfo["method_okio_buffer_read_from"]

    fun videoUpperAd() = mHookInfo["method_video_upper_ad"]

    private fun readHookInfo(context: Context): MutableMap<String, String?> {
        try {
            val hookInfoFile = File(context.cacheDir, Constant.HOOK_INFO_FILE_NAME)
            Log.d("Reading hook info: $hookInfoFile")
            val startTime = System.currentTimeMillis()
            if (hookInfoFile.isFile && hookInfoFile.canRead()) {
                val lastUpdateTime = context.packageManager.getPackageInfo(
                    AndroidAppHelper.currentPackageName(),
                    0
                ).lastUpdateTime
                val lastModuleUpdateTime = try {
                    context.packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, 0)
                } catch (e: Throwable) {
                    null
                }?.lastUpdateTime ?: 0
                val stream = ObjectInputStream(FileInputStream(hookInfoFile))
                val lastHookInfoUpdateTime = stream.readLong()
                @Suppress("UNCHECKED_CAST")
                if (lastHookInfoUpdateTime >= lastUpdateTime && lastHookInfoUpdateTime >= lastModuleUpdateTime)
                    return stream.readObject() as MutableMap<String, String?>
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

        fun <K, V> MutableMap<K, V>.checkOrPut(
            key: K,
            checkOption: String? = null,
            defaultValue: () -> V
        ): MutableMap<K, V> {
            if (checkOption != null) {
                if (!sPrefs.getBoolean(checkOption, false)) return this
            }
            if (!containsKey(key)) {
                put(key, defaultValue())
                needUpdate = true
            }
            return this
        }

        fun <K, V> MutableMap<K, V>.checkOrPut(
            keys: Array<out K>,
            checkOption: String? = null,
            checker: (map: MutableMap<K, V>, keys: Array<out K>) -> Boolean,
            defaultValue: () -> Array<V>
        ): MutableMap<K, V> {
            if (checkOption != null) {
                if (!sPrefs.getBoolean(checkOption, false)) return this
            }
            if (!checker(this, keys)) {
                putAll(keys.zip(defaultValue()))
                needUpdate = true
            }
            return this
        }

        fun <K, V> MutableMap<K, V>.checkConjunctiveOrPut(
            vararg keys: K,
            defaultValue: () -> Array<V>
        ) =
            checkOrPut(
                keys,
                null,
                { m, ks -> ks.fold(true) { acc, k -> acc && m.containsKey(k) } },
                defaultValue
            )

        @Suppress("unused")
        fun <K, V> MutableMap<K, V>.checkDisjunctiveOrPut(
            vararg keys: K,
            defaultValue: () -> Array<V>
        ) =
            checkOrPut(
                keys,
                null,
                { m, ks -> ks.fold(false) { acc, k -> acc || m.containsKey(k) } },
                defaultValue
            )

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
        }.checkOrPut("class_party_section") {
            findPartySectionClass()
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
        }.checkOrPut("method_party_like") {
            findPartyLikeMethod()
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
        }.checkOrPut("class_live_notification_helper") {
            findLiveNotificationHelper()
        }.checkConjunctiveOrPut("methods_set_notification", "class_notification_builder") {
            findSetNotificationMethods()
        }.checkOrPut("class_abs_music_service") {
            findAbsMusicService()
        }.checkOrPut("class_drawer_layout_params") {
            findDrawerLayoutParams()
        }.checkConjunctiveOrPut("method_open_drawer", "method_close_drawer") {
            findDrawerMethod()
        }.checkOrPut("method_is_drawer_open") {
            findIsDrawerOpenMethod()
        }.checkOrPut("map_ids") {
            getMapIds()
        }.checkConjunctiveOrPut("class_bangumi_params_map", "method_params_to_map") {
            findBangumiParamsMap()
        }.checkConjunctiveOrPut("class_gson_converter", "field_gson") {
            findGson()
        }.checkConjunctiveOrPut("class_player_options_panel_holder", "field_playback_speed_list") {
            findPlaybackSpeedList()
        }.checkConjunctiveOrPut("class_player_core_service_v2", "method_get_default_speed") {
            findGetDefaultSpeed()
        }.checkConjunctiveOrPut("method_gson_tojson", "method_gson_fromjson") {
            arrayOf(findGsonToJsonMethod(), findGsonFromJsonMethod())
        }.checkConjunctiveOrPut(
            "class_pegasus_feed",
            "class_okhttp_response",
            "method_pegasus_feed"
        ) {
            findPegasusFeed()
        }.checkOrPut("class_chronos_switch") {
            findChronosSwitch()
        }.checkOrPut("class_subtitle_span") {
            findSubtitleSpan()
        }.checkOrPut("class_comment_long_click") {
            findCommentLongClick()
        }.checkConjunctiveOrPut(
            "class_okio_wrapper",
            "field_okio",
            "field_okio_length"
        ) {
            findOkioWrapper()
        }.checkConjunctiveOrPut(
            "class_okio_buffer",
            "method_okio_buffer_input_stream",
            "method_okio_buffer_read_from"
        ) {
            findOkioBuffer()
        }.checkConjunctiveOrPut(
            "class_video_upper_ad",
            "method_video_upper_ad"
        ) {
            findVideoUpperAd()
        }.checkOrPut("class_comment_span") {
            findCommentSpan()
        }.checkOrPut("class_dyn_desc_holder_listener") {
            findDynamicDescHolderListener()
        }.checkConjunctiveOrPut("classes_desc_copy_view", "method_desc_copy") {
            findDescCopyView()
        }.checkOrPut("class_video_subtitle") {
            findVideoSubtitleClass()
        }.checkOrPut("class_subtitle_item") {
            findSubtitleItemClass()
        }

        Log.d(mHookInfo.filterKeys { it != "map_ids" })
        Log.d("Check hook info completed: needUpdate = $needUpdate")
        return needUpdate
    }

    private fun findCommentSpan() =
        "com.bilibili.app.comm.comment2.widget.CommentExpandableTextView".findClassOrNull(
            mClassLoader
        )?.name ?: classesList.filter {
            it.startsWith("com.bilibili.app.comm.comment2.widget")
        }.firstOrNull { c ->
            c.findClass(mClassLoader).declaredFields.count {
                it.type == Float::class.javaPrimitiveType
            } > 1
        }

    private fun findDescCopyView(): Array<String?> {
        val classes = ArrayList<String>()
        val methods = ArrayList<String>()
        classesList.filter {
            it.startsWith("tv.danmaku.bili.ui.video.profile.info.DescViewHolder")
        }.map { c ->
            c.findClass(mClassLoader)
        }.forEach { c ->
            c.declaredMethods.firstOrNull { m ->
                m.parameterTypes.size == 2 && m.parameterTypes[0] == View::class.java && m.parameterTypes[1] == ClickableSpan::class.java
            }?.let { classes.add(c.name); methods.add(it.name) }
        }
        return if (classes.size > 0) arrayOf(
            classes.joinToString(";"),
            methods.joinToString(";")
        ) else arrayOfNulls(2)
    }

    private fun findDynamicDescHolderListener() = classesList.filter {
        it.startsWith("com.bilibili.bplus.followinglist.module.item")
    }.firstOrNull { c ->
        c.findClass(mClassLoader).run {
            declaredMethods.filter {
                it.name == "onLongClick"
            }.count() == 1
        }
    }

    private fun findVideoUpperAd(): Array<String?> {
        val adHolderClass =
            "com.bilibili.ad.adview.videodetail.upper.VideoUpperAdSectionViewHolder".findClassOrNull(
                mClassLoader
            ) ?: "com.bilibili.ad.adview.videodetail.upper.VideoUpperAdViewHolder".findClassOrNull(
                mClassLoader
            ) ?: return arrayOfNulls(2)
        val reg = Regex("^com\\.bilibili\\.ad\\.adview\\.videodetail\\.upper\\.[^.]*$")
        classesList.filter {
            it.startsWith("com.bilibili.ad.adview.videodetail.upper")
        }.filter { c ->
            c.matches(reg)
        }.map { c ->
            c.findClass(mClassLoader)
        }.forEach { c ->
            c.declaredMethods.forEach { m ->
                if (Modifier.isPublic(m.modifiers) && m.parameterTypes.size >= 2 &&
                    m.parameterTypes[0] == ViewGroup::class.java &&
                    m.returnType == adHolderClass
                ) return arrayOf(c.name, m.name)
            }
        }
        return arrayOfNulls(2)
    }

    private fun findOkioBuffer(): Array<String?> {
        val okioBufferClass = classesList.filter {
            it.startsWith("okio")
        }.map { c ->
            c.findClass(mClassLoader)
        }.firstOrNull { c ->
            c.interfaces.contains(ByteChannel::class.java)
        } ?: return arrayOfNulls(3)
        val okioInputStreamMethod = okioBufferClass.declaredMethods.firstOrNull {
            it.parameterTypes.isEmpty() && it.returnType == InputStream::class.java
        } ?: return arrayOfNulls(3)
        val okioReadFromMethod = okioBufferClass.declaredMethods.firstOrNull {
            it.parameterTypes.size == 1 && it.parameterTypes[0] == InputStream::class.java
        } ?: return arrayOfNulls(3)
        return arrayOf(okioBufferClass.name, okioInputStreamMethod.name, okioReadFromMethod.name)
    }

    private fun findOkioWrapper(): Array<String?> {
        val responseClass = okhttpResponseClass ?: return arrayOfNulls(3)
        val wrapperClass = classesList.filter {
            it.startsWith(responseClass.name)
        }.map { c ->
            c.findClass(mClassLoader)
        }.firstOrNull { c ->
            c.superclass == responseClass
        } ?: return arrayOfNulls(3)
        val okioField = wrapperClass.declaredFields.firstOrNull { f ->
            f.type.name.startsWith("okio")
        } ?: return arrayOfNulls(3)
        val okioLenField = wrapperClass.declaredFields.firstOrNull { f ->
            f.type == Long::class.javaPrimitiveType
        } ?: return arrayOfNulls(3)
        return arrayOf(
            wrapperClass.name,
            okioField.name,
            okioLenField.name
        )
    }

    private fun findCommentLongClick() = classesList.filter {
        it.startsWith("com.bilibili.app.comm.comment2")
    }.firstOrNull { c ->
        c.findClass(mClassLoader).run {
            declaredMethods.filter {
                it.name == "onLongClick"
            }.count() == 1
        }
    }

    private fun findSubtitleSpan() = classesList.filter {
        it.startsWith("tv.danmaku.danmaku.subititle") ||
                it.startsWith("tv.danmaku.danmaku.subtitle")
    }.firstOrNull { c ->
        c.findClass(mClassLoader).interfaces.contains(LineBackgroundSpan::class.java)
    }

    private fun findChronosSwitch(): String? {
        val regex = Regex("""^tv\.danmaku\.chronos\.wrapper\.[^.]*$""")
        return classesList.filter {
            it.matches(regex)
        }.firstOrNull { c ->
            c.findClass(mClassLoader).declaredFields.count {
                it.type == Boolean::class.javaObjectType
            } >= 5
        }
    }

    private fun findPegasusFeed(): Array<String?> {
        val itemClass =
            "com.bilibili.pegasus.api.model.BasicIndexItem".findClassOrNull(mClassLoader)
        classesList.filter {
            it.startsWith("com.bilibili.pegasus.api")
        }.map { c ->
            c.findClass(mClassLoader)
        }.filter { c ->
            c.declaredMethods.firstOrNull {
                it.returnType == itemClass
            } != null
        }.forEach { c ->
            c.declaredMethods.forEach {
                if (it.parameterTypes.size == 1 && it.returnType == generalResponseClass)
                    return arrayOf(c.name, it.parameterTypes[0].name, it.name)
            }
        }
        return arrayOfNulls(2)
    }

    private fun findGsonToJsonMethod() = gsonClass?.declaredMethods?.firstOrNull { m ->
        m.returnType == String::class.java && m.parameterTypes.size == 1 && m.parameterTypes[0] == Object::class.java
    }?.name

    private fun findGsonFromJsonMethod() = gsonClass?.declaredMethods?.firstOrNull { m ->
        m.returnType == Object::class.java && m.parameterTypes.size == 2 && m.parameterTypes[0] == String::class.java && m.parameterTypes[1] == Class::class.java
    }?.name

    private fun findGetDefaultSpeed(): Array<String?> {
        val playerCoreServiceV2class =
            "tv.danmaku.biliplayerv2.service.core.PlayerCoreServiceV2".findClassOrNull(mClassLoader)
                ?: "tv.danmaku.biliplayerimpl.core.PlayerCoreServiceV2".findClassOrNull(mClassLoader)
                ?: classesList.filter {
                    it.startsWith("tv.danmaku.biliplayerv2.service") ||
                            it.startsWith("tv.danmaku.biliplayerimpl")
                }.firstOrNull { c ->
                    c.findClass(mClassLoader).declaredFields.filter {
                        it.type.name == "tv.danmaku.ijk.media.player.IMediaPlayer\$OnErrorListener"
                    }.count().let { it > 0 }
                }?.findClassOrNull(mClassLoader) ?: return arrayOfNulls(2)
        playerCoreServiceV2class.declaredMethods.forEach { m ->
            if (Modifier.isPublic(m.modifiers) && m.parameterTypes.size == 1 && m.parameterTypes[0] == Boolean::class.java && m.returnType == Float::class.javaPrimitiveType)
                return arrayOf(playerCoreServiceV2class.name, m.name)
        }
        return arrayOfNulls(2)
    }

    private fun findPlaybackSpeedList(): Array<String?> {
        classesList.filter {
            it.startsWith("tv.danmaku.biliplayer.features.options.PlayerOptionsPanelHolder") ||
                    it.startsWith("com.bilibili.playerbizcommon.widget.function.setting")
        }.forEach { c ->
            c.findClass(mClassLoader).declaredFields.forEach { f ->
                if (Modifier.isStatic(f.modifiers) && f.type == FloatArray::class.java)
                    return arrayOf(c, f.name)
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
            c.findClass(mClassLoader).declaredFields.forEach { f ->
                if (Modifier.isStatic(f.modifiers) && f.type == gsonClass)
                    return arrayOf(c, f.name)
            }
        }
        return arrayOfNulls(2)
    }

    private fun findBangumiParamsMap(): Array<String?> {
        val bangumiDetailApiServiceClass = classesList.filter {
            it.startsWith("com.bilibili.bangumi.data.page.detail")
        }.map { c ->
            c.findClass(mClassLoader)
        }.lastOrNull { c ->
            c.declaredMethods.map { it.name }.contains("getViewSeasonV2")
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
            it.startsWith("tv.danmaku.bili")
        }.filter {
            it.matches(reg)
        }.flatMap { c ->
            c.findClass(mClassLoader).declaredFields.filter {
                it.modifiers == mask
                        && it.type == Int::class.javaPrimitiveType
            }
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

    private fun findDrawerMethod(): Array<String?> = try {
        arrayOf(
            drawerLayoutClass?.getMethod(
                "openDrawer",
                View::class.java,
                Boolean::class.javaPrimitiveType
            )?.name,
            drawerLayoutClass?.getMethod(
                "closeDrawer",
                View::class.java,
                Boolean::class.javaPrimitiveType
            )?.name
        )
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

    private fun findAbsMusicService() = classesList.filter {
        it.startsWith("tv.danmaku.bili.ui.player.notification")
    }.firstOrNull { c ->
        c.findClass(mClassLoader).superclass == Service::class.java
    }

    private fun findSetNotificationMethods(): Array<String?> =
        musicNotificationHelperClass?.declaredMethods?.lastOrNull {
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
        c.findClass(mClassLoader).declaredFields.filter {
            it.type == PendingIntent::class.java
        }.count().let { it > 0 }
    }

    private fun findLiveNotificationHelper() = classesList.filter {
        it.startsWith("com.bilibili.bililive.room.ui.liveplayer.background")
    }.firstOrNull { c ->
        c.findClass(mClassLoader).declaredFields.filter {
            it.type == PendingIntent::class.java
        }.count().let { it > 0 }
    }

    private fun findGarbHelper(): Array<String?> {
        val garbClass = "com.bilibili.lib.ui.garb.Garb".findClassOrNull(mClassLoader)
            ?: return arrayOfNulls(2)
        classesList.filter {
            it.startsWith("com.bilibili.lib.ui.garb")
        }.forEach { c ->
            c.findClass(mClassLoader).declaredMethods.forEach { m ->
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
            c.findClass(mClassLoader).declaredMethods.forEach { m ->
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
            it.startsWith("com.bilibili.lib.sharewrapper")
        }.filter {
            it.matches(reg)
        }.firstOrNull { c ->
            c.findClass(mClassLoader).declaredMethods.filter {
                it.parameterTypes.size == 2 && it.parameterTypes[0] == String::class.java && it.parameterTypes[1] == Bundle::class.java
            }.count().let { it > 0 }
        }
    }

    private fun writeHookInfo(context: Context) {
        try {
            val hookInfoFile = File(context.cacheDir, Constant.HOOK_INFO_FILE_NAME)
            val lastUpdateTime = context.packageManager.getPackageInfo(
                AndroidAppHelper.currentPackageName(),
                0
            ).lastUpdateTime
            val lastModuleUpdateTime = try {
                context.packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, 0)
            } catch (e: Throwable) {
                null
            }?.lastUpdateTime ?: 0
            if (hookInfoFile.exists()) hookInfoFile.delete()
            ObjectOutputStream(FileOutputStream(hookInfoFile)).use { stream ->
                stream.writeLong(max(lastModuleUpdateTime, lastUpdateTime))
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
    }?.firstOrNull()?.name ?: "retrofit2.HttpException".findClassOrNull(mClassLoader)
        ?.findFieldOrNull("response")?.type?.name

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

    private fun findFastJsonClass(): Class<*>? =
        "com.alibaba.fastjson.JSON".findClassOrNull(mClassLoader)
            ?: "com.alibaba.fastjson.a".findClassOrNull(mClassLoader)

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
        val biliSkinListClass =
            "tv.danmaku.bili.ui.theme.api.BiliSkinList".findClassOrNull(mClassLoader)
                ?: return null
        return "tv.danmaku.bili.ui.theme.ThemeStoreActivity".findClassOrNull(mClassLoader)?.declaredMethods?.firstOrNull {
            it.parameterTypes.size == 2 && it.parameterTypes[0] == biliSkinListClass &&
                    it.parameterTypes[1] == Boolean::class.javaPrimitiveType
        }?.name
    }

    private fun findThemeListClickClass() =
        "tv.danmaku.bili.ui.theme.ThemeStoreActivity".findClassOrNull(mClassLoader)?.declaredClasses?.firstOrNull {
            it.interfaces.contains(View.OnClickListener::class.java)
        }?.name

    private fun findThemeNameClass() = classesList.filter {
        it.startsWith("tv.danmaku.bili.ui.garb")
    }.firstOrNull { c ->
        c.findClass(mClassLoader).declaredFields.filter {
            Modifier.isStatic(it.modifiers) && it.type == Map::class.java
        }.count() == 1
    }

    private fun findThemeNameField() = themeNameClass?.declaredFields?.firstOrNull {
        it.type == Map::class.java
                && Modifier.isStatic(it.modifiers)
    }?.name

    private fun findSignQueryMethod(): String? {
        val signedQueryClass =
            "com.bilibili.nativelibrary.SignedQuery".findClassOrNull(mClassLoader)
                ?: return null
        return libBiliClass?.declaredMethods?.firstOrNull {
            it.parameterTypes.size == 1 && it.parameterTypes[0] == Map::class.java &&
                    it.returnType == signedQueryClass
        }?.name
    }

    private fun findThemeHelper() = classesList.filter {
        it.startsWith("tv.danmaku.bili.ui.theme")
    }.firstOrNull { c ->
        c.findClass(mClassLoader).declaredFields.filter {
            Modifier.isStatic(it.modifiers)
        }.filter {
            it.type == SparseArray::class.java
        }.count().let { it > 1 }
    }

    private fun findThemeIdHelper() = classesList.filter {
        it.startsWith("tv.danmaku.bili.ui.theme")
    }.firstOrNull { c ->
        c.findClass(mClassLoader).declaredFields.filter {
            Modifier.isStatic(it.modifiers)
        }.filter {
            it.type == SparseArray::class.java
        }.count().let { it == 1 }
    }

    private fun findColumnHelper() = classesList.filter {
        it.startsWith("com.bilibili.column.helper")
    }.firstOrNull { c ->
        c.findClass(mClassLoader).declaredFields.filter {
            Modifier.isStatic(it.modifiers)
        }.filter {
            it.type == SparseArray::class.java
        }.count().let { it > 1 }
    }

    private fun findThemeProcessor(): String? {
        val biliSkinListClass =
            "tv.danmaku.bili.ui.theme.api.BiliSkinList".findClassOrNull(mClassLoader)
        return classesList.filter {
            it.startsWith("tv.danmaku.bili.ui.theme")
        }.firstOrNull { c ->
            c.findClass(mClassLoader).declaredFields.filter {
                it.type == biliSkinListClass
            }.count().let { it > 1 }
        }
    }

    private fun findThemeResetMethods() = themeProcessorClass?.declaredMethods?.filter {
        it.parameterTypes.isEmpty() && it.modifiers == 0
    }?.joinToString(";") { it.name }

    private fun findAddSettingMethod() = homeUserCenterClass?.declaredMethods?.firstOrNull {
        it.parameterTypes.size >= 2 && it.parameterTypes[0] == Context::class.java && it.parameterTypes[1] == List::class.java
    }?.name

    private fun findSettingRouterClass() = classesList.filter {
        it.startsWith("tv.danmaku.bili.ui.main2")
    }.firstOrNull { c ->
        c.findClass(mClassLoader).declaredFields.filter {
            it.type == menuGroupItemClass && Modifier.isPublic(it.modifiers)
        }.count() > 0
    }

    private fun findPartySectionClass() = classesList.filter {
        it.startsWith("tv.danmaku.bili.ui.video.party.section") ||
                it.startsWith("com.bilibili.video.videodetail.party.section") ||
                it.startsWith("tv.danmaku.bili.ui.video.profile.action")
    }.firstOrNull { c ->
        c.findClass(mClassLoader).declaredFields.filter {
            it.type == progressBarClass
        }.count().let { it > 0 }
    }

    private fun findSectionClass() = classesList.filter {
        it.startsWith("tv.danmaku.bili.ui.video.section")
    }.firstOrNull { c ->
        c.findClass(mClassLoader).declaredFields.filter {
            it.type == progressBarClass
        }.count().let { it > 0 }
    }

    private fun findLikeMethod() = sectionClass?.declaredMethods?.firstOrNull {
        it.parameterTypes.size == 1 && it.parameterTypes[0] == Object::class.java
    }?.name

    private fun findPartyLikeMethod() = partySectionClass?.declaredMethods?.firstOrNull {
        it.parameterTypes.size == 1 && it.parameterTypes[0] == Object::class.java
    }?.name

    private fun findDrawerClass(): String? {
        val navigationViewClass =
            "android.support.design.widget.NavigationView".findClassOrNull(mClassLoader)
                ?: return null
        val regex = Regex("^tv\\.danmaku\\.bili\\.ui\\.main2\\.[^.]*$")
        return classesList.filter {
            it.startsWith("tv.danmaku.bili.ui.main2")
        }.filter {
            it.matches(regex)
        }.firstOrNull { c ->
            c.findClass(mClassLoader).declaredFields.filter {
                it.type == navigationViewClass
            }.count() > 0
        }
    }

    private fun findDownloadThreadListener() = classesList.filter {
        it.startsWith("tv.danmaku.bili.ui.offline")
    }.firstOrNull { c ->
        c.findClass(mClassLoader).run {
            declaredMethods.filter { m ->
                m.name == "onClick"
            }.count() > 0 && declaredFields.filter {
                it.type == TextView::class.java || it.type == downloadingActivityClass
            }.count() > 1
        }
    }

    private fun findVideoSubtitleClass(): String? {
        val regex = "^com\\.bapis\\.bilibili\\.community\\.service\\.\\w+\\.\\w+\\.VideoSubtitle$".toRegex()
        return classesList.filter {
            it.matches(regex)
        }.getOrNull(0)
    }

    private fun findSubtitleItemClass(): String? {
        val regex = "^com\\.bapis\\.bilibili\\.community\\.service\\.\\w+\\.\\w+\\.SubtitleItem$".toRegex()
        return classesList.filter {
            it.matches(regex)
        }.getOrNull(0)
    }

    private fun findDownloadThreadField() = downloadingActivityClass?.declaredFields?.firstOrNull {
        it.type == Int::class.javaPrimitiveType
    }?.name


    companion object {
        @Volatile
        lateinit var instance: BiliBiliPackage
    }
}
