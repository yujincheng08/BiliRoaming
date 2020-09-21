package me.iacn.biliroaming.hook

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.graphics.Bitmap
import android.media.session.MediaSession
import android.os.Build
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

    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("music_notification", false)) return

        Log.d("startHook: MusicNotification")

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
}