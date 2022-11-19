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
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import java.lang.reflect.Modifier


class MusicNotificationHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    class ActionDesc(
        var icon: Int? = null,
        var title: CharSequence? = null,
        var intent: PendingIntent? = null
    )

    private val iconId = getId("icon")
    private val notificationIconId = getId("notification_icon")
    private val text1Id = getId("text1")
    private val text2Id = getId("text2")
    private val text3Id = getId("text3")
    private val notificationText1Id = getId("notification_text1")
    private val notificationText2Id = getId("notification_text2")
    private val notificationText3Id = getId("notification_text3")
    private val action1Id = getId("action1")
    private val action2Id = getId("action2")
    private val action3Id = getId("action3")
    private val action4Id = getId("action4")
    private val notificationAction1Id = getId("notification_action1")
    private val notificationAction2Id = getId("notification_action2")
    private val notificationAction3Id = getId("notification_action3")
    private val notificationAction4Id = getId("notification_action4")
    private val stopId = getId("stop")
    private val notificationStopId = getId("notification_stop")

    private val liveNotificationTitleId = getId("live_notification_title")
    private val liveNotificationSubtitleId = getId("live_notification_subtitle")
    private val liveNotificationUpNameId = getId("live_notification_up_name")
    private val liveNotificationStopId = getId("live_notification_stop")
    private val liveNotificationIconId = getId("live_notification_icon")

    private val bitmapActionClass by Weak {
        "android.widget.RemoteViews.BitmapReflectionAction".findClass(
            mClassLoader
        )
    }
    private val reflectionActionClass by Weak {
        "android.widget.RemoteViews.ReflectionAction".findClass(
            mClassLoader
        )
    }
    private val onClickActionClass by Weak {
        "android.widget.RemoteViews.SetOnClickResponse".findClass(
            mClassLoader
        )
    }
    private val mediaMetadataClass by Weak {
        "android.support.v4.media.MediaMetadataCompat".findClass(
            mClassLoader
        )
    }

    private var position = 0L
    private var speed = 1f
    private var duration = 0L
    private var lastState = 0
    private var absMusicService: Any? = null

    private val getFlagMethod by lazy {
        instance.absMusicServiceClass?.declaredMethods?.firstOrNull {
            it.parameterTypes.isEmpty() &&
                    it.returnType == Long::class.javaPrimitiveType
        }
    }

    private val updateMetadataMethod by lazy {
        instance.absMusicServiceClass?.declaredMethods?.firstOrNull {
            it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Boolean::class.javaPrimitiveType
        }
    }

    private val notificationServiceField by lazy {
        instance.absMusicServiceClass?.declaredFields?.firstOrNull {
            instance.backgroundPlayerClass?.interfaces?.contains(it.type) == true
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

    private val playbackStateBuilderClass by lazy {
        val playbackStateClass =
            "android.support.v4.media.session.PlaybackStateCompat".findClassOrNull(mClassLoader)
        playbackStateClass?.declaredClasses?.firstOrNull { c ->
            c.declaredMethods.any {
                it.returnType == playbackStateClass && it.parameterTypes.isEmpty()
            }
        }
    }

    private val setPlaybackStateMethod by lazy {
        playbackStateBuilderClass?.declaredMethods?.firstOrNull {
            it.parameterTypes.size == 4
        }
    }

    private val playerServiceField by lazy {
        instance.backgroundPlayerClass?.declaredFields?.firstOrNull {
            it.type == instance.playerServiceClass
        }
    }

    private val corePlayerMethod by lazy {
        instance.playerServiceClass?.declaredMethods?.firstOrNull {
            instance.playerCoreServiceV2Class?.interfaces?.contains(it.returnType) ?: false
        }
    }

    private val getDurationMethod by lazy {
        try {
            instance.playerCoreServiceV2Class?.getDeclaredMethod("getDuration")?.name
        } catch (e: Throwable) {
            "i"
        }
    }

    private val getCurrentPositionMethod by lazy {
        try {
            instance.playerCoreServiceV2Class?.getDeclaredMethod("getCurrentPosition")?.name
        } catch (e: Throwable) {
            "j"
        }
    }

    private val setStateMethod by lazy {
        instance.absMusicServiceClass?.runCatchingOrNull {
            getDeclaredMethod(
                instance.setState()!!,
                Int::class.javaPrimitiveType
            ).also { it.isAccessible = true }
        }
    }

    private fun getCorePlayer(absMusicService: Any?) =
        absMusicService?.getObjectField(notificationServiceField?.name)?.runCatchingOrNull {
            when (javaClass) {
                instance.backgroundPlayerClass -> getObjectField(playerServiceField?.name)
                instance.musicBackgroundPlayerClass -> getObjectField(instance.musicWrapperPlayer())
                    ?.getObjectField(instance.musicPlayer())
                    ?.getObjectField(instance.musicPlayerService())
                else -> null
            }?.callMethod(corePlayerMethod?.name)
        }

    override fun startHook() {
        if (!sPrefs.getBoolean("music_notification", false)) return

        Log.d("startHook: MusicNotification")

        updateMetadataMethod?.hookBeforeMethod { param ->
            metadataField?.isAccessible = true
            val bundle = metadataField?.get(param.thisObject)
                ?.getObjectFieldAs<Bundle>(metadataBundleField?.name)
                ?: return@hookBeforeMethod
            val currentDuration = bundle.getLong(MediaMetadata.METADATA_KEY_DURATION)
            if (currentDuration != 0L) return@hookBeforeMethod
            param.args[0] = true
            duration = getCorePlayer(param.thisObject)?.callMethodAs<Long>(getDurationMethod) ?: 0L
            bundle.putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
        }

        instance.playerOnSeekCompleteClass?.hookBeforeAllMethods(
            instance.onSeekComplete()
        ) {
            absMusicService?.let { s -> setStateMethod?.let { m -> m(s, lastState) } }
        }

        instance.mediaSessionCallbackClass?.hookAfterMethod(
            "onSeekTo",
            Long::class.javaPrimitiveType
        ) { param ->
            position = param.args[0] as Long
            getCorePlayer(absMusicService)?.callMethod(instance.seekTo(), position.toInt())
            absMusicService?.let { s -> setStateMethod?.let { m -> m(s, lastState) } }
        }

        setStateMethod?.hookBeforeMethod { param ->
            lastState = param.args[0] as Int
            getCorePlayer(param.thisObject)?.run {
                position = callMethodAs<Int>(getCurrentPositionMethod).toLong()
                speed = try {
                    callMethodAs(instance.defaultSpeed(), true)
                } catch (e: Throwable) {
                    callMethodAs(instance.defaultSpeed())
                }
            }
        }

        instance.absMusicServiceClass?.hookAfterAllConstructors { param ->
            absMusicService = param.thisObject
        }

        instance.absMusicServiceClass?.hookAfterMethod("onDestroy") {
            duration = 0L
            position = 0L
            speed = 1.0f
            absMusicService = null
        }

        getFlagMethod?.hookAfterMethod { param ->
            param.result = PlaybackState.ACTION_SEEK_TO or (param.result as Long)
        }

        setPlaybackStateMethod?.hookBeforeMethod { param ->
            param.args[1] = position
            param.args[2] = speed
        }


        instance.musicNotificationHelperClass?.replaceMethod(
            instance.setNotification(),
            instance.notificationBuilderClass
        ) {}

        val hooker: Hooker = fun(param: MethodHookParam) {
            val old = param.result as? Notification ?: return

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
                val serviceField = param.thisObject.javaClass.declaredFields.firstOrNull {
                    it.type.superclass == Service::class.java
                } ?: return
                val sessionField = serviceField.type.declaredFields.firstOrNull {
                    it.type.name.endsWith("MediaSessionCompat")
                } ?: return
                val tokenMethod = sessionField.type.declaredMethods.firstOrNull {
                    it.returnType.name.endsWith("Token")
                } ?: return
                param.result = Notification.Builder(
                    param.thisObject.getObjectFieldAs<Service>(serviceField.name),
                    old.channelId
                ).run {
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
                                    iconId, notificationIconId -> setLargeIcon(
                                        action.getObjectFieldAs<Bitmap>(
                                            "bitmap"
                                        )
                                    )
                                }
                            }
                            reflectionActionClass -> {
                                when (action.getObjectFieldAs<String>("methodName")) {
                                    "setText" ->
                                        when (viewId) {
                                            text1Id, notificationText1Id, liveNotificationTitleId -> setContentTitle(
                                                action.getObjectFieldAs<CharSequence>("value")
                                            )
                                            text2Id, notificationText2Id, liveNotificationSubtitleId -> setContentText(
                                                action.getObjectFieldAs<CharSequence>("value")
                                            )
                                            text3Id, notificationText3Id, liveNotificationUpNameId -> setSubText(
                                                action.getObjectFieldAs<CharSequence>("value")
                                            )
                                            else -> Log.w("Unknown viewId $viewId for setText")
                                        }
                                    "setImageResource" ->
                                        when (viewId) {
                                            action1Id, notificationAction1Id -> buttons[action1Id]?.icon =
                                                action.getObjectFieldAs<Int>("value")
                                            action2Id, notificationAction2Id -> buttons[action2Id]?.icon =
                                                action.getObjectFieldAs<Int>("value")
                                            action3Id, notificationAction3Id -> buttons[action3Id]?.icon =
                                                action.getObjectFieldAs<Int>("value")
                                            action4Id, notificationAction4Id -> buttons[action4Id]?.icon =
                                                action.getObjectFieldAs<Int>("value")
                                            stopId, notificationStopId -> buttons[stopId]?.icon =
                                                action.getObjectFieldAs<Int>("value")
                                            iconId, notificationIconId, liveNotificationIconId -> {
                                                val originIcon = BitmapFactory.decodeResource(
                                                    currentContext.resources,
                                                    action.getObjectFieldAs("value")
                                                )
                                                val largeIcon =
                                                    originIcon.copy(originIcon.config, true)
                                                largeIcon.eraseColor(old.color)
                                                val canvas = Canvas(largeIcon)
                                                canvas.drawBitmap(originIcon, 0f, 0f, null)
                                                setLargeIcon(largeIcon)
                                                originIcon.recycle()
                                            }
                                            else -> Log.w("Unknown viewId $viewId for setImageReousrce")
                                        }
                                }
                            }
                            onClickActionClass -> {
                                val response = action.getObjectField("mResponse")
                                val pendingIntent =
                                    response?.getObjectFieldAs<PendingIntent?>("mPendingIntent")
                                when (viewId) {
                                    action1Id, notificationAction1Id -> buttons[action1Id]?.intent =
                                        pendingIntent
                                    action2Id, notificationAction2Id -> buttons[action2Id]?.intent =
                                        pendingIntent
                                    action3Id, notificationAction3Id -> buttons[action3Id]?.intent =
                                        pendingIntent
                                    action4Id, notificationAction4Id -> buttons[action4Id]?.intent =
                                        pendingIntent
                                    stopId, notificationStopId, liveNotificationStopId -> buttons[stopId]?.intent =
                                        pendingIntent
                                    else -> Log.w("Unknown viewId $viewId for onClick")
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

                    buttons[stopId]?.icon = getId("sobot_icon_close_normal")
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
                    val mediaStyle = Notification.MediaStyle().setShowActionsInCompactView(
                        *when (buttonCount) {
                            0 -> intArrayOf()
                            1 -> intArrayOf(0)
                            2 -> intArrayOf(0, 1)
                            3 -> intArrayOf(0, 1, 2)
                            4 -> intArrayOf(1, 2, 3)
                            else -> intArrayOf(2, 3, 4)
                        }
                    )
                    val token = param.thisObject.getObjectField(serviceField.name)
                        ?.getObjectField(sessionField.name)
                        ?.callMethod(tokenMethod.name)?.run {
                            javaClass.declaredFields.firstNotNullOfOrNull {
                                getObjectField(it.name) as? MediaSession.Token
                            }
                        }
                    token?.let { mediaStyle.setMediaSession(it) }
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

        instance.liveNotificationHelperClass?.declaredMethods?.filter {
            !Modifier.isStatic(it.modifiers) && it.returnType == Notification::class.java
        }?.forEach {
            it.hookAfterMethod(hooker)
        }
    }
}
