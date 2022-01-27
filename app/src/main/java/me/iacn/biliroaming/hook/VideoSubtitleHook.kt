package me.iacn.biliroaming.hook

import android.net.Uri
import me.iacn.biliroaming.BiliBiliPackage
import me.iacn.biliroaming.utils.*

class VideoSubtitleHook(private val classLoader: ClassLoader) : BaseHook(classLoader) {

    private val host = "https://www.kofua.top/bsub/t2s"

    override fun startHook() {
        if (!sPrefs.getBoolean("auto_generate_chs_subtitle", false)) return

        BiliBiliPackage.instance.videoSubtitleClass?.hookAfterMethod("getSubtitlesList") ret@{ param ->
            val subtitles = param.result as? List<*> ?: listOf<Any>()
            val subtitleItemClass = BiliBiliPackage.instance.subtitleItemClass ?: return@ret
            val lanCodes = subtitles.map { s -> s?.callMethod("getLan") as? String ?: "" }
            if ("zh-CN" !in lanCodes && "zh-Hant" in lanCodes) {
                val zhHant = subtitles.first { s -> s?.callMethod("getLan") == "zh-Hant" } ?: return@ret
                val subUrl = zhHant.callMethod("getSubtitleUrl") as? String ?: return@ret
                val zhHansUrl = Uri.parse(host).buildUpon().appendQueryParameter("sub_url", subUrl).build().toString()
                val subTypeClass = "com.bapis.bilibili.community.service.dm.v1.SubtitleType".findClass(classLoader)
                val ccType = subTypeClass.getStaticObjectField("CC")
                val item = subtitleItemClass.new()
                item.callMethod("setLan", "zh-CN")
                item.callMethod("setLanDoc", "简中（生成）")
                item.callMethod("setSubtitleUrl", zhHansUrl)
                item.callMethod("setType", ccType)
                item.callMethod("setId", zhHant.callMethod("getId"))
                item.callMethod("setIdStr", zhHant.callMethod("getIdStr"))
                val thiz = param.thisObject
                thiz.callMethod("addSubtitles", subtitles.size, item)
                param.result = thiz.callMethod("getSubtitlesList")
            }
        }
    }
}
