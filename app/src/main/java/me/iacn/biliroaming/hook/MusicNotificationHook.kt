package me.iacn.biliroaming.hook

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import java.lang.reflect.Modifier


class MusicNotificationHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    class ActionDesc(var icon: Int? = null, var title: CharSequence? = null, var intent: PendingIntent? = null)

    private val bitmapActionClass by Weak { "android.widget.RemoteViews.BitmapReflectionAction".findClass(mClassLoader) }
    private val reflectionActionClass by Weak { "android.widget.RemoteViews.ReflectionAction".findClass(mClassLoader) }
    private val onClickActionClass by Weak { "android.widget.RemoteViews.SetOnClickResponse".findClass(mClassLoader) }
    private val mediaMetadataClass by Weak { "android.support.v4.media.MediaMetadataCompat".findClass(mClassLoader) }

    private var position = 0L
    private var speed = 1f
    private var duration = 0L
    private var lastState = 0
    private var lastSeekService: Any? = null

    private val getFlagMethod by lazy {
        instance.absMusicServiceClass?.declaredMethods?.firstOrNull {
            it.parameterTypes.isEmpty() &&
                    it.returnType == Long::class.javaPrimitiveType
        }
    }

    private val updateStateMethod by lazy {
        instance.absMusicServiceClass?.declaredMethods?.firstOrNull {
            it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    it.returnType == Void::class.javaPrimitiveType &&
                    Modifier.isProtected(it.modifiers)
        }
    }

    private val updateMetadataMethod by lazy {
        instance.absMusicServiceClass?.declaredMethods?.firstOrNull {
            it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Boolean::class.javaPrimitiveType
        }
    }

    private val playerHelperField by lazy {
        instance.absMusicServiceClass?.declaredFields?.firstOrNull {
            it.type.name.startsWith("tv.danmaku.bili.ui.player.notification") &&
                    Modifier.isProtected(it.modifiers)
        }
    }

    private val backgroundHelper by lazy {
        instance.classesList.filter {
            it.startsWith("tv.danmaku.biliplayerv2.service.business.background")
        }.firstOrNull { c ->
            c.findClass(mClassLoader)?.interfaces?.contains(playerHelperField?.type) ?: false
        }
    }

    private val musicHelper by lazy {
        instance.classesList.filter {
            it.startsWith("com.bilibili.music.app.base.mediaplayer")
        }.firstOrNull { c ->
            c.findClass(mClassLoader)?.interfaces?.filter {
                it == playerHelperField?.type
            }?.count()?.let { it > 0 } ?: false
        }
    }

    private val metadataField by lazy {
        instance.absMusicServiceClass?.declaredFields?.firstOrNull {
            it.type == mediaMetadataClass
        }
    }

    private val metadataBundleField by lazy {
        mediaMetadataClass?.declaredFields?.firstOrNull {
            it.type == Bundle::class.java
        }
    }

    private val mediaSessionCallbackClass by lazy {
        instance.classesList.filter {
            it.startsWith("android.support.v4.media.session")
        }.firstOrNull { c ->
            c.findClass(mClassLoader)?.declaredMethods?.filter {
                it.name == "onSeekTo"
            }?.count()?.let { it > 0 } ?: false
        }?.findClass(mClassLoader)
    }

    private val playbackStateBuilderClass by lazy {
        val playbackStateClass = "android.support.v4.media.session.PlaybackStateCompat".findClassOrNull(mClassLoader)
        playbackStateClass?.declaredClasses?.firstOrNull { c ->
            c.declaredMethods.filter {
                it.returnType == playbackStateClass && it.parameterTypes.isEmpty()
            }.count() > 0
        }
    }

    private val setPlaybackStateMethod by lazy {
        playbackStateBuilderClass?.declaredMethods?.firstOrNull {
            it.parameterTypes.size == 4
        }
    }

    private val backgroundPlayerField by lazy {
        backgroundHelper?.findClass(mClassLoader)?.declaredFields?.firstOrNull {
            it.type.name.run {
                startsWith("tv.danmaku.biliplayerv2") &&
                        !startsWith("tv.danmaku.biliplayerv2.service")
            }
        }
    }

    private val mediaPlayerInterface by lazy {
        "tv.danmaku.ijk.media.player.IMediaPlayer".findClass(mClassLoader)
    }

    private val corePlayerClass by lazy {
        "tv.danmaku.biliplayerv2.service.core.PlayerCoreServiceV2".findClassOrNull(mClassLoader)
                ?: instance.classesList.filter {
                    it.startsWith("tv.danmaku.biliplayerv2.service")
                }.firstOrNull { c ->
                    c.findClass(mClassLoader)?.declaredFields?.filter {
                        it.type.name == "tv.danmaku.ijk.media.player.IMediaPlayer\$OnErrorListener"
                    }?.count()?.let { it > 0 } ?: false
                }?.findClass(mClassLoader)
    }

    private val corePlayerOnSeekListenerClass by lazy {
        instance.classesList.filter {
            corePlayerClass?.name?.let { name -> it.startsWith(name) } ?: false
        }.firstOrNull { c ->
            c.findClass(mClassLoader)?.interfaces?.map { it.name }
                    ?.contains("tv.danmaku.ijk.media.player.IMediaPlayer\$OnSeekCompleteListener")
                    ?: false
        }?.findClass(mClassLoader)
    }

    private val corePlayerMethod by lazy {
        backgroundPlayerField?.type?.declaredMethods?.firstOrNull {
            corePlayerClass?.interfaces?.contains(it.returnType) ?: false
        }
    }

    private val getSpeedMethod by lazy {
        corePlayerClass?.declaredMethods?.firstOrNull {
            it.returnType == Float::class.javaPrimitiveType && it.parameterTypes.size == 1 && it.parameterTypes[0] == Boolean::class.javaPrimitiveType
        }?.name ?: "o"
    }

    private val getDurationMethod by lazy {
        try {
            corePlayerClass?.getDeclaredMethod("getDuration")?.name
        } catch (e: Throwable) {
            "i"
        }
    }

    private val getCurrentPositionMethod by lazy {
        try {
            corePlayerClass?.getDeclaredMethod("getCurrentPosition")?.name
        } catch (e: Throwable) {
            "j"
        }
    }

    private val seekToMethod by lazy {
        try {
            corePlayerClass?.getDeclaredMethod("seekTo", Int::class.javaPrimitiveType)?.name
        } catch (e: Throwable) {
            "a"
        }
    }

    private val rxMediaPlayerInterface by lazy {
        "com.bilibili.opd.app.bizcommon.mediaplayer.rx.RxMediaPlayer".findClass(mClassLoader)
    }

    private val rxMediaPlayerField by lazy {
        musicHelper?.findClass(mClassLoader)?.declaredFields?.firstOrNull {
            it.type == rxMediaPlayerInterface
        }
    }

    private val rxMediaPlayerClass by lazy {
        instance.classesList.filter {
            it.startsWith("com.bilibili.opd.app.bizcommon.mediaplayer.rx")
        }.firstOrNull { c ->
            c.findClass(mClassLoader)?.interfaces?.contains(rxMediaPlayerInterface) ?: false
        }?.findClass(mClassLoader)
    }

    private val rxMediaPlayerImplClass by lazy {
        rxMediaPlayerClass?.declaredClasses?.firstOrNull { c ->
            c.declaredFields.filter {
                it.type == mediaPlayerInterface
            }.count() > 0
        }
    }

    private val rxMediaPlayerImplField by lazy {
        rxMediaPlayerClass?.declaredFields?.firstOrNull {
            it.type == rxMediaPlayerImplClass
        }
    }

    private val mediaPlayerField by lazy {
        rxMediaPlayerImplClass?.declaredFields?.firstOrNull {
            it.type == mediaPlayerInterface
        }
    }

    override fun startHook() {
        if (!sPrefs.getBoolean("music_notification", false)) return

        Log.d("startHook: MusicNotification")

        updateMetadataMethod?.hookBeforeMethod { param ->
            val playerHelper = param.thisObject.getObjectField(playerHelperField?.name)
            metadataField?.isAccessible = true
            val bundle = metadataField?.get(param.thisObject)?.getObjectFieldAs<Bundle>(metadataBundleField?.name)
                    ?: return@hookBeforeMethod
            val currentDuration = bundle.getLong(MediaMetadata.METADATA_KEY_DURATION)
            if (currentDuration != 0L) return@hookBeforeMethod
            param.args[0] = true
            duration = when (playerHelper?.javaClass?.name) {
                musicHelper ->
                    playerHelper?.getObjectField(rxMediaPlayerField?.name)?.getObjectField(rxMediaPlayerImplField?.name)?.getObjectField(mediaPlayerField?.name)?.callMethodAs<Long>("getDuration")
                            ?: 0L
                backgroundHelper ->
                    playerHelper?.getObjectField(backgroundPlayerField?.name)?.callMethod(corePlayerMethod?.name)?.callMethodAs<Int>(getDurationMethod)?.toLong()
                            ?: 0L
                else -> 0L
            }
            bundle.putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
        }

        rxMediaPlayerClass?.hookBeforeMethod("onSeekComplete", mediaPlayerInterface) {
            lastSeekService?.callMethod(updateStateMethod?.name, lastState)
        }

        corePlayerOnSeekListenerClass?.hookBeforeMethod("onSeekComplete", mediaPlayerInterface) {
            lastSeekService?.callMethod(updateStateMethod?.name, lastState)
        }

        mediaSessionCallbackClass?.hookAfterMethod("onSeekTo", Long::class.javaPrimitiveType) { param ->
            position = param.args[0] as Long
            val absMusicService = mediaSessionCallbackClass?.declaredFields?.get(0)?.run {
                val res = param.thisObject.getObjectField(name)?.run {
                    getObjectField(javaClass.declaredFields.last().name)
                }
                if (instance.absMusicServiceClass?.isInstance(res) != true)
                    res?.getObjectField(res.javaClass.declaredFields.last().name)
                else
                    res
            }
            val playerHelper = absMusicService?.getObjectField(playerHelperField?.name)
            when (playerHelper?.javaClass?.name) {
                musicHelper ->
                    playerHelper?.getObjectField(rxMediaPlayerField?.name)?.getObjectField(rxMediaPlayerImplField?.name)?.getObjectField(mediaPlayerField?.name)?.callMethod("seekTo", position)
                backgroundHelper ->
                    playerHelper?.getObjectField(backgroundPlayerField?.name)?.callMethod(corePlayerMethod?.name)?.callMethod(seekToMethod, position.toInt())
            }
            lastSeekService = absMusicService
            absMusicService?.callMethod(updateStateMethod?.name, lastState)
        }

        updateStateMethod?.hookBeforeMethod { param ->
            lastState = param.args[0] as Int
            val playerHelper = param.thisObject.getObjectField(playerHelperField?.name)
            when (playerHelper?.javaClass?.name) {
                musicHelper ->
                    playerHelper?.getObjectField(rxMediaPlayerField?.name)?.getObjectField(rxMediaPlayerImplField?.name)?.getObjectField(mediaPlayerField?.name)?.run {
                        position = callMethodAs("getCurrentPosition")
                        speed = callMethodAs("getSpeed", 0.0f)
                    }
                backgroundHelper -> playerHelper?.getObjectField(backgroundPlayerField?.name)?.callMethod(corePlayerMethod?.name)?.run {
                    position = callMethodAs<Int>(getCurrentPositionMethod).toLong()
                    speed = try {
                        callMethodAs(getSpeedMethod, true)
                    } catch (e: Throwable) {
                        callMethodAs(getSpeedMethod)
                    }
                }
            }
            if (duration == position)
                lastSeekService = param.thisObject
        }

        instance.absMusicServiceClass?.hookAfterMethod("onDestroy") {
            duration = 0L
            position = 0L
            speed = 1.0f
            lastSeekService = null
        }

        getFlagMethod?.hookAfterMethod { param ->
            param.result = PlaybackState.ACTION_SEEK_TO or (param.result as Long)
        }

        setPlaybackStateMethod?.hookBeforeMethod { param ->
            param.args[1] = position
            param.args[2] = speed
        }


        instance.musicNotificationHelperClass?.replaceMethod(instance.setNotification(), instance.notificationBuilderClass) {}

        val hooker = fun(param: MethodHookParam) {
            val old = param.result as Notification? ?: return
            val res = currentContext.resources
            val getId = { name: String -> res.getIdentifier(name, "id", packageName) }
            val iconId = getId("icon")
            val text1Id = getId("text1")
            val text2Id = getId("text2")
            val action1Id = getId("action1")
            val action2Id = getId("action2")
            val action3Id = getId("action3")
            val action4Id = getId("action4")
            val stopId = getId("stop")

            if (old.extras.containsKey("Primitive")) return

            @Suppress("DEPRECATION")
            val view = old.bigContentView ?: old.contentView ?: return

            @Suppress("DEPRECATION")
            val actions = view.getObjectFieldAs<ArrayList<Any>>("mActions")
            val buttons = linkedMapOf(
                    action1Id to ActionDesc(),
                    action2Id to ActionDesc(),
                    action3Id to ActionDesc(),
                    action4Id to ActionDesc(),
                    stopId to ActionDesc(),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                param.result = Notification.Builder(param.thisObject.getObjectFieldAs<Service>("a"), old.channelId).run {
                    setSmallIcon(old.smallIcon)
                    setColor(old.color)
                    setUsesChronometer(false)
                    setOngoing(false) // Important
                    setContentIntent(old.contentIntent)
                    setVisibility(Notification.VISIBILITY_PUBLIC)
                    setWhen(System.currentTimeMillis())
                    setCategory(old.category)

                    for (action in actions) {
                        val viewId = action.getIntField("viewId")
                        when (action.javaClass) {
                            bitmapActionClass -> {
                                when (viewId) {
                                    iconId -> setLargeIcon(action.getObjectFieldAs<Bitmap>("bitmap"))
                                }
                            }
                            reflectionActionClass -> {
                                when (action.getObjectFieldAs<String>("methodName")) {
                                    "setText" ->
                                        when (viewId) {
                                            text1Id -> setContentTitle(action.getObjectFieldAs<CharSequence>("value"))
                                            text2Id -> setContentText(action.getObjectFieldAs<CharSequence>("value"))
                                        }
                                    "setImageResource" ->
                                        when (viewId) {
                                            action1Id -> buttons[action1Id]?.icon = action.getObjectFieldAs<Int>("value")
                                            action2Id -> buttons[action2Id]?.icon = action.getObjectFieldAs<Int>("value")
                                            action3Id -> buttons[action3Id]?.icon = action.getObjectFieldAs<Int>("value")
                                            action4Id -> buttons[action4Id]?.icon = action.getObjectFieldAs<Int>("value")
                                            stopId -> buttons[stopId]?.icon = action.getObjectFieldAs<Int>("value")
                                            iconId -> {
                                                val originIcon = BitmapFactory.decodeResource(currentContext.resources, action.getObjectFieldAs("value"))
                                                val largeIcon = originIcon.copy(originIcon.config, true)
                                                largeIcon.eraseColor(old.color)
                                                val canvas = Canvas(largeIcon)
                                                canvas.drawBitmap(originIcon, 0f, 0f, null)
                                                setLargeIcon(largeIcon)
                                                originIcon.recycle()
                                            }
                                        }
                                }
                            }
                            onClickActionClass -> {
                                val response = action.getObjectField("mResponse")
                                val pendingIntent = response?.getObjectFieldAs<PendingIntent?>("mPendingIntent")
                                when (viewId) {
                                    action1Id -> buttons[action1Id]?.intent = pendingIntent
                                    action2Id -> buttons[action2Id]?.intent = pendingIntent
                                    action3Id -> buttons[action3Id]?.intent = pendingIntent
                                    action4Id -> buttons[action4Id]?.intent = pendingIntent
                                    stopId -> buttons[stopId]?.intent = pendingIntent
                                }
                            }
                        }
                    }

                    for (button in buttons) {
                        button.value.title = when (button.key) {
                            action1Id -> "Mode"
                            action2Id -> "Previous"
                            action3Id -> "Pause/Play"
                            action4Id -> "Next"
                            stopId -> "Stop"
                            else -> null
                        }
                    }

                    buttons[stopId]?.icon = res.getIdentifier("sobot_icon_close_normal", "drawable", packageName)
                    var buttonCount = 0
                    for (button in buttons) {
                        button.value.run {
                            icon?.let {
                                @Suppress("DEPRECATION")
                                addAction(it, title, intent)
                                buttonCount += 1
                            }
                        }
                    }
                    val mediaStyle = Notification.MediaStyle().setShowActionsInCompactView(*when (buttonCount) {
                        0 -> intArrayOf()
                        1 -> intArrayOf(0)
                        2 -> intArrayOf(0, 1)
                        3 -> intArrayOf(0, 1, 2)
                        else -> intArrayOf(2, 3, 4)
                    })
                    instance.absMusicService()?.let { f ->
                        instance.mediaSessionToken()?.let { m ->
                            val token = param.thisObject.getObjectField(f)?.callMethod(m)?.getObjectField("a") as MediaSession.Token
                            mediaStyle.setMediaSession(token)
                        }
                    }
                    style = mediaStyle
                    extras.putBoolean("Primitive", true)
                    build()
                }
            }
        }

        instance.musicNotificationHelperClass?.declaredMethods?.filter {
            !Modifier.isStatic(it.modifiers) && it.returnType == Notification::class.java
        }?.forEach {
            it.hookAfterMethod(hooker)
        }
    }
}