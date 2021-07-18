package me.iacn.biliroaming.hook

import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import kotlin.math.roundToInt


class SubtitleHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        val backgroundSpan = { backgroundColor: Int ->
            LineBackgroundSpan { canvas, paint, left, right, top, _, bottom, text, start, end, _ ->
                val width = paint.measureText(text, start, end).roundToInt()
                val textLeft = (right + left - width) / 2
                val color = paint.color
                val rect = Rect()
                rect.set(textLeft, top, textLeft + width, bottom)
                paint.color = backgroundColor
                canvas.drawRect(rect, paint)
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
            bgColor: String
        ) {
            val subtitleBlurSolid = blurSolid.toString() + "f"
            subtitle.setSpan(
                ForegroundColorSpan(Color.parseColor("#$fontColor")), start, end, flags
            )
            subtitle.setSpan(
                AbsoluteSizeSpan(fontSize, true),
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
            // should be drawn the last
            subtitle.setSpan(backgroundSpan(Color.parseColor("#$bgColor")), start, end, flags)
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
                    )!!, sPrefs.getInt("subtitle_font_size", 30),
                     sPrefs.getString(
                            "subtitle_background_color",
                            "20000000"
                    )!!
                )
                param.result = null
            }
        }
    }
}
