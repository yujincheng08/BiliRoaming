package me.iacn.biliroaming.hook

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.session.MediaSession
import android.os.Build
import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.BiliBiliPackage.Weak
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.*
import java.lang.reflect.Modifier


class MusicNotificationHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    class ActionDesc(var icon: Int? = null, var title: CharSequence? = null, var intent: PendingIntent? = null)

    private val bitmapActionClass by Weak { "android.widget.RemoteViews.BitmapReflectionAction".findClass(mClassLoader) }
    private val reflectionActionClass by Weak { "android.widget.RemoteViews.ReflectionAction".findClass(mClassLoader) }
    private val onClickActionClass by Weak { "android.widget.RemoteViews.SetOnClickResponse".findClass(mClassLoader) }

    private var position = 0L
    private var speed = 1f
    private var lastState = 0


    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("music_notification", false)) return

        Log.d("startHook: MusicNotification")

        "com.bilibili.opd.app.bizcommon.mediaplayer.rx.c1".hookAfterMethod(mClassLoader, "l0") { param ->
            // trick to notify notification to change speed
            param.thisObject.callMethod("toggle")
            param.thisObject.callMethod("toggle")
        }

        "tv.danmaku.bili.ui.player.notification.AbsMusicService".hookBeforeMethod(mClassLoader, "u", Boolean::class.javaPrimitiveType) { param ->
            val playerHelper = param.thisObject.getObjectField("f")
            val bundle = param.thisObject.getObjectField("e")?.getObjectField("a") as Bundle?
                    ?: return@hookBeforeMethod

            val currentDuration = bundle.getLong(DURATION_KEY)
            if (currentDuration != 0L) return@hookBeforeMethod
            param.args[0] = true
            val duration = when (playerHelper?.javaClass?.name) {
                "com.bilibili.music.app.base.mediaplayer.p0" ->
                    playerHelper.getObjectField("b")?.callMethodAs<Long>("getDuration")!!
                "tv.danmaku.biliplayerv2.service.business.background.d" ->
                    playerHelper.getObjectField("e")?.callMethod("w")?.callMethodAs<Int>("getDuration")?.toLong()!!
                else -> 0L
            }
            bundle.putLong(DURATION_KEY, duration)
        }

        "android.support.v4.media.session.MediaSessionCompat\$c\$b".hookAfterMethod(mClassLoader, "onSeekTo", Long::class.javaPrimitiveType) { param ->
            position = param.args[0] as Long
            val absMusicService = param.thisObject.getObjectField("a")?.getObjectField("e")
            val playerHelper = absMusicService?.getObjectField("f")
            when (playerHelper?.javaClass?.name) {
                "com.bilibili.music.app.base.mediaplayer.p0" ->
                    playerHelper.getObjectField("b")?.callMethod("seekTo", position)
                "tv.danmaku.biliplayerv2.service.business.background.d" -> playerHelper.getObjectField("e")?.callMethod("w")?.callMethod("seekTo", position.toInt())
            }
            absMusicService?.callMethod("v", lastState)
        }
        "tv.danmaku.bili.ui.player.notification.AbsMusicService".hookBeforeMethod(mClassLoader, "v", Int::class.javaPrimitiveType) { param ->
            lastState = param.args[0] as Int
            val playerHelper = param.thisObject.getObjectField("f")
            when (playerHelper?.javaClass?.name) {
                "com.bilibili.music.app.base.mediaplayer.p0" ->
                    playerHelper.getObjectField("b")?.getObjectField("q")?.callMethod("h")?.run {
                        position = callMethodAs("getCurrentPosition")
                        speed = callMethodAs("getSpeed", 0.0f)
                    }
                "tv.danmaku.biliplayerv2.service.business.background.d" -> playerHelper.getObjectField("e")?.callMethod("w")?.run {
                    position = callMethodAs<Int>("getCurrentPosition").toLong()
                    speed = callMethodAs("q0", true)
                }
            }
        }

        "tv.danmaku.bili.ui.player.notification.AbsMusicService".hookAfterMethod(mClassLoader, "d") { param ->
            param.result = (1L shl 8) or (param.result as Long)
        }

        "android.support.v4.media.session.PlaybackStateCompat\$b".hookBeforeMethod(mClassLoader, "c", Int::class.javaPrimitiveType, Long::class.javaPrimitiveType, Float::class.javaPrimitiveType, Long::class.javaPrimitiveType) { param ->
            param.args[1] = position
            param.args[2] = speed
        }


        instance.musicNotificationHelperClass?.replaceMethod(instance.setNotification(), instance.notificationBuilderClass) {}

        val hooker = fun(param: MethodHookParam) {
            val old = param.result as Notification? ?: return
            val res = XposedInit.currentContext.resources
            val getId = { name: String -> res.getIdentifier(name, "id", XposedInit.currentContext.packageName) }
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
                                                val originIcon = BitmapFactory.decodeResource(XposedInit.currentContext.resources, action.getObjectFieldAs("value"))
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

                    buttons[stopId]?.icon = res.getIdentifier("sobot_icon_close_normal", "drawable", XposedInit.currentContext.packageName)
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
                        else -> intArrayOf(1, 2, 3)
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

    companion object {
        private const val DURATION_KEY = "android.media.metadata.DURATION"
    }
}