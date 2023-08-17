package me.iacn.biliroaming.hook

import android.net.Uri
import android.os.Bundle
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.bv2av
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.sPrefs
import java.net.HttpURLConnection
import java.net.URL

class ShareHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val contentUrlPattern = Regex("""[\s\S]*(https://b23\.tv/\S*)$""")

    private fun resolveB23URL(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = false
        conn.connect()
        if (conn.responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
            return conn.getHeaderField("Location")
        }
        return url
    }

    private fun transformUrl(url: String, transformAv: Boolean): String {
        val target = Uri.parse(url)
        val bv = if (transformAv)
            target.path?.split("/")
                ?.firstOrNull { it.startsWith("BV") && it.length == 12 }
        else null
        val av = bv?.let { "av${bv2av(bv)}" }
        val query = target.encodedQuery?.split("&")?.map {
            it.split("=")
        }?.filter {
            it.size == 2
        }?.mapNotNull {
            if (it[0] == "p" || it[0] == "t") "${it[0]}=${it[1]}"
            else if (it[0] == "start_progress") "start_progress=${it[1]}&t=${it[1].toLong() / 1000}"
            else null
        }?.joinToString("&")
        val newUrl = target.buildUpon()
        if (av != null) {
            newUrl.path(target.path!!.replace(bv, av))
        }
        if (query != null) {
            newUrl.encodedQuery(query)
        }
        return newUrl.build().toString()
    }

    override fun startHook() {
        val miniProgramEnabled = sPrefs.getBoolean("mini_program", false)
        val purifyShareEnabled = sPrefs.getBoolean("purify_share", false)
        if (!miniProgramEnabled && !purifyShareEnabled) return
        Log.d("startHook: ShareHook")
        instance.shareWrapperClass?.hookBeforeMethod(
            instance.shareWrapper(),
            String::class.java,
            Bundle::class.java
        ) { param ->
            val bundle = param.args[1] as Bundle
            if (miniProgramEnabled && bundle.getString("params_type") == "type_min_program") {
                bundle.putString("params_type", "type_web")
                if (bundle.getString("params_title") == "哔哩哔哩") {
                    bundle.putString("params_title", bundle.getString("params_content"))
                    bundle.putString("params_content", "由哔哩漫游分享")
                }
                if (bundle.getString("params_content")?.startsWith("已观看") == true) {
                    bundle.putString(
                        "params_content",
                        "${bundle.getString("params_content")}\n由哔哩漫游分享"
                    )
                }
            }
            val targetUrl = bundle.getString("params_target_url")?.let {
                Uri.parse(it).buildUpon().query("").build().toString()
            }
            val content = bundle.getString("params_content")
            val contentUrl = content?.let {
                contentUrlPattern.matchEntire(it)?.groups?.get(1)?.value
            }
            val realUrl = if (targetUrl != null) {
                if (targetUrl.startsWith("https://b23.tv")) resolveB23URL(targetUrl)
                else targetUrl
            } else if (contentUrl != null) {
                resolveB23URL(contentUrl)
            } else return@hookBeforeMethod
            if (!purifyShareEnabled && !realUrl.contains("/video/")) return@hookBeforeMethod
            if (contentUrl != null) {
                bundle.putString("params_content", content.replace(contentUrl, transformUrl(realUrl, miniProgramEnabled)))
            }
            bundle.putString("params_target_url", realUrl)
        }
    }
}
