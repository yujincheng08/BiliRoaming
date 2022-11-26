@file:Suppress("DEPRECATION")

package me.iacn.biliroaming

import android.app.AndroidAppHelper
import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
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
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
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
        Log.d(result.copy { clearMapIds() }.print())
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
    val homeUserCenterClass by Weak { if (mHookInfo.settings.homeUserCenterCount == 1) mHookInfo.settings.homeUserCenterList.first().class_ from mClassLoader else null }
    val musicNotificationHelperClass by Weak { mHookInfo.musicNotification.helper from mClassLoader }
    val liveNotificationHelperClass by Weak { mHookInfo.musicNotification.liveHelper from mClassLoader }
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
    val commentCopyNewClass by Weak { mHookInfo.commentLongClickNew from mClassLoader }
    val kotlinJsonClass by Weak { "kotlinx.serialization.json.Json" from mClassLoader }
    val gsonConverterClass by Weak { mHookInfo.gsonHelper.gsonConverter from mClassLoader }
    val playerCoreServiceV2Class by Weak { mHookInfo.playerCoreService.class_ from mClassLoader }
    val teenagersModeDialogActivityClass by Weak {
        "com.bilibili.teenagersmode.ui.TeenagersModeDialogActivity" from mClassLoader
    }
    val pegasusFeedClass by Weak { mHookInfo.pegasusFeed.class_ from mClassLoader }
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
    val videoUpperAdClass by Weak { mHookInfo.videoUpperAd.class_ from mClassLoader }
    val ellipsizingTextViewClass by Weak { "com.bilibili.bplus.followingcard.widget.EllipsizingTextView" from mClassLoader }
    val dynamicDescHolderListenerClass by Weak { mHookInfo.dynDescHolderListener from mClassLoader }
    val shareClickResultClass by Weak { "com.bilibili.lib.sharewrapper.online.api.ShareClickResult" from mClassLoader }
    val backgroundPlayerClass by Weak { mHookInfo.musicNotification.backgroundPlayer from mClassLoader }
    val playerServiceClass by Weak { mHookInfo.musicNotification.playerService from mClassLoader }
    val mediaSessionCallbackClass by Weak { mHookInfo.musicNotification.mediaSessionCallback from mClassLoader }
    val playerOnSeekCompleteClass by Weak { mHookInfo.playerCoreService.seekCompleteListener from mClassLoader }
    val musicBackgroundPlayerClass by Weak { mHookInfo.musicNotification.musicBackgroundPlayer from mClassLoader }
    val kanbanCallbackClass by Weak { mHookInfo.kanBan.class_ from mClassLoader }
    val toastHelperClass by Weak { mHookInfo.toastHelper.class_ from mClassLoader }
    val videoDetailCallbackClass by Weak { mHookInfo.videoDetailCallback from mClassLoader }
    val biliAccountsClass by Weak { mHookInfo.biliAccounts.class_ from mClassLoader }
    val networkExceptionClass by Weak { "com.bilibili.lib.moss.api.NetworkException" from mClassLoader }
    val brotliInputStreamClass by Weak { mHookInfo.brotliInputStream from mClassLoader }
    val arcConfClass by Weak { "com.bapis.bilibili.app.playurl.v1.ArcConf" from mClassLoader }
    val arcConfExtraContentClass by Weak { "com.bapis.bilibili.app.playurl.v1.ExtraContent" from mClassLoader }
    val commentInvalidFragmentClass by Weak {
        "com.bilibili.bangumi.ui.page.detail.OGVCommentFragment".from(mClassLoader)
            ?: "com.bilibili.bangumi.ui.page.detail.BangumiCommentInvalidFragmentV2"
                .from(mClassLoader)
    }
    val playerQualityServiceClass by Weak { "com.bilibili.playerbizcommon.features.quality.PlayerQualityService" from mClassLoader }
    val mossResponseHandlerClass by Weak { "com.bilibili.lib.moss.api.MossResponseHandler" from mClassLoader }
    val projectionPlayUrlClass by Weak { "com.bilibili.lib.projection.internal.api.model.ProjectionPlayUrl" from mClassLoader }
    val playerFullStoryWidgetClass by Weak { mHookInfo.playerFullStoryWidget.class_ from mClassLoader }
    val responseBodyClass by Weak { mHookInfo.okHttp.responseBody.class_ from mClassLoader }
    val mediaTypeClass by Weak { mHookInfo.okHttp.mediaType.class_ from mClassLoader }
    val biliCallClass by Weak { mHookInfo.biliCall.class_ from mClassLoader }
    val parserClass by Weak { mHookInfo.biliCall.parser from mClassLoader }
    val livePagerRecyclerViewClass by Weak { mHookInfo.livePagerRecyclerView from mClassLoader }
    val cronCanvasClass by Weak { "com.bilibili.cron.Canvas" from mClassLoader }
    val subtitleConfigGetClass by Weak { "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.receive.GetDanmakuConfig\$SubtitleConfig" from mClassLoader }
    val subtitleConfigChangeClass by Weak { "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.send.DanmakuConfigChange\$SubtitleConfig" from mClassLoader }
    val liveRoomPlayerViewClass by Weak { "com.bilibili.bililive.room.ui.roomv3.player.container.LiveRoomPlayerContainerView" from mClassLoader }

    val ids: Map<String, Int> by lazy {
        mHookInfo.mapIds.idsMap
    }

    fun getCustomizeAccessKey(area: String): String? {
        var key = sPrefs.getString("${area}_accessKey", null)
        if (key.isNullOrBlank()) key = accessKey
        return key
    }

    val biliAccounts by lazy {
        biliAccountsClass?.callStaticMethodOrNull(
            mHookInfo.biliAccounts.get.orNull,
            AndroidAppHelper.currentApplication()
        )
    }

    val accessKey by lazy {
        biliAccounts?.callMethodOrNullAs<String>(mHookInfo.biliAccounts.getAccessKey.orNull)
    }

    fun fastJsonParse() = mHookInfo.fastJson.parse.orNull

    fun colorArray() = mHookInfo.themeHelper.colorArray.orNull

    fun colorId() = mHookInfo.themeIdHelper.colorId.orNull

    fun columnColorArray() = mHookInfo.columnHelper.colorArray.orNull

    fun signQueryName() = mHookInfo.signQuery.method.orNull

    fun skinList() = mHookInfo.skinList.orNull

    fun themeReset() = mHookInfo.themeProcessor.methodsList.map { it.orNull }

    fun homeCenters() = mHookInfo.settings.homeUserCenterList.map {
        it.class_ from mClassLoader to it.addSetting.orNull
    }

    fun requestField() = mHookInfo.okHttp.response.request.orNull

    fun likeMethod() = mHookInfo.section.method.orNull

    fun partyLikeMethod() = mHookInfo.partySection.method.orNull

    fun themeName() = mHookInfo.themeName.field.orNull

    fun shareWrapper() = mHookInfo.shareWrapper.method.orNull

    fun downloadingThread() = mHookInfo.downloadThread.field.orNull

    fun reportDownloadThread() = mHookInfo.downloadThread.reportDownload.method.orNull

    fun setNotification() = mHookInfo.musicNotification.builder.method.orNull

    fun openDrawer() = mHookInfo.drawer.open.orNull

    fun closeDrawer() = mHookInfo.drawer.close.orNull

    fun isDrawerOpen() = mHookInfo.drawer.isOpen.orNull

    fun paramsToMap() = mHookInfo.bangumiParams.paramsToMap.orNull

    fun gson() = mHookInfo.gsonHelper.gson.orNull

    fun defaultSpeed() = mHookInfo.playerCoreService.getDefaultSpeed.orNull

    fun urlField() = mHookInfo.okHttp.request.url.orNull

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

    fun canTryWatchVipQuality() = mHookInfo.canTryWatchVipQuality.orNull

    fun setInvalidTips() = commentInvalidFragmentClass?.declaredMethods?.find { m ->
        m.parameterTypes.let { it.size == 2 && it[0] == commentInvalidFragmentClass && it[1].name == "kotlin.Pair" }
    }?.name

    fun musicWrapperPlayer() = mHookInfo.musicNotification.musicWrapperPlayer.orNull

    fun musicPlayer() = mHookInfo.musicNotification.musicPlayer.orNull

    fun musicPlayerService() = mHookInfo.musicNotification.musicPlayerService.orNull

    fun playerFullStoryWidget() = mHookInfo.playerFullStoryWidget.method.orNull

    fun create() = mHookInfo.okHttp.responseBody.create.orNull

    fun string() = mHookInfo.okHttp.responseBody.string.orNull

    fun get() = mHookInfo.okHttp.mediaType.get.orNull

    fun setParser() = mHookInfo.biliCall.setParser.orNull

    fun biliCallRequestField() = mHookInfo.biliCall.request.orNull

    fun onOperateClick() = mHookInfo.onOperateClick.orNull

    fun getContentString() = mHookInfo.getContentString.orNull

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
                    if (info.lastUpdateTime >= lastUpdateTime && info.lastUpdateTime >= lastModuleUpdateTime
                        && getVersionCode(context.packageName) == info.clientVersionCode
                        && BuildConfig.VERSION_CODE == info.moduleVersionCode
                        && BuildConfig.VERSION_NAME == info.moduleVersionName
                        && info.biliAccounts.getAccessKey.orNull != null
                    )
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

            val dexHelper =
                DexHelper(classloader.findDexClassLoader(::findRealClassloader) ?: return@hookInfo)
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
            clientVersionCode = getVersionCode(context.packageName)
            moduleVersionCode = BuildConfig.VERSION_CODE
            moduleVersionName = BuildConfig.VERSION_NAME
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
                name = "retrofit2.Response".from(classloader)?.name
                    ?: dexHelper.findMethodUsingString(
                        "rawResponse must be successful response",
                        false,
                        -1,
                        -1,
                        null,
                        -1,
                        null,
                        null,
                        null,
                        true
                    ).asSequence().firstNotNullOfOrNull {
                        dexHelper.decodeMethodIndex(it)
                    }?.declaringClass?.name ?: return@class_
            }
            val responseBodyClass = "okhttp3.ResponseBody".from(classloader)
                ?: dexHelper.findMethodUsingString(
                    "Cannot buffer entire body for content length: ",
                    false,
                    -1,
                    -1,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                }?.declaringClass
            okHttp = okHttp {
                val responseClass = "okhttp3.Response".from(classloader)
                    ?: dexHelper.findMethodUsingString(
                        "Response{protocol=",
                        false,
                        -1,
                        -1,
                        null,
                        -1,
                        null,
                        null,
                        null,
                        true
                    ).asSequence().firstNotNullOfOrNull {
                        dexHelper.decodeMethodIndex(it)
                    }?.declaringClass ?: return@okHttp
                val requestClass = "okhttp3.Request".from(classloader)
                    ?: dexHelper.findMethodUsingString(
                        "Request{method=",
                        false,
                        -1,
                        -1,
                        null,
                        -1,
                        null,
                        null,
                        null,
                        true
                    ).asSequence().firstNotNullOfOrNull {
                        dexHelper.decodeMethodIndex(it)
                    }?.declaringClass ?: return@okHttp
                val urlClass = "okhttp3.HttpUrl".from(classloader)
                    ?: dexHelper.findMethodUsingString(
                        ":@",
                        false,
                        -1,
                        -1,
                        null,
                        -1,
                        null,
                        null,
                        null,
                        true
                    ).asSequence().firstNotNullOfOrNull {
                        dexHelper.decodeMethodIndex(it)
                    }?.declaringClass ?: return@okHttp
                responseBodyClass ?: return@okHttp
                val getMethod = dexHelper.findMethodUsingString(
                    "No subtype found for:",
                    true,
                    -1,
                    -1,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                } ?: return@okHttp
                request = request {
                    class_ = class_ { name = requestClass.name }
                    url = field {
                        name = requestClass.findFirstFieldByExactTypeOrNull(urlClass)?.name
                            ?: return@field
                    }
                }
                response = response {
                    class_ = class_ { name = responseClass.name }
                    request = field {
                        name = responseClass.findFirstFieldByExactTypeOrNull(requestClass)?.name
                            ?: return@field
                    }
                }
                responseBody = responseBody {
                    class_ = class_ { name = responseBodyClass.name }
                    create = method {
                        name = responseBodyClass.methods.find {
                            it.isStatic && it.parameterTypes.size == 2 && it.parameterTypes[1] == String::class.java
                        }?.name ?: return@method
                    }
                    string = method {
                        name = responseBodyClass.methods.find {
                            it.parameterTypes.isEmpty() && it.returnType == String::class.java
                        }?.name ?: return@method
                    }
                }
                mediaType = mediaType {
                    class_ = class_ { name = getMethod.declaringClass.name }
                    get = method { name = getMethod.name }
                }
            }
            fastJson = fastJson {
                val fastJsonClass = dexHelper.findMethodUsingString(
                    "toJSON error",
                    false,
                    -1,
                    -1,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                }?.declaringClass ?: return@fastJson
                val notObfuscated = "JSON" == fastJsonClass.simpleName
                class_ = class_ { name = fastJsonClass.name }
                parse = method { name = if (notObfuscated) "parseObject" else "a" }
            }
            biliAccounts = biliAccounts {
                val biliAccountsClass = dexHelper.findMethodUsingString(
                    "refresh token error",
                    false,
                    -1,
                    0,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                }?.declaringClass ?: return@biliAccounts
                class_ = class_ { name = biliAccountsClass.name }
                val biliAccountIndex = dexHelper.encodeClassIndex(biliAccountsClass)
                val biliAuthFragmentMethodIndex = dexHelper.findMethodUsingString(
                    "initFacial enter",
                    true,
                    -1,
                    -1,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    true
                ).firstOrNull() ?: return@biliAccounts
                val calledMethods = dexHelper.findMethodInvoking(
                    biliAuthFragmentMethodIndex,
                    -1,
                    -1,
                    null,
                    biliAccountIndex,
                    null,
                    null,
                    null,
                    false
                ).asSequence().mapNotNull {
                    dexHelper.decodeMethodIndex(it) as? Method
                }

                get = method {
                    name = calledMethods.firstOrNull {
                        it.isStatic && it.parameterTypes.size == 1 && it.parameterTypes[0] == Context::class.java && it.returnType == biliAccountsClass
                    }?.name ?: return@method
                }

                getAccessKey = method {
                    name = calledMethods.firstOrNull {
                        it.isNotStatic && it.parameterTypes.isEmpty() && it.returnType == String::class.java
                    }?.name ?: return@method
                }
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
                val shareToIndex = dexHelper.findMethodUsingString(
                    "share.helper.inner",
                    false,
                    -1,
                    -1,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    true
                ).firstOrNull() ?: return@shareWrapper
                val shareHelperClass =
                    dexHelper.decodeMethodIndex(shareToIndex)?.declaringClass ?: return@shareWrapper
                class_ = class_ { name = shareHelperClass.name }
                val shareHelperIndex = dexHelper.encodeClassIndex(shareHelperClass)
                val stringIndex = dexHelper.encodeClassIndex(String::class.java)
                val bundleIndex = dexHelper.encodeClassIndex(Bundle::class.java)
                val shareMethod = dexHelper.findMethodInvoking(
                    shareToIndex,
                    -1,
                    2,
                    "VLL",
                    shareHelperIndex,
                    longArrayOf(stringIndex, bundleIndex),
                    null,
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                } ?: return@shareWrapper
                method = method { name = shareMethod.name }
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
            section = section {
                val progressBarClass = "tv.danmaku.biliplayer.view.RingProgressBar" from classloader
                    ?: "com.bilibili.playerbizcommon.view.RingProgressBar" from classloader
                val sectionClass = classesList.filter {
                    it.startsWith("tv.danmaku.bili.ui.video.section") ||
                            it.startsWith("tv.danmaku.bili.ui.video.profile.action")
                }.firstNotNullOfOrNull { c ->
                    c.findClass(classloader).takeIf {
                        it.declaredFields.any { f -> f.type == progressBarClass }
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
                val partySectionClass = dexHelper.findMethodUsingString(
                    "mRecommendLayout",
                    false,
                    -1,
                    -1,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                }?.declaringClass ?: return@partySection
                class_ = class_ { name = partySectionClass.name }
                method = method {
                    name = partySectionClass.superclass?.declaredMethods?.firstOrNull {
                        it.parameterTypes.size == 1 && it.returnType == Void::class.javaPrimitiveType && !it.isFinal
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
                menuGroupItem = class_ { name = menuGroupItemClass.name }
                settingRouter = class_ {
                    name = dexHelper.findMethodUsingString(
                        "UperHotMineSolution",
                        false,
                        -1,
                        0,
                        "V",
                        -1,
                        null,
                        null,
                        null,
                        true
                    ).asSequence().firstNotNullOfOrNull {
                        dexHelper.decodeMethodIndex(it)
                    }?.declaringClass?.interfaces?.firstOrNull()?.let {
                        dexHelper.encodeClassIndex(it)
                    }?.let {
                        dexHelper.findField(it, null, true).asSequence().firstNotNullOfOrNull { f ->
                            dexHelper.decodeFieldIndex(f)
                        }?.declaringClass
                    }?.name ?: return@class_
                }
                val contextIndex = dexHelper.encodeClassIndex(Context::class.java)
                val listIndex = dexHelper.encodeClassIndex(List::class.java)
                dexHelper.findMethodUsingString(
                    "main.my-information.noportrait.0.show",
                    false,
                    -1,
                    -1,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    false
                ).asSequence().mapNotNull { dexHelper.decodeMethodIndex(it)?.declaringClass }
                    .forEach { homeUserCenterClass ->
                        val homeUserCenterIndex = dexHelper.encodeClassIndex(homeUserCenterClass)
                        val addSettingMethod = dexHelper.findMethodUsingString(
                            "bilibili://main/scan",
                            true,
                            -1,
                            -1,
                            null,
                            homeUserCenterIndex,
                            null,
                            longArrayOf(contextIndex),
                            null,
                            false
                        ).asSequence().mapNotNull {
                            dexHelper.decodeMethodIndex(it) as? Method
                        }.firstOrNull {
                            it.parameterTypes.size == 2 &&
                                    it.parameterTypes[1] != List::class.java
                        } ?: dexHelper.findMethodUsingString(
                            "activity://main/preference",
                            true,
                            -1,
                            -1,
                            null,
                            homeUserCenterIndex,
                            null,
                            longArrayOf(contextIndex, listIndex),
                            null,
                            true
                        ).asSequence().firstNotNullOfOrNull {
                            dexHelper.decodeMethodIndex(it)
                        } ?: dexHelper.findMethodUsingString(
                            "bilibili://main/preference",
                            true,
                            -1,
                            -1,
                            null,
                            homeUserCenterIndex,
                            null,
                            longArrayOf(contextIndex, listIndex),
                            null,
                            true
                        ).asSequence().firstNotNullOfOrNull {
                            dexHelper.decodeMethodIndex(it)
                        } ?: return@settings
                        homeUserCenter += homeUserCenter {
                            class_ = class_ { name = homeUserCenterClass.name }
                            addSetting = method { name = addSettingMethod.name }
                        }
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
                        name = drawerLayoutClass.getMethod(
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
                            } && declaredFields.any {
                                it.type == TextView::class.java
                            } && declaredFields.any {
                                it.type == downloadingActivityClass
                            }
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
                val bitmapIndex = dexHelper.encodeClassIndex(Bitmap::class.java)
                val notificationIndex = dexHelper.encodeClassIndex(Notification::class.java)
                val musicNotificationHelperClass = dexHelper.findMethodUsingString(
                    "buildNewJBNotification",
                    true,
                    notificationIndex,
                    1,
                    "LL",
                    -1,
                    longArrayOf(bitmapIndex),
                    null,
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                }?.declaringClass ?: return@musicNotification
                helper = class_ { name = musicNotificationHelperClass.name }
                liveHelper = class_ {
                    name = dexHelper.findMethodUsingString(
                        "buildLiveNotification",
                        true,
                        notificationIndex,
                        1,
                        "LL",
                        -1,
                        longArrayOf(bitmapIndex),
                        null,
                        null,
                        true
                    ).asSequence().firstNotNullOfOrNull {
                        dexHelper.decodeMethodIndex(it)
                    }?.declaringClass?.name ?: return@class_
                }
                val musicNotificationHelperIndex =
                    dexHelper.encodeClassIndex(musicNotificationHelperClass)
                val musicManagerIndex = dexHelper.findMethodUsingString(
                    "the build sdk >= 8.0",
                    true,
                    -1,
                    -1,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    true
                ).firstOrNull() ?: return@musicNotification
                builder = notificationBuilder {
                    dexHelper.findMethodInvoking(
                        musicManagerIndex,
                        -1,
                        0,
                        "L",
                        musicNotificationHelperIndex,
                        null,
                        null,
                        null,
                        false
                    ).asSequence().flatMap {
                        dexHelper.findMethodInvoking(
                            it,
                            -1,
                            1,
                            "VL",
                            musicNotificationHelperIndex,
                            null,
                            null,
                            null,
                            false
                        ).asSequence()
                    }.firstNotNullOfOrNull {
                        dexHelper.decodeMethodIndex(it)
                    }?.let {
                        class_ = class_ {
                            name = (it as? Method)?.parameterTypes?.get(0)?.name ?: return@class_
                        }
                        method = method { name = it.name }
                    }
                }
                val backgroundPlayerClass = dexHelper.findMethodUsingString(
                    "backgroundPlayer status changed",
                    true,
                    -1,
                    2,
                    "VIZ",
                    -1,
                    null,
                    null,
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                }?.declaringClass ?: return@musicNotification
                backgroundPlayer = class_ { name = backgroundPlayerClass.name }
                val setStateMethod = dexHelper.findMethodUsingString(
                    "MediaSession setPlaybackState",
                    true,
                    -1,
                    1,
                    "VI",
                    -1,
                    null,
                    null,
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                } ?: return@musicNotification
                setState = method { name = setStateMethod.name }
                absMusicService = class_ { name = setStateMethod.declaringClass.name }
                val playerServiceClass = dexHelper.findMethodUsingString(
                    "mPlayerServiceManager",
                    true,
                    -1,
                    0,
                    "L",
                    -1,
                    null,
                    null,
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                }?.declaringClass?.superclass?.interfaces?.firstOrNull()
                playerService = class_ { name = playerServiceClass?.name ?: return@class_ }
                mediaSessionCallback = class_ {
                    name = classesList.filter {
                        it.startsWith("android.support.v4.media.session")
                    }.firstOrNull { c ->
                        c.findClass(classloader).declaredMethods.any {
                            it.name == "onSeekTo"
                        }
                    } ?: return@class_
                }
                val musicBackgroundPlayerClass = dexHelper.findMethodUsingString(
                    "MusicBackgroundPlayBack call resetPendingState",
                    true,
                    -1,
                    0,
                    "V",
                    -1,
                    null,
                    null,
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                }?.declaringClass ?: dexHelper.findMethodUsingString(
                    "MusicBackgroundPlayBack status changed",
                    true,
                    -1,
                    -1,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                }?.declaringClass

                musicBackgroundPlayer = class_ {
                    name = musicBackgroundPlayerClass?.name ?: return@class_
                }
                val musicWrapperPlayerClass = dexHelper.findMethodUsingString(
                    "call playNextVideo",
                    true,
                    -1,
                    -1,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                }?.declaringClass
                musicWrapperPlayer = field {
                    name = musicBackgroundPlayerClass?.declaredFields?.firstOrNull {
                        it.type == musicWrapperPlayerClass
                    }?.name ?: return@field
                }
                val ifs = musicWrapperPlayerClass?.interfaces?.flatMap {
                    it.interfaces.asSequence() ?: arrayOf(it).asSequence()
                } ?: return@musicNotification
                val musicPlayerField = musicWrapperPlayerClass.declaredFields.firstOrNull {
                    ifs.contains(it.type.interfaces.firstOrNull())
                } ?: return@musicNotification
                musicPlayer = field {
                    name = musicPlayerField.name
                }
                musicPlayerService = field {
                    name = musicPlayerField.type?.declaredFields?.firstOrNull {
                        it.type == playerServiceClass
                    }?.name ?: return@field
                }
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
                val gsonClass = dexHelper.findMethodUsingString(
                    "AssertionError (GSON",
                    true,
                    -1,
                    -1,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                }?.declaringClass ?: return@gsonHelper
                val gsonConverterClass =
                    dexHelper.findField(dexHelper.encodeClassIndex(gsonClass), null, false)
                        .asSequence().firstNotNullOfOrNull {
                            dexHelper.decodeFieldIndex(it)?.takeIf { f ->
                                f.isStatic && f.isFinal && f.isPublic
                            }?.declaringClass?.takeIf { c ->
                                c.declaredMethods.any { m ->
                                    m.returnType == gsonClass && m.isNotStatic
                                }
                            }
                        } ?: return@gsonHelper
                gsonConverter = class_ { name = gsonConverterClass.name }
                gson = field {
                    name = gsonConverterClass.declaredMethods.firstOrNull { m ->
                        m.returnType == gsonClass && m.isNotStatic
                    }?.name ?: return@field
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
                val seekToMethod = dexHelper.findMethodUsingString(
                    "[player]seek to",
                    true,
                    -1,
                    1,
                    "VI",
                    -1,
                    null,
                    null,
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                } ?: run {
                    val doSeekToIndex = dexHelper.findMethodUsingString(
                        "[player]seek to",
                        true,
                        -1,
                        2,
                        "VIZ",
                        -1,
                        null,
                        null,
                        null,
                        true
                    ).firstOrNull() ?: return@playerCoreService
                    val doSeekToMethod =
                        dexHelper.decodeMethodIndex(doSeekToIndex) ?: return@playerCoreService
                    val playerCoreServiceIndex =
                        dexHelper.encodeClassIndex(doSeekToMethod.declaringClass)
                    dexHelper.findMethodInvoked(
                        doSeekToIndex,
                        -1,
                        1,
                        "VI",
                        playerCoreServiceIndex,
                        null,
                        null,
                        null,
                        true
                    ).asSequence().firstNotNullOfOrNull {
                        dexHelper.decodeMethodIndex(it)
                    }
                } ?: return@playerCoreService
                val playerCoreServiceClass = seekToMethod.declaringClass
                seekTo = method { name = seekToMethod.name }
                class_ = class_ { name = playerCoreServiceClass.name }
                val onSeekCompleteMethod = dexHelper.findMethodUsingString(
                    "[player]seek complete",
                    true,
                    -1,
                    -1,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                } ?: return@playerCoreService
                onSeekComplete = method { name = onSeekCompleteMethod.name }
                seekCompleteListener =
                    class_ { name = onSeekCompleteMethod.declaringClass.name }
                getDefaultSpeed = method {
                    name = dexHelper.findMethodUsingString(
                        "player_key_video_speed",
                        true,
                        -1,
                        1,
                        "FZ",
                        dexHelper.encodeClassIndex(playerCoreServiceClass),
                        null,
                        null,
                        null,
                        true
                    ).asSequence().firstNotNullOfOrNull {
                        dexHelper.decodeMethodIndex(it)
                    }?.name ?: return@method
                }
            }
            generalResponse = class_ {
                name = "com.bilibili.okretro.GeneralResponse"
            }
            pegasusFeed = pegasusFeed {
                val fastJSONObject =
                    dexHelper.encodeClassIndex(
                        "com.alibaba.fastjson.JSONObject" from classloader ?: return@pegasusFeed
                    )
                val pegasusFeedClass = dexHelper.findMethodUsingString(
                    "card_type is empty",
                    false,
                    -1,
                    -1,
                    null,
                    -1,
                    null,
                    longArrayOf(fastJSONObject),
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                }?.declaringClass ?: return@pegasusFeed
                class_ = class_ { name = pegasusFeedClass.name }
                method = method {
                    name = pegasusFeedClass.declaredMethods.firstOrNull {
                        it.parameterTypes.size == 1 && it.parameterTypes[0].name == this@hookInfo.okHttp.responseBody.class_.name
                                && it.returnType != Object::class.java
                    }?.name ?: return@method
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
            val commentSpanClass = "com.bilibili.app.comm.comment2.widget.CommentExpandableTextView"
                .from(classloader) ?: dexHelper.findMethodUsingString(
                "comment.catch_on_draw_exception",
                false,
                -1,
                -1,
                null,
                -1,
                null,
                null,
                null,
                true
            ).asSequence().firstNotNullOfOrNull {
                dexHelper.decodeMethodIndex(it)
            }?.declaringClass
            commentSpan = class_ {
                name = commentSpanClass?.name ?: return@class_
            }
            commentLongClick = class_ {
                val viewIndex = dexHelper.encodeClassIndex(View::class.java)
                val onLongClickListenerIndex = dexHelper.encodeMethodIndex(
                    View::class.java.getDeclaredMethod(
                        "setOnLongClickListener",
                        View.OnLongClickListener::class.java
                    )
                )
                name = dexHelper.findMethodInvoked(
                    onLongClickListenerIndex,
                    -1,
                    2,
                    "VLL",
                    -1,
                    longArrayOf(viewIndex, -1),
                    null,
                    null,
                    false
                ).firstOrNull {
                    dexHelper.decodeMethodIndex(it)?.run {
                        this !is Constructor<*> && isStatic && isPublic && (this as? Method)?.parameterTypes.let { t ->
                            t?.get(0) == View::class.java && t[1] != CharSequence::class.java
                        }
                    } == true
                }?.let {
                    dexHelper.findMethodInvoking(it, -1, 2, "VLL", -1, null, null, null, false)
                        .asSequence()
                        .firstNotNullOfOrNull { m -> dexHelper.decodeMethodIndex(m) }?.declaringClass?.name
                } ?: return@class_
            }
            commentLongClickNew = class_ {
                val setExpandLinesMethod = commentSpanClass?.declaredMethods?.find {
                    it.parameterTypes.size == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType
                } ?: return@class_
                val setExpandLinesIndex = dexHelper.encodeMethodIndex(setExpandLinesMethod)
                name = dexHelper.findMethodInvoked(
                    setExpandLinesIndex,
                    -1,
                    1,
                    "VL",
                    -1,
                    null,
                    null,
                    null,
                    true
                ).firstOrNull()?.let {
                    dexHelper.findMethodInvoking(it, -1, 2, "VLL", -1, null, null, null, true)
                        .map { m -> dexHelper.decodeMethodIndex(m) }
                        .firstOrNull()?.declaringClass?.name
                } ?: return@class_
            }
            okio = okIO {
                val responseClassIndex =
                    dexHelper.encodeClassIndex(responseBodyClass ?: return@okIO)
                val createMethodIndex = dexHelper.findMethodUsingString(
                    "source == null",
                    false,
                    -1,
                    -1,
                    null,
                    responseClassIndex,
                    null,
                    null,
                    null,
                    true
                ).firstOrNull() ?: return@okIO
                val wrapperClass = dexHelper.findMethodInvoking(
                    createMethodIndex,
                    -1,
                    -1,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    false
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it).takeIf { m ->
                        m is Constructor<*>
                    }
                }?.declaringClass ?: return@okIO
                class_ = class_ { name = wrapperClass.name }
                val okioField = wrapperClass.declaredFields.firstOrNull { f ->
                    f.type.isAbstract
                } ?: return@okIO
                field = field { name = okioField.name }
                val okioLenField = wrapperClass.declaredFields.firstOrNull { f ->
                    f.type == Long::class.javaPrimitiveType
                } ?: return@okIO
                length = field { name = okioLenField.name }
            }
            okioBuffer = okIOBuffer {
                val okioBufferClass = dexHelper.findMethodUsingString(
                    "already attached to a buffer",
                    false,
                    -1,
                    -1,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                }?.declaringClass ?: return@okIOBuffer
                class_ = class_ { name = okioBufferClass.name }
                val okioInputStreamMethod = okioBufferClass.declaredMethods.firstOrNull {
                    it.parameterTypes.isEmpty() && it.returnType == InputStream::class.java
                } ?: return@okIOBuffer
                inputStream = method { name = okioInputStreamMethod.name }
                val okioReadFromMethod = okioBufferClass.declaredMethods.firstOrNull {
                    it.parameterTypes.size == 1 && it.parameterTypes[0] == InputStream::class.java
                } ?: return@okIOBuffer
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
                val descViewHolderClass = dexHelper.findMethodUsingString(
                    "AV%d",
                    false,
                    -1,
                    -1,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                }?.declaringClass ?: return@descCopy
                val descViewHolderIndex = dexHelper.encodeClassIndex(descViewHolderClass)
                val copyMethodIndex = dexHelper.findMethodUsingString(
                    "clipboard",
                    false,
                    -1,
                    -1,
                    null,
                    descViewHolderIndex,
                    null,
                    null,
                    null,
                    true
                ).firstOrNull() ?: return@descCopy
                val toCopyMethodIndex = dexHelper.findMethodInvoked(
                    copyMethodIndex,
                    -1,
                    3,
                    "VLZL",
                    descViewHolderIndex,
                    null,
                    null,
                    null,
                    true
                ).firstOrNull() ?: return@descCopy
                val viewIndex = dexHelper.encodeClassIndex(View::class.java)
                val clickableSpanIndex = dexHelper.encodeClassIndex(ClickableSpan::class.java)
                dexHelper.findMethodInvoked(
                    toCopyMethodIndex,
                    -1,
                    2,
                    "VLL",
                    -1,
                    longArrayOf(viewIndex, clickableSpanIndex),
                    null,
                    null,
                    false
                ).asSequence().mapNotNull {
                    dexHelper.decodeMethodIndex(it)
                }.forEach {
                    classes += class_ { name = it.declaringClass.name }
                    methods += method { name = it.name }
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
                    dexHelper.findMethodInvoked(
                        dexHelper.encodeMethodIndex(it),
                        -1,
                        1,
                        "VL",
                        -1,
                        null,
                        null,
                        null,
                        true
                    ).firstOrNull()
                }?.let {
                    dexHelper.decodeMethodIndex(it)
                }?.let {
                    class_ = class_ { name = it.declaringClass.name }
                    method = method { name = it.name }
                }
            }
            toastHelper = toastHelper {
                val showToastCallerIndex = dexHelper.findMethodUsingString(
                    "main.lessonmodel.enterdetail.change-pswd-success.click",
                    false,
                    -1,
                    -1,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    true
                ).firstOrNull() ?: return@toastHelper
                val contextIndex = dexHelper.encodeClassIndex(Context::class.java)
                val stringIndex = dexHelper.encodeClassIndex(String::class.java)
                val showToastMethod = dexHelper.findMethodInvoking(
                    showToastCallerIndex,
                    -1,
                    3,
                    "VLLI",
                    -1,
                    longArrayOf(contextIndex, stringIndex, -1),
                    null,
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                } ?: return@toastHelper
                val toastHelperClass = showToastMethod.declaringClass
                class_ = class_ { name = toastHelperClass.name }
                show = method { name = showToastMethod.name }
                cancel = method {
                    name = toastHelperClass.declaredMethods.firstOrNull {
                        it.isStatic && it.parameterTypes.isEmpty()
                    }?.name ?: return@method
                }
            }
            brotliInputStream = class_ {
                name = dexHelper.findMethodUsingString(
                    "Brotli decoder initialization failed",
                    false,
                    -1,
                    -1,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    true
                ).asSequence().firstNotNullOfOrNull {
                    dexHelper.decodeMethodIndex(it)
                }?.declaringClass?.name ?: return@class_
            }
            canTryWatchVipQuality = method {
                name = dexHelper.findMethodUsingString(
                    "user is vip, cannot trywatch",
                    false,
                    dexHelper.encodeClassIndex(Boolean::class.java),
                    -1,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    true
                ).firstOrNull()?.let {
                    dexHelper.decodeMethodIndex(it)
                }?.name ?: return@method
            }
            playerFullStoryWidget = playerFullStoryWidget {
                val playerFullStoryWidgetClass = dexHelper.findMethodUsingString(
                    "PlayerFullStoryWidget",
                    true,
                    -1,
                    -1,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    true
                ).firstOrNull()?.let {
                    dexHelper.decodeMethodIndex(it)
                }?.declaringClass ?: return@playerFullStoryWidget
                class_ = class_ {
                    name = playerFullStoryWidgetClass.name
                }
                method = method {
                    name = playerFullStoryWidgetClass.declaredMethods.firstOrNull {
                        it.isStatic && it.parameterTypes.size == 1 && it.parameterTypes[0] == playerFullStoryWidgetClass && it.returnType == Boolean::class.javaPrimitiveType
                    }?.name ?: return@method
                }
            }
            biliCall = biliCall {
                val biliCallClass = "com.bilibili.okretro.call.BiliCall".from(classloader)
                    ?: dexHelper.findMethodUsingString(
                        "Any arguments of BiliCall constructor can not be null",
                        false,
                        -1,
                        -1,
                        null,
                        -1,
                        null,
                        null,
                        null,
                        true
                    ).asSequence().firstNotNullOfOrNull {
                        dexHelper.decodeMethodIndex(it)
                    }?.declaringClass ?: return@biliCall
                val setParserMethod = biliCallClass.methods.find {
                    it.parameterTypes.size == 1 && it.parameterTypes[0].let { c ->
                        c.isInterface && c.methods.size == 2 && c.methods.any { m ->
                            m.parameterTypes.size == 1 && m.parameterTypes[0] == Any::class.java
                                    && m.returnType == Any::class.java
                        }
                    }
                } ?: return@biliCall
                val requestFiled = biliCallClass.declaredFields.find {
                    it.type.name == this@hookInfo.okHttp.request.class_.name
                } ?: return@biliCall
                class_ = class_ { name = biliCallClass.name }
                parser = class_ { name = setParserMethod.parameterTypes[0].name }
                setParser = method { name = setParserMethod.name }
                request = field { name = requestFiled.name }
            }
            dexHelper.findMethodUsingString(
                "im.chat-group.msg.repost.click",
                false,
                -1,
                -1,
                null,
                -1,
                null,
                null,
                null,
                true
            ).asSequence().firstNotNullOfOrNull {
                dexHelper.decodeMethodIndex(it) as? Method
            }?.let {
                val getContentStringMethod = it.parameterTypes[1].declaredMethods.find { m ->
                    m.returnType == String::class.java && m.parameterTypes.isEmpty()
                } ?: return@let
                onOperateClick = method { name = it.name }
                getContentString = method { name = getContentStringMethod.name }
            }
            livePagerRecyclerView = class_ {
                val liveVerticalPagerView =
                    "com.bilibili.bililive.room.ui.roomv3.vertical.widget.LiveVerticalPagerView"
                        .from(classloader) ?: return@class_
                name = liveVerticalPagerView.declaredFields.find {
                    View::class.java.isAssignableFrom(it.type)
                }?.type?.name ?: return@class_
            }

            dexHelper.close()
        }

        @Volatile
        lateinit var instance: BiliBiliPackage
    }
}
