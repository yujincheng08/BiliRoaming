package me.iacn.biliroaming.hook

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.documentfile.provider.DocumentFile
import de.robv.android.xposed.callbacks.XCallback
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import me.iacn.biliroaming.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import me.iacn.biliroaming.utils.SubtitleHelper.convertToSrt
import me.iacn.biliroaming.utils.SubtitleHelper.reSort
import me.iacn.biliroaming.utils.SubtitleHelper.removeSubAppendedInfo
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class VideoSubtitleHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private val fakeConvertApi = "https://subtitle.biliroaming.114514"
    private val convertApi = "https://www.kofua.top/bsub/%s"
    private val useLocalDict = true

    override fun startHook() {
        if (sPrefs.getBoolean("main_func", false))
            enableSubtitleDownloadHook()
        if (!sPrefs.getBoolean("auto_generate_subtitle", false)) return

        "com.bapis.bilibili.community.service.dm.v1.DMMoss".from(mClassLoader)
            ?.hookAfterMethodWithPriority(
                "dmView",
                XCallback.PRIORITY_HIGHEST,
                "com.bapis.bilibili.community.service.dm.v1.DmViewReq",
            ) { param ->
                val dmViewReply = param.result?.let {
                    API.DmViewReply.parseFrom(
                        it.callMethodAs<ByteArray>("toByteArray")
                    )
                } ?: return@hookAfterMethodWithPriority
                val subtitles = dmViewReply.subtitle.subtitlesList
                if (subtitles.isEmpty()) return@hookAfterMethodWithPriority
                val lanCodes = subtitles.map { it.lan }
                val genCN = "zh-Hant" in lanCodes && "zh-CN" !in lanCodes
                val origin = if (genCN) "zh-Hant" else ""
                val converter = if (genCN) "t2cn" else ""
                val target = if (genCN) "zh-CN" else ""
                val targetDoc = if (genCN) "简中（生成）" else ""
                val targetDocBrief = if (genCN) "简中" else ""
                if (!genCN) {
                    currentSubtitles = subtitles
                    return@hookAfterMethodWithPriority
                }

                val origSub = subtitles.first { it.lan == origin }
                var origSubId = origSub.id
                val api = if (!useLocalDict) convertApi.format(converter) else fakeConvertApi
                val targetSubUrl = Uri.parse(api).buildUpon()
                    .appendQueryParameter("sub_url", origSub.subtitleUrl)
                    .appendQueryParameter("sub_id", origSubId.toString())
                    .build().toString()

                val newSub = subtitleItem {
                    lan = target
                    lanDoc = targetDoc
                    lanDocBrief = targetDocBrief
                    subtitleUrl = targetSubUrl
                    id = ++origSubId
                    idStr = origSubId.toString()
                }

                val newRes = dmViewReply.copy {
                    subtitle = subtitle.copy {
                        this.subtitles.add(newSub)
                    }
                }
                currentSubtitles = newRes.subtitle.subtitlesList

                param.result = (param.method as Method).returnType
                    .callStaticMethod("parseFrom", newRes.toByteArray())
            }

        if (!useLocalDict) return
        instance.realCallClass?.hookBeforeMethod(instance.executeCall()) { param ->
            val request = param.thisObject.getObjectField(instance.realCallRequestField())
                ?: return@hookBeforeMethod
            val url = request.getObjectField(instance.urlField())?.toString()
                ?: return@hookBeforeMethod
            if (url.contains(fakeConvertApi)) {
                val subUrl = Uri.parse(url).let { uri ->
                    Uri.parse(uri.getQueryParameter("sub_url"))
                        .buildUpon()
                        .apply {
                            uri.queryParameterNames.forEach {
                                if (it != "sub_url" && it != "sub_id")
                                    appendQueryParameter(it, uri.getQueryParameter(it))
                            }
                        }.build().toString()
                }
                val protocol = instance.protocolClass?.fields?.get(0)?.get(null)
                    ?: return@hookBeforeMethod
                val mediaType = instance.mediaTypeClass
                    ?.callStaticMethod(
                        instance.getMediaType(),
                        "application/json; charset=UTF-8"
                    ) ?: return@hookBeforeMethod

                val dictReady = if (!SubtitleHelper.dictExist) {
                    runCatchingOrNull {
                        SubtitleHelper.executor.submit(Callable {
                            SubtitleHelper.checkDictUpdate()
                        }).get(60, TimeUnit.SECONDS)
                    } != null || SubtitleHelper.dictExist
                } else true
                val converted = if (dictReady) {
                    runCatching {
                        val responseText = URL(subUrl).readText()
                        SubtitleHelper.convert(responseText)
                    }.onFailure {
                        Log.e(it)
                    }.getOrNull()
                        ?: SubtitleHelper.errorResponse(XposedInit.moduleRes.getString(R.string.subtitle_convert_failed))
                } else SubtitleHelper.errorResponse(XposedInit.moduleRes.getString(R.string.subtitle_dict_download_failed))

                runCatchingOrNull {
                    SubtitleHelper.executor.execute {
                        SubtitleHelper.checkDictUpdate()?.let {
                            SubtitleHelper.reloadDict()
                        }
                    }
                }

                val responseBody = instance.responseBodyClass
                    ?.callStaticMethod(
                        instance.createResponseBody(),
                        mediaType,
                        converted
                    ) ?: return@hookBeforeMethod
                val responseBuildFields = instance.responseBuildFields()
                    .takeIf { it.isNotEmpty() } ?: return@hookBeforeMethod

                instance.responseBuilderClass?.new()
                    ?.setObjectField(responseBuildFields[0], request)
                    ?.setObjectField(responseBuildFields[1], protocol)
                    ?.setIntField(responseBuildFields[2], 200)
                    ?.setObjectField(responseBuildFields[3], "OK")
                    ?.setObjectField(responseBuildFields[4], responseBody)
                    ?.let { (param.method as Method).returnType.new(it) }
                    ?.let { param.result = it }
            }
        }
    }
    
    private var currentSubtitles = listOf<API.SubtitleItem>()

    private fun enableSubtitleDownloadHook() {
        "com.bilibili.bangumi.ui.page.detail.BangumiDetailActivityV3".hookAfterMethod(
            mClassLoader,
            "onConfigurationChanged",
            Configuration::class.java
        ) { param ->
            val thiz = param.thisObject as Activity
            activityRef = WeakReference(thiz)
            val newConfig = param.args[0] as Configuration
            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                subDownloadButtonHook(thiz)
            }
        }
        "com.bilibili.bangumi.ui.page.detail.BangumiDetailActivityV3".hookAfterMethod(
            mClassLoader,
            "onActivityResult",
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Intent::class.java
        ) { param ->
            val thiz = param.thisObject as Activity
            val requestCode = param.args[0] as Int
            val resultCode = param.args[1] as Int
            val data = (param.args[2] as Intent?)?.data
            if (data == null || resultCode != Activity.RESULT_OK) return@hookAfterMethod
            val titleDir = BangumiSeasonHook.lastSeasonInfo["title"] ?: return@hookAfterMethod
            val epId = BangumiSeasonHook.lastSeasonInfo["epid"]
            val epTitleDir = BangumiSeasonHook.lastSeasonInfo["ep_title_$epId"]
                ?: return@hookAfterMethod
            val exportJson = requestCode == reqCodeJson
            val ext = if (exportJson) "json" else "srt"
            val mimeType = if (exportJson) "application/json"
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                "application/x-subrip"
            else
                "application/octet-stream"
            SubtitleHelper.executor.execute {
                val titleDirDoc = DocumentFile.fromTreeUri(thiz, data)
                    ?.findOrCreateDir(titleDir) ?: return@execute
                currentSubtitles.forEach { item ->
                    val lan = item.lan
                    val lanDoc = item.lanDoc
                    val url = item.subtitleUrl
                    val fileName = "$titleDir-$epTitleDir-$lan-$lanDoc.$ext"
                    val subFileDoc = titleDirDoc
                        .findOrCreateDir(epTitleDir)
                        ?.findOrCreateFile(mimeType, fileName)
                        ?: return@forEach
                    thiz.contentResolver.openOutputStream(subFileDoc.uri, "wt")?.use { os ->
                        runCatching {
                            val json = JSONObject(URL(url).readText())
                            val body = json.getJSONArray("body")
                                .removeSubAppendedInfo().reSort()
                            json.put("body", body)
                            if (exportJson) {
                                val prettyJson = json.toString(2)
                                os.write(prettyJson.toByteArray())
                            } else {
                                os.write(body.convertToSrt().toByteArray())
                            }
                            Log.toast("字幕 $fileName 下载完成", force = true)
                        }.onFailure {
                            Log.toast("字幕 $fileName 下载失败", force = true)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private var activityRef = WeakReference<Activity>(null)
        private val anchorId by lazy { getId("control_container_subtitle_text") }
        private val playControlId by lazy { getId("control_container") }
        private val subDownloadButtonId = View.generateViewId()
        private const val reqCodeJson = 6666
        private const val reqCodeSrt = 8888

        private val Int.dp
            inline get() = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                toFloat(),
                currentContext.resources.displayMetrics
            ).roundToInt()

        fun onEpPlay() {
            MainScope().launch {
                activityRef.get()?.let {
                    subDownloadButtonHook(it)
                }
            }
        }

        @SuppressLint("InlinedApi")
        private fun subDownloadButtonHook(activity: Activity) {
            val anchor = activity.findViewById<TextView>(anchorId)
            val anchorButton = anchor?.parent as View? ?: return
            val buttonsView = anchorButton.parent as LinearLayout? ?: return
            if (anchorButton.visibility != View.VISIBLE
                || buttonsView.findViewById<TextView>(subDownloadButtonId) != null
            ) return
            val anchorIdx = buttonsView.indexOfChild(anchorButton)
            val subDownloadButton = TextView(activity).apply {
                text = "字幕下载"
                id = subDownloadButtonId
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14F)
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setPadding(11.dp, 0, 11.dp, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                setOnClickListener {
                    AlertDialog.Builder(activity)
                        .setTitle("格式选择")
                        .setItems(arrayOf("json", "srt")) { _, which ->
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                putExtra(
                                    DocumentsContract.EXTRA_INITIAL_URI,
                                    Uri.parse("content://com.android.externalstorage.documents/document/primary:Download")
                                )
                            }
                            activity.startActivityForResult(
                                intent,
                                if (which == 0) reqCodeJson else reqCodeSrt
                            )
                        }
                        .create().apply {
                            setOnShowListener {
                                window?.let { w ->
                                    @Suppress("DEPRECATION")
                                    val screenWidth = w.windowManager.defaultDisplay.width
                                    w.attributes = w.attributes.also {
                                        it.width = (screenWidth * 0.3).toInt()
                                    }
                                }
                            }
                        }
                        .show()
                    activity.findViewById<ViewGroup>(playControlId)?.getChildAt(0)
                        ?.visibility = View.GONE
                }
            }
            buttonsView.addView(subDownloadButton, anchorIdx - 1)
        }
    }
}