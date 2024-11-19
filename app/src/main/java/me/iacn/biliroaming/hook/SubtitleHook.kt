package me.iacn.biliroaming.hook

import android.graphics.*
import android.net.Uri
import android.text.Layout
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.LineBackgroundSpan
import android.text.style.MaskFilterSpan
import android.text.style.StyleSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import me.iacn.biliroaming.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.hook.BangumiSeasonHook.Companion.lastSeasonInfo
import me.iacn.biliroaming.hook.ProtoBufHook.Companion.removeCmdDms
import me.iacn.biliroaming.network.BiliRoamingApi
import me.iacn.biliroaming.utils.*
import org.json.JSONArray
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.math.ceil
import kotlin.math.roundToInt


class SubtitleHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        val fontFile by lazy { File(currentContext.getExternalFilesDir(null), "subtitle.font") }

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

    private val hidden = sPrefs.getBoolean("hidden", false)
    private val removeCmdDms = sPrefs.getBoolean("remove_video_cmd_dms", false)

    private val mainFunc by lazy { sPrefs.getBoolean("main_func", false) }
    private val generateSubtitle by lazy { sPrefs.getBoolean("auto_generate_subtitle", false) }
    private val addCloseSubtitle by lazy { mainFunc && getVersionCode(packageName) >= 6750300 }
    private val customSubtitle by lazy { sPrefs.getBoolean("custom_subtitle", false) }
    private val removeBg by lazy { sPrefs.getBoolean("subtitle_remove_bg", true) }
    private val boldText by lazy { sPrefs.getBoolean("subtitle_bold", true) }
    private val fontSizePortrait by lazy { sPrefs.getInt("subtitle_font_size_portrait", 0).sp }
    private val fontSizeLandscape by lazy { sPrefs.getInt("subtitle_font_size_landscape", 0).sp }
    private val fillColor by lazy {
        sPrefs.getString("subtitle_font_color2", null)
            ?.runCatchingOrNull { Color.parseColor("#$this") } ?: Color.WHITE
    }
    private val strokeColor by lazy {
        sPrefs.getString("subtitle_stroke_color", null)
            ?.runCatchingOrNull { Color.parseColor("#$this") } ?: Color.BLACK
    }
    private val strokeWidth by lazy {
        sPrefs.getFloat("subtitle_stroke_width", 5.0F)
    }
    private val offset by lazy { sPrefs.getInt("subtitle_offset", 0) }

    private val closeText =
        currentContext.getString(getResId("Player_option_subtitle_lan_doc_nodisplay", "string"))

    private var subtitleFont: Typeface? = null

    override fun startHook() {
        if (customSubtitle)
            if (instance.cronCanvasClass == null) {
                hookSubtitleStyle()
            } else {
                hookSubtitleStyleNew()
            }
        if (mainFunc || generateSubtitle)
            hookSubtitleList()

        hookDmViewAsync()
    }

    private fun hookDmViewAsync() {
        if (!(mainFunc || generateSubtitle || (hidden && removeCmdDms))) return
        instance.dmMossClass?.hookBeforeMethod(
            "dmView",
            instance.dmViewReqClass,
            instance.mossResponseHandlerClass
        ) { param ->
            val dmViewReq = param.args[0]
            param.args[1] = param.args[1].mossResponseHandlerReplaceProxy { dmViewReply ->
                if (hidden && removeCmdDms) {
                    dmViewReply?.removeCmdDms()
                }
                if (mainFunc || generateSubtitle) {
                    dmViewReply.hookSubtitleList(dmViewReq)
                } else null
            }
        }
    }

    private fun hookSubtitleStyle() {
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

    private fun hookSubtitleStyleNew() {
        if (offset != 0) {
            arrayOf(instance.subtitleConfigGetClass, instance.subtitleConfigChangeClass).forEach {
                it?.hookBeforeMethod("setBottomMargin", Float::class.javaObjectType) { param ->
                    param.args[0] = (param.args[0] as Float) + offset
                }
            }
        }
        val cronCanvasClass = instance.cronCanvasClass ?: return
        if (removeBg) {
            cronCanvasClass.replaceMethod(
                "drawPath",
                Path::class.java,
                Boolean::class.javaPrimitiveType
            ) { null }
        }
        val paintField = cronCanvasClass.getDeclaredField("paint")
            .apply { isAccessible = true }
        val fillColorField = cronCanvasClass.getDeclaredField("fillColor")
            .apply { isAccessible = true }
        val strokeColorField = cronCanvasClass.getDeclaredField("strokeColor")
            .apply { isAccessible = true }
        val maxWidthField = cronCanvasClass.getDeclaredField("maxWidth")
            .apply { isAccessible = true }
        val staticLayoutField = cronCanvasClass.getDeclaredField("staticLayout")
            .apply { isAccessible = true }
        val alignmentField = cronCanvasClass.getDeclaredField("alignment")
            .apply { isAccessible = true }
        val measureTextFromLayoutMethod = cronCanvasClass.getDeclaredMethod(
            "measureTextFromLayout", StaticLayout::class.java
        ).apply { isAccessible = true }
        MainScope().launch(Dispatchers.IO) {
            subtitleFont = if (fontFile.isFile) {
                Typeface.createFromFile(fontFile)
            } else null
        }
        cronCanvasClass.hookBeforeMethod(
            "measureTextImpl",
            String::class.java
        ) { param ->
            val cronCanvas = param.thisObject
            val maxWidth = maxWidthField.getFloat(cronCanvas)
            if (maxWidth != 0.0F) {
                val paint = paintField.get(cronCanvas) as TextPaint
                paint.strokeWidth = strokeWidth
                paint.isFakeBoldText = boldText
                subtitleFont?.let { paint.typeface = it }
                if (currentIsLandscape && fontSizeLandscape > 0) {
                    paint.textSize = fontSizeLandscape.toFloat()
                } else if (!currentIsLandscape && fontSizePortrait > 0) {
                    paint.textSize = fontSizePortrait.toFloat()
                }
                val text = param.args[0] as String
                val alignment = alignmentField.get(cronCanvas) as Layout.Alignment
                val staticLayout = staticLayoutField.get(cronCanvas) as? StaticLayout
                if (staticLayout != null && staticLayout.text == text) {
                    param.result = measureTextFromLayoutMethod(null, staticLayout)
                } else {
                    val wantWidth = if (maxWidth <= 0.0F) Int.MAX_VALUE
                    else maxWidth.toInt().coerceAtMost(Int.MAX_VALUE)
                    var layout = StaticLayout.Builder
                        .obtain(text, 0, text.length, paint, wantWidth)
                        .setAlignment(alignment)
                        .setIncludePad(false)
                        .build()
                    val lineMaxWidth = IntRange(0, layout.lineCount - 1).maxOfOrNull {
                        ceil(layout.getLineWidth(it)).toInt()
                    } ?: 0
                    layout = StaticLayout.Builder
                        .obtain(text, 0, text.length, paint, lineMaxWidth)
                        .setAlignment(alignment)
                        .setIncludePad(false)
                        .build()
                    staticLayoutField.set(cronCanvas, layout)
                    param.result = measureTextFromLayoutMethod(null, layout)
                }
            }
        }
        cronCanvasClass.hookBeforeMethod(
            "drawText",
            String::class.java,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        ) { param ->
            val cronCanvas = param.thisObject
            val maxWidth = maxWidthField.getFloat(cronCanvas)
            if (maxWidth != 0.0F) {
                fillColorField.setInt(cronCanvas, fillColor)
                strokeColorField.setInt(cronCanvas, strokeColor)
                param.args[3] = true
                param.invokeOriginalMethod()
                param.args[3] = false
            }
        }
    }

    private fun hookSubtitleList() {
        instance.dmMossClass?.hookAfterMethod(
            if (instance.useNewMossFunc) "executeDmView" else "dmView",
            instance.dmViewReqClass,
        ) { param ->
            param.result.hookSubtitleList(param.args[0])?.let { param.result = it }
        }

        if (!generateSubtitle) return
        instance.biliCallClass?.hookBeforeMethod(
            instance.setParser(), instance.parserClass
        ) { param ->
            val url = param.thisObject.getObjectField(instance.biliCallRequestField())
                ?.getObjectField(instance.urlField())?.toString()
            if (url?.contains("zh_converter=t2cn") != true)
                return@hookBeforeMethod
            val parser = param.args[0]
            param.args[0] = Proxy.newProxyInstance(
                parser.javaClass.classLoader,
                arrayOf(instance.parserClass)
            ) { _, m, args ->
                val dictReady = if (!SubtitleHelper.dictExist) {
                    SubtitleHelper.downloadDict()
                } else true
                val converted = if (dictReady) {
                    runCatching {
                        val responseText = args[0].callMethodAs<String>(instance.string())
                        SubtitleHelper.convert(responseText)
                    }.onFailure {
                        Log.e(it)
                    }.getOrNull()
                        ?: SubtitleHelper.errorResponse(XposedInit.moduleRes.getString(R.string.subtitle_convert_failed))
                } else SubtitleHelper.errorResponse(XposedInit.moduleRes.getString(R.string.subtitle_dict_download_failed))

                val mediaType = instance.mediaTypeClass
                    ?.callStaticMethod(
                        instance.get(),
                        "application/json; charset=UTF-8"
                    ) ?: return@newProxyInstance m(parser, *args)
                val responseBody = instance.responseBodyClass
                    ?.callStaticMethod(
                        instance.create(),
                        mediaType,
                        converted
                    ) ?: return@newProxyInstance m(parser, *args)
                m(parser, responseBody)
            }
        }
    }

    private fun Any?.hookSubtitleList(originalReq: Any): Any? {
        val originalReply = this
        val parseDmViewReply = { r: Any? ->
            r?.let { DmViewReply.parseFrom(it.callMethodAs<ByteArray>("toByteArray")) }
        }

        val extraSubtitles = mutableListOf<SubtitleItem>()
        val oid = originalReq.callMethod("getOid").toString()
        val tryThailand = lastSeasonInfo.containsKey(oid) && ((
                lastSeasonInfo.containsKey("area")
                        && lastSeasonInfo["area"] == "th") ||
                (lastSeasonInfo.containsKey("watch_platform")
                        && lastSeasonInfo["watch_platform"] == "1"
                        && (originalReply == null || originalReply.callMethod("getSubtitle")
                    ?.callMethod("getSubtitlesCount") == 0)))
        if (mainFunc && tryThailand) {
            val subtitles = if (lastSeasonInfo.containsKey("sb$oid")) {
                JSONArray(lastSeasonInfo["sb$oid"])
            } else {
                val result = BiliRoamingApi.getThailandSubtitles(
                    lastSeasonInfo[oid] ?: lastSeasonInfo["epid"]
                )?.toJSONObject()
                if (result != null && result.optInt("code") == 0) {
                    result.optJSONObject("data")
                        ?.optJSONArray("subtitles").orEmpty()
                } else JSONArray()
            }
            if (subtitles.length() != 0) {
                extraSubtitles += subtitles.toSubtitles()
            }
            // Disable danmaku for Area Th
            originalReply?.run {
                callMethod("setClosed", true)
                callMethod("setInputPlaceholder", "泰区不支持")
            }
        }

        val fromPGC = originalReq.callMethodAs<String>("getSpmid").contains("pgc")
        val dmViewReply = if (generateSubtitle || (addCloseSubtitle && fromPGC)) {
            parseDmViewReply(originalReply)
        } else null
        if (generateSubtitle) {
            val subtitles = mutableListOf<SubtitleItem>()
            dmViewReply?.subtitle?.subtitlesList?.let { subtitles += it }
            subtitles += extraSubtitles
            if (subtitles.map { it.lan }.let { "zh-Hant" in it && "zh-CN" !in it }) {
                val origSub = subtitles.first { it.lan == "zh-Hant" }
                val targetSubUrl = Uri.parse(origSub.subtitleUrl).buildUpon()
                    .appendQueryParameter("zh_converter", "t2cn")
                    .build().toString()

                subtitleItem {
                    lan = "zh-CN"
                    lanDoc = "简中（生成）"
                    lanDocBrief = "简中"
                    subtitleUrl = targetSubUrl
                    id = origSub.id + 1
                    idStr = id.toString()
                }.let { extraSubtitles += it }
            }
        }

        if (addCloseSubtitle && fromPGC
            && (!dmViewReply?.subtitle?.subtitlesList.isNullOrEmpty() || extraSubtitles.isNotEmpty())
        ) {
            subtitleItem {
                lan = "nodisplay"
                lanDoc = closeText
            }.let { extraSubtitles += it }
        }

        if (extraSubtitles.isNotEmpty()) {
            val newRes = (dmViewReply ?: parseDmViewReply(originalReply)
            ?: dmViewReply {}).copy {
                subtitle = subtitle.copy {
                    subtitles += extraSubtitles
                }
                d = tryThailand
                inputPlaceHolder = "泰区不支持"
            }
            return originalReply?.javaClass?.callStaticMethod("parseFrom", newRes.toByteArray())
        }

        return null
    }

    private fun JSONArray.toSubtitles(): List<SubtitleItem> {
        val subList = mutableListOf<SubtitleItem>()
        for (subtitle in this) {
            subtitleItem {
                id = subtitle.optLong("id")
                idStr = subtitle.optLong("id").toString()
                subtitleUrl = subtitle.optString("url")
                lan = subtitle.optString("key")
                lanDoc = subtitle.optString("title")
            }.let { subList.add(it) }
        }
        return subList
    }
}
