package me.iacn.biliroaming.hook

import android.net.Uri
import me.iacn.biliroaming.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class GenerateSubtitleHook(classLoader: ClassLoader) : BaseHook(classLoader) {

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
                val targetSubUrl = Uri.parse(origSub.subtitleUrl).buildUpon()
                    .appendQueryParameter("zh_converter", "t2cn")
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
                    runCatchingOrNull {
                        SubtitleHelper.downloadDict()
                    } == true
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
}
