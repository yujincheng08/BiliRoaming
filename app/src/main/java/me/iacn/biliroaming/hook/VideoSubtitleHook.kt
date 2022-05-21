package me.iacn.biliroaming.hook

import android.net.Uri
import me.iacn.biliroaming.API
import me.iacn.biliroaming.copy
import me.iacn.biliroaming.subtitleItem
import me.iacn.biliroaming.utils.*
import java.lang.reflect.Method

class VideoSubtitleHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private val convertApi = "https://www.kofua.top/bsub/%s"

    override fun startHook() {
        if (!sPrefs.getBoolean("auto_generate_subtitle", false)) return

        "com.bapis.bilibili.community.service.dm.v1.DMMoss".from(mClassLoader)
            ?.hookAfterMethod(
                "dmView",
                "com.bapis.bilibili.community.service.dm.v1.DmViewReq"
            ) { param ->
                val dmViewReply = param.result?.let {
                    API.DmViewReply.parseFrom(
                        it.callMethodAs<ByteArray>("toByteArray")
                    )
                } ?: return@hookAfterMethod
                val subtitles = dmViewReply.subtitle.subtitlesList
                if (subtitles.isEmpty()) return@hookAfterMethod
                val lanCodes = subtitles.map { it.lan }
                val genCN = "zh-Hant" in lanCodes && "zh-CN" !in lanCodes
                val genHant = "zh-CN" in lanCodes && "zh-Hant" !in lanCodes
                val origin = if (genCN) "zh-Hant" else if (genHant) "zh-CN" else ""
                val target = if (genCN) "zh-CN" else if (genHant) "zh-Hant" else ""
                val converter = if (genCN) "t2cn" else if (genHant) "cn2t" else ""
                val targetDoc = if (genCN) "简中（生成）" else if (genHant) "繁中（生成）" else ""
                val targetDocBrief = if (genCN) "简中" else if (genHant) "繁中" else ""
                if (origin.isEmpty()) return@hookAfterMethod

                val origSub = subtitles.first { it.lan == origin }
                var origSubId = origSub.id
                val targetSubUrl = Uri.parse(convertApi.format(converter))
                    .buildUpon()
                    .appendQueryParameter("sub_url", origSub.subtitleUrl)
                    .appendQueryParameter("sub_id", origSubId.toString())
                    .build()
                    .toString()

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

                param.result = (param.method as Method).returnType
                    .callStaticMethod("parseFrom", newRes.toByteArray())
            }
    }
}
