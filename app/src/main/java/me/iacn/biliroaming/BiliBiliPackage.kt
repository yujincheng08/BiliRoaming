@file:Suppress("DEPRECATION")

package me.iacn.biliroaming

import android.app.AndroidAppHelper
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.os.Bundle
import android.text.style.ClickableSpan
import android.text.style.LineBackgroundSpan
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dalvik.system.BaseDexClassLoader
import me.iacn.biliroaming.utils.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.net.URL
import java.nio.channels.ByteChannel
import kotlin.math.max
import kotlin.system.measureTimeMillis
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue


infix fun Configs.Class.from(cl: ClassLoader) = if (hasName()) name.findClassOrNull(cl) else null
val Configs.Method.orNull get() = if (hasName()) name else null
val Configs.Field.orNull get() = if (hasName()) name else null

class BiliBiliPackage constructor(private val mClassLoader: ClassLoader, mContext: Context) {
    init {
        instance = this
    }

    @OptIn(ExperimentalTime::class)
    private val mHookInfo: Configs.HookInfo = run {
        val (result, time) = measureTimedValue { readHookInfo(mContext) }
        Log.d("load hookinfo $time")
        Log.d(result.copy { clearMapIds() })
        result
    }
    val bangumiApiResponseClass by Weak { mHookInfo.bangumiApiResponse from mClassLoader }
    val rxGeneralResponseClass by Weak { "com.bilibili.okretro.call.rxjava.RxGeneralResponse" from mClassLoader }
    val fastJsonClass by Weak { mHookInfo.fastJson.class_ from mClassLoader }
    val bangumiUniformSeasonClass by Weak { mHookInfo.bangumiSeason from mClassLoader }
    val sectionClass by Weak { mHookInfo.section.class_ from mClassLoader }
    val partySectionClass by Weak { mHookInfo.partySection.class_ from mClassLoader }
    val retrofitResponseClass by Weak { mHookInfo.retrofitResponse from mClassLoader }
    val themeHelperClass by Weak { mHookInfo.themeHelper.class_ from mClassLoader }
    val themeIdHelperClass by Weak { mHookInfo.themeIdHelper.class_ from mClassLoader }
    val columnHelperClass by Weak { mHookInfo.columnHelper.class_ from mClassLoader }
    val settingRouterClass by Weak { mHookInfo.settings.settingRouter from mClassLoader }
    val themeListClickClass by Weak { mHookInfo.themeListClick from mClassLoader }
    val shareWrapperClass by Weak { mHookInfo.shareWrapper.class_ from mClassLoader }
    val themeNameClass by Weak { mHookInfo.themeName.class_ from mClassLoader }
    val themeProcessorClass by Weak { mHookInfo.themeProcessor.class_ from mClassLoader }
    val drawerClass by Weak { mHookInfo.drawer.class_ from mClassLoader }
    val generalResponseClass by Weak { mHookInfo.generalResponse from mClassLoader }
    val seasonParamsMapClass by Weak { "com.bilibili.bangumi.data.page.detail.BangumiDetailApiService\$UniformSeasonParamsMap" from mClassLoader }
    val seasonParamsClass by Weak { mHookInfo.bangumiParams.class_ from mClassLoader }
    val brandSplashClass by Weak { "tv.danmaku.bili.ui.splash.brand.ui.BaseBrandSplashFragment" from mClassLoader }
    val urlConnectionClass by Weak { "com.bilibili.lib.okhttp.huc.OkHttpURLConnection" from mClassLoader }
    val downloadThreadListenerClass by Weak { mHookInfo.downloadThread.listener from mClassLoader }
    val downloadingActivityClass by Weak { mHookInfo.downloadThread.downloadActivity from mClassLoader }
    val reportDownloadThreadClass by Weak { mHookInfo.downloadThread.reportDownload.class_ from mClassLoader }
    val libBiliClass by Weak { mHookInfo.signQuery.class_ from mClassLoader }
    val splashActivityClass by Weak {
        "tv.danmaku.bili.ui.splash.SplashActivity" from mClassLoader
            ?: "tv.danmaku.bili.MainActivityV2" from mClassLoader
    }
    val mainActivityClass by Weak { "tv.danmaku.bili.MainActivityV2" from mClassLoader }
    val homeUserCenterClass by Weak { mHookInfo.settings.homeUserCenter from mClassLoader }
    val musicNotificationHelperClass by Weak { mHookInfo.musicNotification.helper from mClassLoader }
    val liveNotificationHelperClass by Weak { mHookInfo.liveNotificationHelper from mClassLoader }
    val notificationBuilderClass by Weak { mHookInfo.musicNotification.builder.class_ from mClassLoader }
    val absMusicServiceClass by Weak { mHookInfo.musicNotification.absMusicService from mClassLoader }
    val menuGroupItemClass by Weak { mHookInfo.settings.menuGroupItem from mClassLoader }
    val drawerLayoutClass by Weak { mHookInfo.drawer.layout from mClassLoader }
    val drawerLayoutParamsClass by Weak { mHookInfo.drawer.layoutParams from mClassLoader }
    val splashInfoClass by Weak {
        "tv.danmaku.bili.ui.splash.brand.BrandShowInfo" from mClassLoader
            ?: "tv.danmaku.bili.ui.splash.brand.model.BrandShowInfo" from mClassLoader
    }
    val commentCopyClass by Weak { mHookInfo.commentLongClick from mClassLoader }
    val kotlinJsonClass by Weak { "kotlinx.serialization.json.Json" from mClassLoader }
    val gsonConverterClass by Weak { mHookInfo.gsonHelper.gsonConverter from mClassLoader }
    val playerCoreServiceV2Class by Weak { mHookInfo.playerCoreService.class_ from mClassLoader }
    val teenagersModeDialogActivityClass by Weak {
        "com.bilibili.teenagersmode.ui.TeenagersModeDialogActivity" from mClassLoader
    }
    val pegasusFeedClass by Weak { mHookInfo.pegasusFeed.class_ from mClassLoader }
    val okhttpResponseClass by Weak { mHookInfo.okhttpResponse from mClassLoader }
    val subtitleSpanClass by Weak { mHookInfo.subtitleSpan from mClassLoader }
    val chronosSwitchClass by Weak { mHookInfo.chronosSwitch from mClassLoader }
    val biliSpaceClass by Weak { "com.bilibili.app.authorspace.api.BiliSpace" from mClassLoader }
    val biliVideoDetailClass by Weak {
        "tv.danmaku.bili.ui.video.api.BiliVideoDetail" from mClassLoader
            ?: "tv.danmaku.bili.videopage.data.view.model.BiliVideoDetail" from mClassLoader
    }
    val commentSpanTextViewClass by Weak { mHookInfo.commentSpan from mClassLoader }
    val commentSpanEllipsisTextViewClass by Weak { "com.bilibili.app.comm.comment2.widget.CommentSpanEllipsisTextView" from mClassLoader }
    val liveRoomActivityClass by Weak { "com.bilibili.bililive.room.ui.roomv3.LiveRoomActivityV3" from mClassLoader }
    val liveKvConfigHelperClass by Weak { "com.bilibili.bililive.tec.kvcore.LiveKvConfigHelper" from mClassLoader }
    val storyVideoActivityClass by Weak { "com.bilibili.video.story.StoryVideoActivity" from mClassLoader }
    val okioWrapperClass by Weak { mHookInfo.okio.class_ from mClassLoader }
    val progressBarClass by Weak { mHookInfo.progressBar from mClassLoader }
    val videoUpperAdClass by Weak { mHookInfo.videoUpperAd.class_ from mClassLoader }
    val ellipsizingTextViewClass by Weak { "com.bilibili.bplus.followingcard.widget.EllipsizingTextView" from mClassLoader }
    val dynamicDescHolderListenerClass by Weak { mHookInfo.dynDescHolderListener from mClassLoader }
    val shareClickResultClass by Weak { "com.bilibili.lib.sharewrapper.online.api.ShareClickResult" from mClassLoader }
    val backgroundPlayerClass by Weak { mHookInfo.musicNotification.backgroundPlayer from mClassLoader }
    val playerServiceClass by Weak { mHookInfo.musicNotification.playerService from mClassLoader }
    val mediaSessionCallbackClass by Weak { mHookInfo.musicNotification.mediaSessionCallback from mClassLoader }
    val playerOnSeekCompleteClass by Weak { mHookInfo.playerCoreService.seekCompleteListener from mClassLoader }
    val kanbanCallbackClass by Weak { mHookInfo.kanBan.class_ from mClassLoader }
    val toastHelperClass by Weak { mHookInfo.toastHelper.class_ from mClassLoader }
    val videoDetailCallbackClass by Weak { mHookInfo.videoDetailCallback from mClassLoader }

    private val accessKeyInstance by lazy {
        ("com.bilibili.cheese.ui.detail.pay.v3.CheesePayHelperV3\$accessKey\$2".findClassOrNull(
            mClassLoader
        ) ?: "com.bilibili.cheese.ui.detail.pay.v2.CheesePayHelperV2\$accessKey\$2".findClassOrNull(
            mClassLoader
        )
        ?: "com.bilibili.bangumi.ui.page.detail.pay.BangumiPayHelperV2\$accessKey\$2".findClassOrNull(
            mClassLoader
        ))?.getStaticObjectField("INSTANCE")
    }

    val ids: Map<String, Int> by lazy {
        mHookInfo.mapIds.idsMap
    }

    fun getCustomizeAccessKey(area: String): String? {
        var key = sPrefs.getString("${area}_accessKey", null)
        if (key.isNullOrBlank()) key = accessKey
        return key
    }

    val accessKey
        get() = accessKeyInstance?.callMethodAs<String>("invoke")

    fun fastJsonParse() = mHookInfo.fastJson.parse.orNull

    fun colorArray() = mHookInfo.themeHelper.colorArray.orNull

    fun colorId() = mHookInfo.themeIdHelper.colorId.orNull

    fun columnColorArray() = mHookInfo.columnHelper.colorArray.orNull

    fun signQueryName() = mHookInfo.signQuery.method.orNull

    fun skinList() = mHookInfo.skinList.orNull

    fun themeReset() = mHookInfo.themeProcessor.methodsList.map { it.orNull }

    fun addSetting() = mHookInfo.settings.addSetting.orNull

    fun requestField() = mHookInfo.okHttp.request.orNull

    fun likeMethod() = mHookInfo.section.method.orNull

    fun partyLikeMethod() = mHookInfo.partySection.method.orNull

    fun themeName() = mHookInfo.themeName.field.orNull

    fun shareWrapper() = mHookInfo.shareWrapper.method.orNull

    fun downloadingThread() = mHookInfo.downloadThread.field.orNull

    fun reportDownloadThread() = mHookInfo.downloadThread.reportDownload.method.orNull

    fun setNotification() = mHookInfo.musicNotification.builder.method.orNull

    fun openDrawer() = mHookInfo.drawer.open.orNull

    fun closeDrawer() = mHookInfo.drawer.open.orNull

    fun isDrawerOpen() = mHookInfo.drawer.isOpen.orNull

    fun paramsToMap() = mHookInfo.bangumiParams.paramsToMap.orNull

    fun gson() = mHookInfo.gsonHelper.gson.orNull

    fun defaultSpeed() = mHookInfo.playerCoreService.getDefaultSpeed.orNull

    fun urlField() = mHookInfo.okHttp.url.orNull

    fun gsonToJson() = mHookInfo.gsonHelper.toJson.orNull

    fun gsonFromJson() = mHookInfo.gsonHelper.fromJson.orNull

    fun pegasusFeed() = mHookInfo.pegasusFeed.method.orNull

    fun descCopy() = mHookInfo.descCopy.methodsList.map { it.orNull }

    fun descCopyView() = mHookInfo.descCopy.classesList.map { it from mClassLoader }

    fun responseDataField() = runCatchingOrNull {
        rxGeneralResponseClass?.getDeclaredField("data")?.name
    } ?: "_data"

    fun okio() = mHookInfo.okio.field.orNull

    fun okioLength() = mHookInfo.okio.length.orNull

    fun okioInputStream() = mHookInfo.okioBuffer.inputStream.orNull

    fun okioReadFrom() = mHookInfo.okioBuffer.readFrom.orNull

    fun videoUpperAd() = mHookInfo.videoUpperAd.method.orNull

    fun seekTo() = mHookInfo.playerCoreService.seekTo.orNull

    fun onSeekComplete() = mHookInfo.playerCoreService.onSeekComplete.orNull

    fun setState() = mHookInfo.musicNotification.setState.orNull

    fun kanbanCallback() = mHookInfo.kanBan.method.orNull

    fun showToast() = mHookInfo.toastHelper.show.orNull

    fun cancelShowToast() = mHookInfo.toastHelper.cancel.orNull

    private fun readHookInfo(context: Context): Configs.HookInfo {
        try {
            val hookInfoFile = File(context.cacheDir, Constant.HOOK_INFO_FILE_NAME)
            Log.d("Reading hook info: $hookInfoFile")
            val t = measureTimeMillis {
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
                    val info = FileInputStream(hookInfoFile).use {
                        runCatchingOrNull { Configs.HookInfo.parseFrom(it) }
                            ?: Configs.HookInfo.newBuilder().build()
                    }
                    if (info.lastUpdateTime >= lastUpdateTime && info.lastUpdateTime >= lastModuleUpdateTime)
                        return info
                }
            }
            Log.d("Read hook info completed: take $t ms")
        } catch (e: Throwable) {
            Log.w(e)
        }
        return initHookInfo(context).also {
            try {
                val hookInfoFile = File(context.cacheDir, Constant.HOOK_INFO_FILE_NAME)
                if (hookInfoFile.exists()) hookInfoFile.delete()
                FileOutputStream(hookInfoFile).use { o -> it.writeTo(o) }
            } catch (e: Exception) {
                Log.e(e)
            }
        }
    }

    companion object {
        @JvmStatic
        fun findRealClassloader(classloader: BaseDexClassLoader): BaseDexClassLoader {
            val serviceField = classloader.javaClass.findFirstFieldByExactTypeOrNull(
                "com.bilibili.lib.tribe.core.internal.loader.DefaultClassLoaderService" from classloader
            )
            val delegateField = classloader.javaClass.findFirstFieldByExactTypeOrNull(
                "com.bilibili.lib.tribe.core.internal.loader.TribeLoaderDelegate" from classloader
            )
            return if (serviceField != null) {
                serviceField.type.declaredFields.filter { f ->
                    f.type == ClassLoader::class.java
                }.map { f ->
                    classloader.getObjectFieldOrNull(serviceField.name)
                        ?.getObjectFieldOrNull(f.name)
                }.firstOrNull { o ->
                    o?.javaClass?.name?.startsWith("com.bilibili") == false
                } as? BaseDexClassLoader ?: classloader
            } else if (delegateField != null) {
                val loaderField =
                    delegateField.type.findFirstFieldByExactTypeOrNull(ClassLoader::class.java)
                val out = classloader.getObjectFieldOrNull(delegateField.name)
                    ?.getObjectFieldOrNull(loaderField?.name)
                if (BaseDexClassLoader::class.java.isInstance(out)) out as BaseDexClassLoader else classloader
            } else classloader
        }

        @JvmStatic
        fun initHookInfo(context: Context) = hookInfo {
            val classloader = context.classLoader
            val classesList = context.classLoader.allClassesList(::findRealClassloader).asSequence()

            try {
                System.loadLibrary("biliroaming")
            } catch (e: Throwable) {
                Log.e(e)
                Log.toast("不支持该架构或框架，部分功能可能失效")
                return@hookInfo
            }

            val dexHelper = DexHelper(classloader.findDexClassLoader(::findRealClassloader))
            lastUpdateTime = max(
                context.packageManager.getPackageInfo(
                    AndroidAppHelper.currentPackageName(),
                    0
                ).lastUpdateTime,
                runCatchingOrNull {
                    context.packageManager.getPackageInfo(
                        BuildConfig.APPLICATION_ID,
                        0
                    )
                }?.lastUpdateTime ?: 0
            )
            mapIds = mapIds {
                val reg = Regex("^tv\\.danmaku\\.bili\\.[^.]*$")
                val mask = Modifier.STATIC or Modifier.PUBLIC or Modifier.FINAL
                classesList.filter {
                    it.startsWith("tv.danmaku.bili")
                }.filter {
                    it.matches(reg)
                }.flatMap { c ->
                    c.findClass(classloader).declaredFields.filter {
                        it.modifiers == mask
                                && it.type == Int::class.javaPrimitiveType
                    }
                }.forEach { ids[it.name] = it.get(null) as Int }
            }

            bangumiApiResponse = class_ {
                name = "com.bilibili.bangumi.data.common.api.BangumiApiResponse"
            }
            retrofitResponse = class_ {
                name = this@hookInfo.bangumiApiResponse.from(classloader)?.methods?.filter {
                    "extractResult" == it.name
                }?.map {
                    it.parameterTypes[0]
                }?.firstOrNull()?.name ?: "retrofit2.HttpException".findClassOrNull(classloader)
                    ?.findFieldOrNull("response")?.type?.name ?: return@class_
            }
            okHttp = okHttp {
                val hostRequestInterceptorClass =
                    "com.bililive.bililive.infra.hybrid.interceptor.HostRequestInterceptor".findClassOrNull(
                        classloader
                    ) ?: "com.bililive.bililive.liveweb.interceptor.a".findClassOrNull(classloader)
                val okHttpRequestClass = hostRequestInterceptorClass?.declaredMethods?.firstOrNull {
                    it.parameterTypes.size == 1 && it.parameterTypes[0] == it.returnType
                }?.returnType
                this@hookInfo.retrofitResponse.from(classloader)?.declaredConstructors?.forEach { c ->
                    c.parameterTypes[0].declaredFields.forEach { f1 ->
                        if (f1.type == okHttpRequestClass) {
                            okHttpRequestClass?.declaredFields?.forEach { f2 ->
                                f2.type.declaredMethods.forEach { m ->
                                    if (m.parameterTypes.isEmpty() && m.returnType == URL::class.java) {
                                        request = field { name = f1.name }
                                        url = field { name = f2.name }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            fastJson = fastJson {
                val fastJsonClass = "com.alibaba.fastjson.JSON" from classloader
                    ?: "com.alibaba.fastjson.a" from classloader
                val notObfuscated = "JSON" == fastJsonClass?.simpleName
                class_ = class_ { name = fastJsonClass?.name ?: return@class_ }
                parse = method { name = if (notObfuscated) "parseObject" else "a" }
            }
            themeHelper = themeHelper {
                val themeHelperClass = classesList.filter {
                    it.startsWith("tv.danmaku.bili.ui.theme")
                }.map { it on classloader }.firstOrNull { c ->
                    c.declaredFields.filter {
                        Modifier.isStatic(it.modifiers)
                    }.count {
                        it.type == SparseArray::class.java
                    } > 1
                } ?: return@themeHelper

                class_ = class_ { name = themeHelperClass.name }
                colorArray = field {
                    name = themeHelperClass.declaredFields.firstOrNull {
                        it.type == SparseArray::class.java &&
                                (it.genericType as ParameterizedType).actualTypeArguments[0].toString() == "int[]"
                    }?.name ?: return@field
                }
            }
            themeIdHelper = themeIdHelper {
                val themeIdHelperClass = classesList.filter {
                    it.startsWith("tv.danmaku.bili.ui.theme")
                }.firstNotNullOfOrNull { c ->
                    c.findClass(classloader).takeIf {
                        it.declaredFields.count { f ->
                            Modifier.isStatic(f.modifiers) && f.type == SparseArray::class.java
                        } == 1
                    }
                } ?: return@themeIdHelper
                class_ = class_ { name = themeIdHelperClass.name }
                colorId = field {
                    name =
                        themeIdHelperClass.declaredFields.firstOrNull {
                            it.type == SparseArray::class.java
                        }?.name ?: return@field
                }
            }
            columnHelper = columnHelper {
                val columnHelperClass = classesList.filter {
                    it.startsWith("com.bilibili.column.helper")
                }.firstNotNullOfOrNull { c ->
                    c.findClass(classloader).takeIf {
                        it.declaredFields.count { f ->
                            Modifier.isStatic(it.modifiers) && f.type == SparseArray::class.java
                        } > 1
                    }
                } ?: return@columnHelper
                class_ = class_ {
                    name = columnHelperClass.name ?: return@class_
                }
                colorArray = field {
                    name = columnHelperClass.declaredFields.firstOrNull {
                        it.type == SparseArray::class.java &&
                                (it.genericType as ParameterizedType).actualTypeArguments[0].toString() == "int[]"
                    }?.name ?: return@field
                }
            }
            skinList = method {
                val biliSkinListClass =
                    "tv.danmaku.bili.ui.theme.api.BiliSkinList".findClassOrNull(classloader)
                        ?: return@method
                name =
                    "tv.danmaku.bili.ui.theme.ThemeStoreActivity".findClassOrNull(classloader)?.declaredMethods?.firstOrNull {
                        it.parameterTypes.size == 2 && it.parameterTypes[0] == biliSkinListClass &&
                                it.parameterTypes[1] == Boolean::class.javaPrimitiveType
                    }?.name ?: return@method
            }
            themeProcessor = themeProcessor {
                val biliSkinListClass =
                    "tv.danmaku.bili.ui.theme.api.BiliSkinList".findClassOrNull(classloader)
                val themeProcessorClass = classesList.filter {
                    it.startsWith("tv.danmaku.bili.ui.theme")
                }.firstNotNullOfOrNull { c ->
                    c.findClass(classloader).takeIf {
                        it.declaredFields.count { f ->
                            f.type == biliSkinListClass
                        } > 1
                    }
                } ?: return@themeProcessor
                class_ = class_ { name = themeProcessorClass.name }
                methods += themeProcessorClass.declaredMethods.filter {
                    it.parameterTypes.isEmpty() && it.modifiers == 0
                }.map { method { name = it.name } }
            }
            themeListClick = class_ {
                name =
                    "tv.danmaku.bili.ui.theme.ThemeStoreActivity".findClassOrNull(classloader)?.declaredClasses?.firstOrNull {
                        it.interfaces.contains(View.OnClickListener::class.java)
                    }?.name ?: return@class_
            }
            shareWrapper = shareWrapper {
                class_ = class_ {
                    val reg = Regex("^com\\.bilibili\\.lib\\.sharewrapper\\.[^.]*$")
                    name = classesList.filter {
                        it.startsWith("com.bilibili.lib.sharewrapper")
                    }.filter {
                        it.matches(reg)
                    }.firstOrNull { c ->
                        c.findClass(classloader).declaredMethods.any {
                            it.parameterTypes.size == 2 && it.parameterTypes[0] == String::class.java && it.parameterTypes[1] == Bundle::class.java
                        }
                    } ?: return@class_
                }
                method = method {
                    name =
                        this@shareWrapper.class_.from(classloader)?.declaredMethods?.firstOrNull {
                            it.parameterTypes.size == 2 && it.parameterTypes[0] == String::class.java && it.parameterTypes[1] == Bundle::class.java
                        }?.name ?: return@method
                }
            }
            themeName = themeName {
                val themeNameClass = classesList.filter {
                    it.startsWith("tv.danmaku.bili.ui.garb")
                }.map {
                    it.findClass(classloader)
                }.firstOrNull { c ->
                    c.declaredFields.count {
                        Modifier.isStatic(it.modifiers) && it.type == Map::class.java
                    } == 1
                }
                class_ = class_ {
                    name = themeNameClass?.name ?: return@class_
                }
                field = field {
                    name = themeNameClass?.declaredFields?.firstOrNull {
                        it.type == Map::class.java
                                && Modifier.isStatic(it.modifiers)
                    }?.name ?: return@field
                }
            }
            progressBar = class_ {
                name = ("tv.danmaku.biliplayer.view.RingProgressBar" from classloader
                    ?: "com.bilibili.playerbizcommon.view.RingProgressBar" from classloader)?.name
                    ?: return@class_
            }
            section = section {
                val sectionClass = classesList.filter {
                    it.startsWith("tv.danmaku.bili.ui.video.section")
                }.firstNotNullOfOrNull { c ->
                    c.findClass(classloader).takeIf {
                        it.declaredFields.any { f ->
                            f.type.name == this@hookInfo.progressBar.name
                        }
                    }
                } ?: return@section
                class_ = class_ { name = sectionClass.name }
                method = method {
                    name = sectionClass.declaredMethods.firstOrNull {
                        it.parameterTypes.size == 1 && it.parameterTypes[0] == Object::class.java
                    }?.name ?: return@method
                }
            }
            partySection = partySection {
                val partySectionClass = classesList.filter {
                    it.startsWith("tv.danmaku.bili.ui.video.party.section") ||
                            it.startsWith("com.bilibili.video.videodetail.party.section") ||
                            it.startsWith("tv.danmaku.bili.ui.video.profile.action") ||
                            it.startsWith("tv.danmaku.bili.ui.video.section.action") || it.startsWith(
                        "aj2"
                    )
                }.firstNotNullOfOrNull { c ->
                    c.findClass(classloader).takeIf {
                        it.declaredFields.any { f ->
                            f.type.name == this@hookInfo.progressBar.name
                        }
                    }
                } ?: return@partySection
                class_ = class_ { name = partySectionClass.name }
                method = method {
                    name = partySectionClass.declaredMethods.firstOrNull {
                        it.parameterTypes.size == 1 && it.returnType == Void::class.javaPrimitiveType && (
                                it.parameterTypes[0] == Object::class.java ||
                                        it.parameterTypes[0].name.startsWith("tv.danmaku.bili.videopage.foundation.section")
                                )
                    }?.name ?: return@method
                }
            }
            signQuery = signQuery {
                val signedQueryClass =
                    "com.bilibili.nativelibrary.SignedQuery".findClassOrNull(classloader)
                        ?: return@signQuery
                val libBiliClass =
                    "com.bilibili.nativelibrary.LibBili" from classloader ?: return@signQuery
                class_ = class_ { name = libBiliClass.name }
                method = method {
                    name = libBiliClass.declaredMethods.firstOrNull {
                        it.parameterTypes.size == 1 && it.parameterTypes[0] == Map::class.java &&
                                it.returnType == signedQueryClass
                    }?.name ?: return@method
                }
            }
            settings = settings {
                val menuGroupItemClass =
                    "com.bilibili.lib.homepage.mine.MenuGroup\$Item" from classloader
                        ?: return@settings
                val homeUserCenterClass =
                    "tv.danmaku.bilibilihd.ui.main.mine.HdHomeUserCenterFragment" from classloader
                        ?: "tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment" from classloader
                        ?: return@settings
                menuGroupItem = class_ { name = menuGroupItemClass.name }
                homeUserCenter = class_ { name = homeUserCenterClass.name }
                settingRouter = class_ {
                    name = classesList.filter {
                        it.startsWith("tv.danmaku.bili.ui.main2")
                    }.firstOrNull { c ->
                        c.findClass(classloader).declaredFields.any {
                            it.type == menuGroupItemClass && Modifier.isPublic(it.modifiers)
                        }
                    } ?: return@class_
                }
                addSetting = method {
                    name = homeUserCenterClass.declaredMethods.firstOrNull {
                        it.parameterTypes.size >= 2 && it.parameterTypes[0] == Context::class.java && it.parameterTypes[1] == List::class.java
                    }?.name ?: return@method
                }
            }
            drawer = drawer {
                val navigationViewClass =
                    "android.support.design.widget.NavigationView" from classloader
                val regex = Regex("^tv\\.danmaku\\.bili\\.ui\\.main2\\.[^.]*$")
                class_ = class_ {
                    name = classesList.filter {
                        it.startsWith("tv.danmaku.bili.ui.main2")
                    }.filter { it.matches(regex) }.firstOrNull { c ->
                        c.findClass(classloader).declaredFields.any {
                            it.type == navigationViewClass
                        }
                    } ?: return@class_
                }
                val drawerLayoutClass = "androidx.drawerlayout.widget.DrawerLayout" from classloader
                    ?: "android.support.v4.widget.DrawerLayout" from classloader ?: return@drawer
                layout = class_ { name = drawerLayoutClass.name }
                layoutParams = class_ {
                    name = drawerLayoutClass.declaredClasses.firstOrNull {
                        it.superclass == ViewGroup.MarginLayoutParams::class.java
                    }?.name ?: return@class_
                }
                try {
                    open = method {
                        name = drawerLayoutClass.getMethod(
                            "openDrawer",
                            View::class.java,
                            Boolean::class.javaPrimitiveType
                        ).name ?: throw Throwable()
                    }
                    close = method {
                        drawerLayoutClass.getMethod(
                            "closeDrawer",
                            View::class.java,
                            Boolean::class.javaPrimitiveType
                        ).name ?: throw Throwable()
                    }
                } catch (e: Throwable) {
                    val a = drawerLayoutClass.declaredMethods.filter {
                        Modifier.isPublic(it.modifiers) &&
                                it.parameterTypes.size == 2 && it.parameterTypes[0] == View::class.java &&
                                it.parameterTypes[1] == Boolean::class.javaPrimitiveType
                    }.map { it.name }.toTypedArray()
                    if (a.size == 2) {
                        open = method { name = a[0] }
                        close = method { name = a[1] }
                    }
                }
                isOpen = method {
                    name = runCatchingOrNull {
                        drawerLayoutClass.getMethod("isDrawerOpen", View::class.java).name
                    } ?: drawerLayoutClass.declaredMethods.firstOrNull {
                        Modifier.isPublic(it.modifiers) &&
                                it.parameterTypes.size == 1 && it.parameterTypes[0] == View::class.java &&
                                it.returnType == Boolean::class.javaPrimitiveType
                    }?.name ?: return@method
                }
            }
            videoDetailCallback = class_ {
                val callback =
                    "com.bilibili.okretro.BiliApiDataCallback".findClassOrNull(classloader)
                name = classesList.filter {
                    it.startsWith("tv.danmaku.bili.ui.video.videodetail.function")
                }.map { c ->
                    c.findClass(classloader)
                }.filter {
                    it.superclass == callback
                }.firstOrNull {
                    it.declaredFields.size == 1
                }?.name ?: return@class_
            }
            downloadThread = downloadThread {
                val downloadingActivityClass =
                    "tv.danmaku.bili.ui.offline.DownloadingActivity" from classloader
                        ?: return@downloadThread
                downloadActivity = class_ { name = downloadingActivityClass.name }
                field = field {
                    name = downloadingActivityClass.declaredFields.firstOrNull {
                        it.type == Int::class.javaPrimitiveType
                    }?.name ?: return@field
                }
                listener = class_ {
                    name = classesList.filter {
                        it.startsWith("tv.danmaku.bili.ui.offline")
                    }.firstOrNull { c ->
                        c.findClass(classloader).run {
                            declaredMethods.any { m ->
                                m.name == "onClick"
                            } && declaredFields.count {
                                it.type == TextView::class.java || it.type == downloadingActivityClass
                            } > 1
                        }
                    } ?: return@class_
                }
                reportDownload = reportDownload {
                    classesList.filter {
                        it.startsWith("tv.danmaku.bili.ui.offline.api")
                    }.forEach { c ->
                        c.findClass(classloader).declaredMethods.forEach { m ->
                            if (m.parameterTypes.size == 2 && m.parameterTypes[0] == Context::class.java && m.parameterTypes[1] == Int::class.javaPrimitiveType) {
                                class_ = class_ { name = c }
                                method = method { name = m.name }
                            }
                        }
                    }
                }
            }
            grabHelper = grabHelper {
                val garbClass = "com.bilibili.lib.ui.garb.Garb".findClassOrNull(classloader)
                    ?: return@grabHelper
                classesList.filter {
                    it.startsWith("com.bilibili.lib.ui.garb")
                }.forEach { c ->
                    c.findClass(classloader).declaredMethods.forEach { m ->
                        if (Modifier.isStatic(m.modifiers) && m.returnType == garbClass) {
                            class_ = class_ { name = c }
                            method = method { name = m.name }
                        }
                    }
                }
            }
            musicNotification = musicNotification {
                val absMusicServiceClass = classesList.filter {
                    it.startsWith("tv.danmaku.bili.ui.player.notification")
                }.firstNotNullOfOrNull { c ->
                    c.findClass(classloader).takeIf {
                        it.superclass == Service::class.java
                    }
                } ?: return@musicNotification
                absMusicService = class_ { name = absMusicServiceClass.name }
                val prefix = absMusicServiceClass.declaredFields.firstOrNull {
                    it.type.name.count { c -> c == '.' } == 1
                }?.type?.name?.substringBefore(
                    '.',
                    ".."
                )
                val musicNotificationHelperClass = classesList.filter {
                    it.startsWith("tv.danmaku.bili.ui.player.notification") || it.startsWith(
                        prefix ?: ".."
                    )
                }.firstNotNullOfOrNull { c ->
                    c.findClass(classloader).takeIf {
                        it.declaredFields.any { f ->
                            f.type == PendingIntent::class.java
                        }
                    }
                } ?: return@musicNotification
                helper = class_ { name = musicNotificationHelperClass.name }
                builder = notificationBuilder {
                    musicNotificationHelperClass.declaredMethods.lastOrNull {
                        it.parameterTypes.size == 1 && it.parameterTypes[0].name.run {
                            startsWith("android.support.v4.app") ||
                                    startsWith("androidx.core.app") ||
                                    startsWith("androidx.core.app.NotificationCompat\$Builder")
                        }
                    }?.let {
                        class_ = class_ { name = it.parameterTypes[0].name }
                        method = method { name = it.name }
                    }
                }
                val backgroundService = absMusicServiceClass.declaredFields.filter {
                    Modifier.isProtected(it.modifiers)
                }.map { it.type }.firstOrNull {
                    it.isInterface && it.name.startsWith("tv.danmaku.bili.ui.player.notification")
                }
                val backgroundPlayerClass = classesList.filter {
                    it.startsWith("tv.danmaku.biliplayerv2.service.business.background")
                }.firstNotNullOfOrNull { c ->
                    c.findClass(classloader).takeIf {
                        it.interfaces.contains(backgroundService)
                    }
                } ?: return@musicNotification
                backgroundPlayer = class_ { name = backgroundPlayerClass.name }
                setState = method {
                    name = absMusicServiceClass.declaredMethods.firstOrNull {
                        it.parameterTypes.size == 1 &&
                                it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                                it.returnType == Void::class.javaPrimitiveType &&
                                (Modifier.isProtected(it.modifiers) || Modifier.isPrivate(it.modifiers))
                    }?.name ?: return@method
                }
                playerService = class_ {
                    name = backgroundPlayerClass.declaredFields.firstOrNull {
                        it.type.name.run {
                            startsWith("tv.danmaku.biliplayerv2") &&
                                    !startsWith("tv.danmaku.biliplayerv2.service")
                        }
                    }?.type?.name ?: return@class_
                }
                mediaSessionCallback = class_ {
                    name = classesList.filter {
                        it.startsWith("android.support.v4.media.session")
                    }.firstOrNull { c ->
                        c.findClass(classloader).declaredMethods.count {
                            it.name == "onSeekTo"
                        } > 0
                    } ?: return@class_
                }

            }
            liveNotificationHelper = class_ {
                val prefix =
                    "com.bilibili.bililive.room.ui.liveplayer.background.AbsLiveBackgroundPlayerService".findClassOrNull(
                        classloader
                    )?.declaredFields?.firstOrNull {
                        it.type.name.count { c -> c == '.' } == 1
                    }?.type?.name?.substringBefore(
                        '.',
                        ".."
                    )
                name = classesList.filter {
                    it.startsWith("com.bilibili.bililive.room.ui.liveplayer.background") || it.startsWith(
                        prefix ?: ".."
                    )
                }.firstOrNull { c ->
                    c.findClass(classloader).declaredFields.any {
                        it.type == PendingIntent::class.java
                    }
                } ?: return@class_
            }
            bangumiParams = bangumiParams {
                val bangumiParamsClass = dexHelper.findMethodUsingString(
                    "UniformSeasonParams(",
                    true,
                    -1,
                    0,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    true
                ).firstOrNull()?.let {
                    dexHelper.decodeMethodIndex(it)
                }?.declaringClass ?: return@bangumiParams
                class_ = class_ { name = bangumiParamsClass.name }
                paramsToMap = method {
                    name = bangumiParamsClass.declaredMethods.firstOrNull {
                        it.returnType == java.util.Map::class.java
                    }?.name ?: return@method
                }
            }
            gsonHelper = gsonHelper {
                val gsonClass = "com.google.gson.Gson".findClassOrNull(classloader)
                    ?: return@gsonHelper
                classesList.filter {
                    it.startsWith("com.bilibili.okretro.converter") || it.startsWith("com.bilibili.api.utils")
                }.forEach { c ->
                    c.findClass(classloader).declaredFields.forEach { f ->
                        if (Modifier.isStatic(f.modifiers) && f.type == gsonClass) {
                            gson = field { name = f.name }
                            gsonConverter = class_ { name = c }
                        }
                    }
                }
                toJson = method {
                    name = gsonClass.declaredMethods.firstOrNull { m ->
                        m.returnType == String::class.java && m.parameterTypes.size == 1 && m.parameterTypes[0] == Object::class.java
                    }?.name ?: return@method
                }
                fromJson = method {
                    name = gsonClass.declaredMethods.firstOrNull { m ->
                        m.returnType == Object::class.java && m.parameterTypes.size == 2 && m.parameterTypes[0] == String::class.java && m.parameterTypes[1] == Class::class.java
                    }?.name ?: return@method
                }
            }
            playerCoreService = playerCoreService {
                val playerCoreServiceV2Class =
                    "tv.danmaku.biliplayerv2.service.core.PlayerCoreServiceV2".findClassOrNull(
                        classloader
                    )
                        ?: "tv.danmaku.biliplayerimpl.core.PlayerCoreServiceV2".findClassOrNull(
                            classloader
                        )
                        ?: classesList.filter {
                            it.startsWith("tv.danmaku.biliplayerv2.service") ||
                                    it.startsWith("tv.danmaku.biliplayerimpl") || it.startsWith("dn2")
                        }.firstOrNull { c ->
                            c.findClass(classloader).declaredFields.any {
                                it.type.name == "tv.danmaku.ijk.media.player.IMediaPlayer\$OnErrorListener"
                            }
                        }?.findClassOrNull(classloader) ?: return@playerCoreService
                playerCoreServiceV2Class.declaredMethods.forEach { m ->
                    if (Modifier.isPublic(m.modifiers) && m.parameterTypes.size == 1 && m.parameterTypes[0] == Boolean::class.java && m.returnType == Float::class.javaPrimitiveType) {
                        class_ = class_ { name = playerCoreServiceV2Class.name }
                        getDefaultSpeed = method { name = m.name }
                    }
                }
                seekTo = method {
                    name = runCatchingOrNull {
                        playerCoreServiceV2Class.getDeclaredMethod(
                            "seekTo",
                            Int::class.javaPrimitiveType
                        ).name
                    } ?: "a"
                }
                onSeekComplete = method {
                    name = "onSeekComplete"
                }
                val prefix = playerCoreServiceV2Class.name.substringBeforeLast('.')
                seekCompleteListener = class_ {
                    name = classesList.filter {
                        name.startsWith(prefix)
                    }.firstOrNull { c ->
                        c.findClass(classloader).interfaces.map { it.name }
                            .contains("tv.danmaku.ijk.media.player.IMediaPlayer\$OnSeekCompleteListener")
                    } ?: return@class_
                }
            }
            generalResponse = class_ {
                name = "com.bilibili.okretro.GeneralResponse"
            }
            okhttpResponse = class_ {
                name = dexHelper.findMethodUsingString(
                    "Cannot buffer entire body for content length",
                    true,
                    -1,
                    0,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    true
                ).map {
                    dexHelper.decodeMethodIndex(it)
                }.firstOrNull()?.declaringClass?.name ?: return@class_
            }
            pegasusFeed = pegasusFeed {
                val itemClass =
                    "com.bilibili.pegasus.api.model.BasicIndexItem".findClassOrNull(classloader)
                classesList.filter {
                    it.startsWith("com.bilibili.pegasus.api")
                }.map { c ->
                    c.findClass(classloader)
                }.filter { c ->
                    c.declaredMethods.firstOrNull {
                        it.returnType == itemClass
                    } != null
                }.forEach { c ->
                    c.declaredMethods.forEach {
                        if (it.parameterTypes.size == 1 && it.returnType.name == this@hookInfo.generalResponse.name) {
                            class_ = class_ { name = c.name }
                            method = method { name = it.name }
                        }
                    }
                }
            }
            chronosSwitch = class_ {
                val regex = Regex("""^tv\.danmaku\.chronos\.wrapper\.[^.]*$""")
                name = classesList.filter {
                    it.matches(regex)
                }.firstOrNull { c ->
                    c.findClass(classloader).declaredFields.count {
                        it.type == Boolean::class.javaObjectType
                    } >= 5
                } ?: return@class_
            }
            subtitleSpan = class_ {
                name = classesList.filter {
                    it.startsWith("tv.danmaku.danmaku.subititle") ||
                            it.startsWith("tv.danmaku.danmaku.subtitle")
                }.firstOrNull { c ->
                    c.findClass(classloader).interfaces.contains(LineBackgroundSpan::class.java)
                } ?: return@class_
            }
            commentLongClick = class_ {
                name = classesList.filter {
                    it.startsWith("com.bilibili.app.comm.comment2")
                }.firstOrNull { c ->
                    c.findClass(classloader).run {
                        declaredMethods.count {
                            it.name == "onLongClick"
                        } == 1
                    }
                } ?: return@class_
            }
            okio = okIO {
                val responseClass = this@hookInfo.okhttpResponse.from(classloader) ?: return@okIO
                val wrapperClass = classesList.filter {
                    it.startsWith(responseClass.name)
                }.map { c ->
                    c.findClass(classloader)
                }.firstOrNull { c ->
                    c.superclass == responseClass
                } ?: return@okIO
                val okioField = wrapperClass.declaredFields.firstOrNull { f ->
                    f.type.name.startsWith("okio")
                } ?: return@okIO
                val okioLenField = wrapperClass.declaredFields.firstOrNull { f ->
                    f.type == Long::class.javaPrimitiveType
                } ?: return@okIO
                class_ = class_ { name = wrapperClass.name }
                field = field { name = okioField.name }
                length = field { name = okioLenField.name }
            }
            okioBuffer = okIOBuffer {
                val okioBufferClass = classesList.filter {
                    it.startsWith("okio")
                }.map { c ->
                    c.findClass(classloader)
                }.firstOrNull { c ->
                    c.interfaces.contains(ByteChannel::class.java)
                } ?: return@okIOBuffer
                val okioInputStreamMethod = okioBufferClass.declaredMethods.firstOrNull {
                    it.parameterTypes.isEmpty() && it.returnType == InputStream::class.java
                } ?: return@okIOBuffer
                val okioReadFromMethod = okioBufferClass.declaredMethods.firstOrNull {
                    it.parameterTypes.size == 1 && it.parameterTypes[0] == InputStream::class.java
                } ?: return@okIOBuffer
                class_ = class_ { name = okioBufferClass.name }
                inputStream = method { name = okioInputStreamMethod.name }
                readFrom = method { name = okioReadFromMethod.name }
            }
            videoUpperAd = videoUpperAdInfo {
                val adHolderClass =
                    "com.bilibili.ad.adview.videodetail.upper.VideoUpperAdSectionViewHolder".findClassOrNull(
                        classloader
                    )
                        ?: "com.bilibili.ad.adview.videodetail.upper.VideoUpperAdViewHolder".findClassOrNull(
                            classloader
                        ) ?: return@videoUpperAdInfo
                val reg = Regex("^com\\.bilibili\\.ad\\.adview\\.videodetail\\.upper\\.[^.]*$")
                classesList.filter {
                    it.startsWith("com.bilibili.ad.adview.videodetail.upper")
                }.filter { c ->
                    c.matches(reg)
                }.map { c ->
                    c.findClass(classloader)
                }.forEach { c ->
                    c.declaredMethods.forEach { m ->
                        if (Modifier.isPublic(m.modifiers) && m.parameterTypes.size >= 2 &&
                            m.parameterTypes[0] == ViewGroup::class.java &&
                            m.returnType == adHolderClass
                        ) {
                            class_ = class_ { name = c.name }
                            method = method { name = m.name }
                        }
                    }
                }
            }
            commentSpan = class_ {
                name =
                    "com.bilibili.app.comm.comment2.widget.CommentExpandableTextView".findClassOrNull(
                        classloader
                    )?.name ?: classesList.filter {
                        it.startsWith("com.bilibili.app.comm.comment2.widget")
                    }.firstOrNull { c ->
                        c.findClass(classloader).declaredFields.count {
                            it.type == Float::class.javaPrimitiveType
                        } > 1
                    } ?: return@class_
            }
            dynDescHolderListener = class_ {
                name = classesList.filter {
                    it.startsWith("com.bilibili.bplus.followinglist.module.item")
                }.firstOrNull { c ->
                    c.findClass(classloader).run {
                        declaredMethods.count {
                            it.name == "onLongClick"
                        } == 1
                    }
                } ?: return@class_
            }
            descCopy = descCopy {
                classesList.filter {
                    it.startsWith("tv.danmaku.bili.ui.video.profile.info.DescViewHolder") ||
                            it.startsWith("tv.danmaku.bili.ui.video.section.info.DescViewHolder")
                }.map { c ->
                    c.findClass(classloader)
                }.forEach { c ->
                    c.declaredMethods.firstOrNull { m ->
                        m.parameterTypes.size == 2 && m.parameterTypes[0] == View::class.java && m.parameterTypes[1] == ClickableSpan::class.java
                    }?.let {
                        classes + c.name
                        methods + it.name
                    }
                }
            }
            bangumiSeason = class_ {
                name = dexHelper.findMethodUsingString(
                    "BangumiAllButton",
                    true, -1, 0, null, -1, null, null, null, true
                ).firstOrNull()?.let {
                    dexHelper.decodeMethodIndex(it)
                }?.declaringClass?.declaringClass?.name ?: return@class_
            }
            kanBan = kanBan {
                val statusClass =
                    "tv.danmaku.bili.ui.kanban.KanBanUserStatus".findClassOrNull(classloader)
                        ?: return@kanBan
                statusClass.runCatchingOrNull {
                    getDeclaredMethod("isUseKanBan")
                }?.let {
                    dexHelper.encodeMethodIndex(it)
                }?.let {
                    dexHelper.findMethodInvoked(it, -1, 1, "VL", -1, null, null, null, true)
                        ?.firstOrNull()
                }?.let {
                    dexHelper.decodeMethodIndex(it)
                }?.let {
                    class_ = class_ { name = it.declaringClass.name}
                    method = method { name = it.name }
                }
            }
            toastHelper = toastHelper {
                val toastHelperClass =
                    "com.bilibili.droid.ToastHelper" from classloader ?: return@toastHelper
                class_ = class_ { name = toastHelperClass.name }
                show = method {
                    name = toastHelperClass.declaredMethods.firstOrNull {
                        it.isStatic && it.parameterTypes.size == 3 &&
                                it.parameterTypes[0] == Context::class.java &&
                                it.parameterTypes[1] == String::class.java &&
                                it.parameterTypes[2] == Int::class.javaPrimitiveType
                    }?.name ?: return@method
                }
                cancel = method {
                    name = toastHelperClass.declaredMethods.firstOrNull {
                        it.isStatic && it.parameterTypes.isEmpty()
                    }?.name ?: return@method
                }
            }

            dexHelper.close()
        }

        @Volatile
        lateinit var instance: BiliBiliPackage
    }
}
