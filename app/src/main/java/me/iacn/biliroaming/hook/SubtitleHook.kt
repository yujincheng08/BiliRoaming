package me.iacn.biliroaming.hook

import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.LineBackgroundSpan
import android.text.style.MaskFilterSpan
import android.text.style.StyleSpan
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import kotlin.math.roundToInt


class SubtitleHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        val backgroundSpan = { backgroundColor: Int, textSize: Int ->
            LineBackgroundSpan { canvas, paint, left, right, top, _, bottom, text, start, end, _ ->
                val ts = paint.textSize
                paint.textSize = textSize.toFloat()
                val width = paint.measureText(text, start, end).roundToInt()
                val textLeft = (right + left - width) / 2
                val color = paint.color
                val rect = Rect()
                rect.set(textLeft, top, textLeft + width, bottom)
                paint.color = backgroundColor
                canvas.drawRect(rect, paint)
                paint.textSize = ts
                paint.color = color
            }
        }

        fun subtitleStylizeRunner(
            subtitle: SpannableString,
            start: Int,
            end: Int,
            flags: Int,
            blurSolid: Int,
            fontColor: String,
            fontSize: Int,
            bgColor: String,
            strokeColor: String,
            strokeWidth: Float,
            fixBreak: Boolean
        ) {
            val subtitleBlurSolid = blurSolid.toString() + "f"
            val fc = Color.parseColor("#$fontColor")
            val sc = Color.parseColor("#$strokeColor")
            if (fixBreak)
                (start until end).forEach { i ->
                    subtitle.setSpan(
                        StrokeSpan(fc, sc, strokeWidth),
                        i,
                        i + 1,
                        flags
                    )
                }
            else
                subtitle.setSpan(StrokeSpan(fc, sc, strokeWidth), start, end, flags)
            subtitle.setSpan(
                AbsoluteSizeSpan(fontSize, false),
                start,
                end,
                flags
            )
            subtitle.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                end,
                flags
            )
            if (blurSolid != 0) {
                subtitle.setSpan(
                    MaskFilterSpan(
                        BlurMaskFilter(
                            subtitleBlurSolid.toFloat(),
                            BlurMaskFilter.Blur.SOLID
                        )
                    ),
                    start,
                    end,
                    flags
                )
            }
            // should be drawn the last
            subtitle.setSpan(
                backgroundSpan(Color.parseColor("#$bgColor"), fontSize),
                start,
                end,
                flags
            )
        }
    }

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
                subtitleStylizeRunner(
                    this, start, end, flags,
                    sPrefs.getInt("subtitle_blur_solid", 1),
                    sPrefs.getString(
                        "subtitle_font_color2",
                        "FFFFFFFF"
                    )!!,
                    sPrefs.getInt("subtitle_font_size", 30),
                    sPrefs.getString(
                        "subtitle_background_color",
                        "20000000"
                    )!!,
                    sPrefs.getString("subtitle_stroke_color", "00000000")!!,
                    sPrefs.getFloat("subtitle_stroke_width", 0F),
                    sPrefs.getBoolean("subtitle_fix_break", false)
                )
                param.result = null
            }
        }
    }
}
