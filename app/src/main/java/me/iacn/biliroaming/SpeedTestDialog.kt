@file:Suppress("DEPRECATION")

package me.iacn.biliroaming

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import me.iacn.biliroaming.network.BiliRoamingApi
import me.iacn.biliroaming.network.BiliRoamingApi.getPlayUrl
import me.iacn.biliroaming.network.BiliRoamingApi.mainlandTestParams
import me.iacn.biliroaming.network.BiliRoamingApi.overseaTestParams
import me.iacn.biliroaming.utils.*
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class SpeedTestResult(val name: String, val value: String, var speed: String)

class SpeedTestAdapter(context: Context) : ArrayAdapter<SpeedTestResult>(context, 0) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return (convertView ?: context.inflateLayout(R.layout.cdn_speedtest_item)).apply {
            getItem(position).let {
                findViewById<TextView>(R.id.upos_name).text = it?.name
                findViewById<TextView>(R.id.upos_speed).text =
                    context.getString(R.string.speed_formatter, it?.speed)
            }
        }
    }

    fun sort() = sort { a, b ->
        val aSpeed = a.speed.toLongOrNull()
        val bSpeed = b.speed.toLongOrNull()
        if (aSpeed == null && bSpeed == null)
            0
        else if (aSpeed == null)
            1
        else if (bSpeed == null)
            -1
        else
            (bSpeed - aSpeed).toInt()
    }
}

class SpeedTestDialog(activity: Activity, prefs: SharedPreferences) :
    AlertDialog.Builder(activity) {
    private val scope = MainScope()
    private val speedTestDispatcher = Executors.newFixedThreadPool(1).asCoroutineDispatcher()

    private val view = ListView(activity)
    private val adapter = SpeedTestAdapter(activity)

    init {
        view.adapter = adapter

        view.addHeaderView(context.inflateLayout(R.layout.cdn_speedtest_item).apply {
            findViewById<TextView>(R.id.upos_name).text = context.getString(R.string.upos)
            findViewById<TextView>(R.id.upos_speed).text = context.getString(R.string.speed)
        }, null, false)

        view.setPadding(16.dp, 10.dp, 16.dp, 10.dp)

        setView(view)

        setPositiveButton("关闭", null)

        setOnDismissListener {
            scope.cancel()
        }

        view.setOnItemClickListener { _, _, pos, _ ->
            val (name, value, _) = adapter.getItem(pos - 1/*headerView*/)
                ?: return@setOnItemClickListener
            Log.d("Use UPOS $name: $value")
            prefs.edit().putString("upos_host", value).apply()
            Log.toast("已启用UPOS服务器：${name}", force = true)
        }

        setTitle("CDN测速")
    }

    override fun show(): AlertDialog {
        val dialog = super.show()
        scope.launch {
            dialog.setTitle("正在测速……")
            val url = getTestUrl() ?: run {
                dialog.setTitle("测速失败")
                return@launch
            }
            context.resources.getStringArray(R.array.upos_entries)
                .zip(context.resources.getStringArray(R.array.upos_values)).asFlow().map {
                    scope.launch {
                        val item = SpeedTestResult(it.first, it.second, "...")
                        adapter.add(item)
                        adapter.sort()
                        val speed = speedTest(it.second, url)
                        item.speed = speed.toString()
                        adapter.sort()
                    }
                }.toList().joinAll()
            dialog.setTitle("测速完成")
        }
        return dialog
    }

    @Suppress("BlockingMethodInNonBlockingContext") // Fuck JetBrain
    private suspend fun speedTest(upos: String, rawUrl: String) = try {
        withContext(speedTestDispatcher) {
            withTimeout(5000) {
                val url = if (upos == "\$1") URL(rawUrl) else {
                    URL(Uri.parse(rawUrl).buildUpon().authority(upos).build().toString())
                }
                val connection = url.openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("User-Agent", "Bilibili Freedoooooom/MarkII")
                connection.connect()
                val buffer = ByteArray(2048)
                var size = 0
                val start = System.currentTimeMillis()
                connection.getInputStream().use { stream ->
                    while (isActive) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        size += read
                    }
                }
                size / (System.currentTimeMillis() - start) // KB/s
            }
        }
    } catch (e: Throwable) {
        0L
    }

    private suspend fun getTestUrl() = try {
        withContext(speedTestDispatcher) {
            withTimeout(5000) {
                val cn = runCatchingOrNull { XposedInit.country.get(5L, TimeUnit.SECONDS) } == "cn"
                val json = if (cn) {
                    getPlayUrl(overseaTestParams, arrayOf("hk", "tw"))
                } else getPlayUrl(mainlandTestParams, arrayOf("cn"))
                json?.toJSONObject()?.optJSONObject("dash")?.getJSONArray("audio")
                    ?.asSequence<JSONObject>()
                    ?.minWithOrNull { a, b -> a.optInt("bandwidth") - b.optInt("bandwidth") }
                    ?.optString("base_url")?.replace("https", "http")
            }
        }
    } catch (e: BiliRoamingApi.CustomServerException) {
        Log.w("请求解析服务器发生错误: ${e.message}")
        null
    } catch (e: Throwable) {
        null
    }
}
