package me.iacn.biliroaming.hook

import android.os.Bundle
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import java.net.HttpURLConnection
import java.net.URL

class MiniProgramHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val extractUrl = Regex("""(.*)(http\S*)(.*)""")
    override fun startHook() {
        if (!sPrefs.getBoolean("mini_program", false)) return
        Log.d("startHook: MiniProgram")
        instance.shareWrapperClass?.hookBeforeMethod(
            instance.shareWrapper(),
            String::class.java,
            Bundle::class.java
        ) { param ->
            val platform = param.args[0] as String
            val bundle = param.args[1] as Bundle
            if (platform == "COPY") {
                bundle.getString("params_content")?.let { content ->
                    extractUrl.matchEntire(content)
                }?.let {
                    listOf(it.groups[1]?.value, it.groups[2]?.value, it.groups[3]?.value)
                }?.let { (prefix, url, postfix) ->
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.instanceFollowRedirects = false
                    conn.connect()
                    if (conn.responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                        val target = URL(conn.getHeaderField("Location"))
                        val bv =
                            target.path.split("/").first { it.startsWith("BV") && it.length == 12 }
                        if (bv.isEmpty()) return@hookBeforeMethod
                        val av = bv2av(bv)
                        val query = target.query.split("&").map {
                            it.split("=")
                        }.filter {
                            it.size == 2
                        }.filter {
                            it[0] == "p"
                        }.joinToString("") {
                            it.joinToString("")
                        }
                        bundle.putString(
                            "params_content",
                            "${prefix}https://b23.tv/av${av}${if (query.isEmpty()) "" else "/${query}"}$postfix"
                        )
                    }

                }
                return@hookBeforeMethod
            }
            if (bundle.getString("params_type") != "type_min_program") return@hookBeforeMethod
            bundle.putString("params_type", "type_web")
            if (bundle.getString("params_title") == "哔哩哔哩") {
                bundle.putString("params_title", bundle.getString("params_content"))
                bundle.putString("params_content", "由哔哩漫游分享")
            }
            if (bundle.getString("params_content")?.startsWith("已观看") == true) {
                bundle.putString("params_content", "${bundle.getString("params_content")}\n由哔哩漫游分享")
            }
        }
    }
}
