package me.iacn.biliroaming.hook

import android.app.Notification
import android.app.PendingIntent
import android.graphics.Bitmap
import de.robv.android.xposed.XC_MethodHook
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.BiliBiliPackage.Weak
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.*


class MusicNotificationHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    class ActionDesc(var icon: Int? = null, var title: CharSequence? = null, var intent: PendingIntent? = null)

    private val bitmapActionClass by Weak { "android.widget.RemoteViews.BitmapReflectionAction".findClass(mClassLoader) }
    private val reflectionActionClass by Weak { "android.widget.RemoteViews.ReflectionAction".findClass(mClassLoader) }
    private val onClickActionClass by Weak { "android.widget.RemoteViews.SetOnClickResponse".findClass(mClassLoader) }

    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("music_notification", false)) return

        Log.d("startHook: MusicNotification")
        val hooker = fun(param: XC_MethodHook.MethodHookParam) {
            val old = param.result as Notification? ?: return
            if (old.extras.containsKey("Primitive")) return
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

            @Suppress("DEPRECATION")
            old.bigContentView ?: return

            @Suppress("DEPRECATION")
            val actions = old.bigContentView.getObjectFieldAs<ArrayList<Any>>("mActions")
            val buttons = linkedMapOf(
                    action1Id to ActionDesc(),
                    action2Id to ActionDesc(),
                    action3Id to ActionDesc(),
                    action4Id to ActionDesc(),
                    stopId to ActionDesc(),
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                param.result = Notification.Builder(XposedInit.currentContext, old.channelId).run {
                    setSmallIcon(old.smallIcon)
                    setColor(old.color)
                    setOnlyAlertOnce(true)
                    setShowWhen(false)
                    setOngoing(true)
                    setUsesChronometer(false)
                    setContentIntent(old.contentIntent)
                    style = Notification.MediaStyle().setShowActionsInCompactView(1, 2, 3)

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

                    for (button in buttons) {
                        button.value.run {
                            @Suppress("DEPRECATION")
                            addAction(icon ?: 0, title, intent)
                        }
                    }
                    extras.putBoolean("Primitive", true)
                    build()
                }
            }
        }

        instance.createNotification()?.split(";")?.forEach {
            instance.musicNotificationHelperClass?.hookAfterMethod(it, Bitmap::class.java, hooker = hooker)
        }
    }
}