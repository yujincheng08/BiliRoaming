package me.iacn.biliroaming.hook

import android.graphics.Rect
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LineBackgroundSpan
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import kotlin.math.roundToInt


class SubtitleHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("custom_subtitle", false)) return
        Log.d("startHook: Subtitle")

        instance.chronosSwitchClass?.hookAfterConstructor { param ->
            param.thisObject.javaClass.declaredFields.forEach {
                if (it.type == Boolean::class.javaObjectType) {
                    param.thisObject.setObjectField(it.name, false)
                }
            }
        }

        val rect = Rect()
        val margin = 3
        val backgroundColor = 0x7f000000
        val backgroundSpan =
            LineBackgroundSpan { canvas, paint, left, right, top, _, bottom, text, start, end, _ ->
                val width = paint.measureText(text, start, end).roundToInt()
                val textLeft = (right - left - width) / 2
                val color = paint.color
                rect.set(textLeft - margin, top, textLeft + width + margin, bottom)
                paint.color = backgroundColor
                canvas.drawRect(rect, paint)
                paint.color = color
            }

        android.text.SpannableString::class.java.hookBeforeMethod(
            "setSpan",
            Object::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java
        ) { param ->
            if (instance.subtitleSpanClass?.isInstance(param.args[0]) != true) return@hookBeforeMethod
            val (start, end, flags) = listOf(
                param.args[1] as Int,
                param.args[2] as Int,
                param.args[3] as Int
            )
            (param.thisObject as SpannableString).run {
                setSpan(
                    ForegroundColorSpan(sPrefs.getInt("subtitle_font_color", 0x7fffffff)),
                    start,
                    end,
                    flags
                )
                setSpan(
                    AbsoluteSizeSpan(sPrefs.getInt("subtitle_font_size", 30), true),
                    start,
                    end,
                    flags
                )
                param.result = null
            }
        }


    }
}
