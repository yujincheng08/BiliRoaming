package me.iacn.biliroaming.hook

import android.net.Uri
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.bv2av
import me.iacn.biliroaming.utils.getObjectField
import me.iacn.biliroaming.utils.hookAfterMethod
import me.iacn.biliroaming.utils.sPrefs
import me.iacn.biliroaming.utils.setObjectField
import java.net.HttpURLConnection
import java.net.URL

class ShareHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val contentUrlPattern = Regex("""[\s\S]*(https?://(?:bili2233\.cn|b23\.tv)/\S*)$""")

    private fun String.resolveB23URL(): String {
        val conn = URL(this).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = false
        conn.connect()
        if (conn.responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
            return conn.getHeaderField("Location")
        }
        return this
    }

    private fun transformUrl(url: String, transformAv: Boolean): String {
        val target = Uri.parse(url)
        val bv = if (transformAv) {
            target.path?.split("/")?.firstOrNull { it.startsWith("BV") && it.length == 12 }
        } else {
            null
        }
        val av = bv?.let { "av${bv2av(bv)}" }
        val newUrl = target.buildUpon()
        if (av != null) {
            newUrl.path(target.path!!.replace(bv, av))
        }
        val encodedQuery = target.encodedQuery
        if (encodedQuery != null) {
            val query = encodedQuery.split("&").map {
                it.split("=")
            }.filter {
                it.size == 2
            }.mapNotNull {
                when {
                    it[0] == "p" || it[0] == "t" -> "${it[0]}=${it[1]}"
                    it[0] == "start_progress" -> "start_progress=${it[1]}&t=${it[1].toLong() / 1000}"
                    else -> null
                }
            }.joinToString("&", postfix = "&unique_k=2333")
            newUrl.encodedQuery(query)
        } else {
            newUrl.appendQueryParameter("unique_k", "2333")
        }
        return newUrl.build().toString()
    }

    override fun startHook() {
        val miniProgramEnabled = sPrefs.getBoolean("mini_program", false)
        val purifyShareEnabled = sPrefs.getBoolean("purify_share", false)
        if (!miniProgramEnabled && !purifyShareEnabled) return
        Log.d("startHook: ShareHook")
        instance.shareClickResultClass?.apply {
            if (purifyShareEnabled) {
                hookAfterMethod("getLink") { param ->
                    (param.result as? String)?.takeIf {
                        it.startsWith("https://bili2233.cn") || it.startsWith("http://bili2233.cn") || it.startsWith("https://b23.tv") || it.startsWith("http://b23.tv")
                    }?.let {
                        val targetUrl = Uri.parse(it).buildUpon().query("").build().toString()
                        param.result = targetUrl.resolveB23URL().also { r -> param.thisObject.setObjectField("link", r) }
                    }
                }
                hookAfterMethod("getContent") { param ->
                    val content = param.result as? String
                    content?.let {
                        contentUrlPattern.matchEntire(it)?.groups?.get(1)?.value
                    }?.let { contentUrl ->
                        val resolvedUrl = (param.thisObject.getObjectField("link")?.let { it as String } ?: contentUrl)
                            .let {
                                if (it.startsWith("https://bili2233.cn") || it.startsWith("http://bili2233.cn") || it.startsWith("https://b23.tv") || it.startsWith("http://b23.tv"))
                                    it.resolveB23URL()
                                else it
                            }
                        param.result = content.replace(contentUrl, transformUrl(resolvedUrl, miniProgramEnabled)).also { r ->
                            param.thisObject.setObjectField("content", r)
                        }
                    }
                }
            }
            if (!miniProgramEnabled) return@apply
            // ShareMode Definition
            // 1: PARAMS_TYPE_TEXT
            // 2: PARAMS_TYPE_AUDIO
            // 4: PARAMS_TYPE_VIDEO
            // 5: PARAMS_TYPE_IMAGE
            // 6 / 7: PARAMS_TYPE_MIN_PROGRAM
            // 21: PARAMS_TYPE_PURE_IMAGE
            // Others: PARAMS_TYPE_WEB
            hookAfterMethod("getShareMode") { param ->
                if (param.result == 6 || param.result == 7) {
                    param.result = 0
                    param.thisObject.apply {
                        getObjectField("title")?.takeIf { it == "哔哩哔哩" }?.let { title ->
                            setObjectField("title", getObjectField("content"))
                            setObjectField("content", "由哔哩漫游分享")
                        }
                        getObjectField("content")?.let { it as String }
                            ?.takeIf { it.startsWith("已观看") }?.let { content ->
                                setObjectField("content", "$content\n由哔哩漫游分享")
                            }
                    }
                }
            }
        }
    }
}
