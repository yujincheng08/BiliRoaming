package me.iacn.biliroaming.hook

import android.net.Uri
import me.iacn.biliroaming.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import java.lang.reflect.Method
import java.net.URL

class GenerateSubtitleHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private val fakeConvertApi = "https://subtitle.biliroaming.114514"

    override fun startHook() {
        if (!sPrefs.getBoolean("auto_generate_subtitle", false)) return

        "com.bapis.bilibili.community.service.dm.v1.DMMoss".from(mClassLoader)
            ?.hookAfterMethod(
                "dmView",
                "com.bapis.bilibili.community.service.dm.v1.DmViewReq",
            ) { param ->
                val dmViewReply = param.result?.let {
                    API.DmViewReply.parseFrom(
                        it.callMethodAs<ByteArray>("toByteArray")
                    )
                } ?: return@hookAfterMethod
                val subtitles = dmViewReply.subtitle.subtitlesList
                val lanCodes = subtitles.map { it.lan }
                val genCN = "zh-Hant" in lanCodes && "zh-CN" !in lanCodes
                val origin = if (genCN) "zh-Hant" else ""
                val target = if (genCN) "zh-CN" else ""
                val targetDoc = if (genCN) "简中（生成）" else ""
                val targetDocBrief = if (genCN) "简中" else ""
                if (!genCN) return@hookAfterMethod

                val origSub = subtitles.first { it.lan == origin }
                val origSubId = origSub.id
                val targetSubUrl = Uri.parse(fakeConvertApi).buildUpon()
                    .appendQueryParameter("sub_url", origSub.subtitleUrl)
                    .build().toString()

                val newSub = subtitleItem {
                    lan = target
                    lanDoc = targetDoc
                    lanDocBrief = targetDocBrief
                    subtitleUrl = targetSubUrl
                    id = origSubId + 1
                    idStr = id.toString()
                }

                val newRes = dmViewReply.copy {
                    subtitle = subtitle.copy {
                        this.subtitles.add(newSub)
                    }
                }

                param.result = (param.method as Method).returnType
                    .callStaticMethod("parseFrom", newRes.toByteArray())
            }

        instance.realCallClass?.hookBeforeMethod(instance.executeCall()) { param ->
            val requestField = instance.realCallRequestField() ?: return@hookBeforeMethod
            val urlField = instance.urlField() ?: return@hookBeforeMethod
            val request = param.thisObject.getObjectField(requestField) ?: return@hookBeforeMethod
            val url = request.getObjectField(urlField)?.toString() ?: return@hookBeforeMethod
            if (url.contains(fakeConvertApi)) {
                val subUrl = Uri.parse(url).let { uri ->
                    Uri.parse(uri.getQueryParameter("sub_url"))
                        .buildUpon()
                        .apply {
                            uri.queryParameterNames.forEach {
                                if (it != "sub_url")
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
                        SubtitleHelper.downloadDict()
                    } == true
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
}
