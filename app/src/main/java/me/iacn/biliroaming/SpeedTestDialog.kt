package me.iacn.biliroaming

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.iacn.biliroaming.XposedInit.Companion.moduleRes
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.fetchJson
import me.iacn.biliroaming.utils.sPrefs
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.Executors

class SpeedTestResult(val ip: String, var speed: String)

class SpeedTestAdapter(context: Context) : ArrayAdapter<SpeedTestResult>(context, 0) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return TextView(context).apply {
            getItem(position)?.run {
                text = moduleRes.getString(R.string.speed_test_formatter).format(ip, speed)
                tag = ip
            }
        }
    }
}

@FlowPreview
class SpeedTestDialog(activity: Activity) : AlertDialog.Builder(activity) {
    private val scope = MainScope()
    private val speedTestDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

    companion object {
        const val endScript = "</script>"
        const val fileUrl = "https://www.biliplus.com/BPplayurl.php?cid=235297653&otype=json&fnval=16&module=pgc&platform=android"
        const val digUrl = "https://tools.ipip.net/dns.php?a=dig&host=${Constant.AKAMAI_HOST}&custom_dns=&area%5B%5D=china&area%5B%5D=asia&area%5B%5D=europe&area%5B%5D=africa&area%5B%5D=oceania&area%5B%5D=north_america&area%5B%5D=south_america"
        const val dnsPattern = """^<script>parent\.call_dns\('.*', '.*', '.*', (.*)\);</script>$"""
    }

    private val dnsRegex = dnsPattern.toRegex()
    private val view = ListView(activity)
    private val adapter = SpeedTestAdapter(activity)

    init {
        view.adapter = adapter

        setView(view)
        setOnDismissListener {
            scope.cancel()
        }

        view.setOnItemClickListener { _, view, _, _ ->
            Log.d("Use ${view.tag}")
            sPrefs.edit().putString("custom_cdn", view.tag.toString()).apply()
            Log.toast("已把${view.tag}填入自定义CDN中")
        }

        setTitle("CDN测速")
    }

    override fun show(): AlertDialog {
        val dialog = super.show()
        scope.launch {
            dialog.setTitle("正在测速")
            val url = getTestUrl() ?: run {
                dialog.setTitle("测速失败")
                return@launch
            }
            digIps().flatMapMerge {
                handleScript(it)
            }.withIndex().map {
                scope.launch {
                    adapter.add(SpeedTestResult(it.value, ""))
                    val speed = speedTest(it.value, url)
                    adapter.getItem(it.index)?.speed = speed.toString()
                    view.invalidateViews()
                    adapter.notifyDataSetChanged()
                }
            }.toList().joinAll()
            dialog.setTitle("测速完成")
        }
        return dialog
    }

    @Suppress("BlockingMethodInNonBlockingContext") // Fuck JetBrain
    private suspend fun speedTest(ip: String, rawUrl: String) = try {
        withContext(speedTestDispatcher) {
            withTimeout(5000) {
                val url = URL(rawUrl.replace(Constant.AKAMAI_HOST, ip))
                val connection = url.openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("Host", Constant.AKAMAI_HOST)
                connection.connect()
                val stream = connection.getInputStream()
                val buffer = ByteArray(2048)
                var size = 0
                val start = System.currentTimeMillis()
                while (isActive) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    size += read
                }
                size / (System.currentTimeMillis() - start) // KB/s
            }
        }
    } catch (e: Throwable) {
        -1L
    }


    private fun handleScript(script: String) = dnsRegex.matchEntire(script)?.run {
        val (jsonString) = destructured
        val json = JSONObject(jsonString)
        json.optJSONObject("ips")?.keys()?.asFlow()
    } ?: emptyFlow()

    private suspend fun getTestUrl() = withContext(Dispatchers.Default) {
        fetchJson(fileUrl)?.optJSONObject("dash")?.getJSONArray("audio")?.run {
            (0 until length()).map { idx -> getJSONObject(idx) }
        }?.minWithOrNull { a, b -> a.optInt("bandwidth") - b.optInt("bandwidth") }?.optString("base_url")?.replace("https", "http")
    }

    @Suppress("BlockingMethodInNonBlockingContext") // Fuck JetBrain
    private suspend fun digIps() = flow {
        val url = URL(digUrl)
        val connection = url.openConnection()
        connection.connect()
        val stream = connection.getInputStream()
        val stringBuffer = StringBuffer()
        val buffer = ByteArray(1024)
        while (true) {
            val end = stringBuffer.indexOf(endScript)
            if (end != -1) {
                val out = stringBuffer.substring(0, end + endScript.length)
                stringBuffer.delete(0, end + endScript.length)
                emit(out)
            } else {
                val readBytes = stream.read(buffer)
                if (readBytes <= 0)
                    break
                stringBuffer.append(buffer.decodeToString(0, readBytes))
            }
        }
    }.flowOn(Dispatchers.IO)
}