@file:Suppress("DEPRECATION")

package me.iacn.biliroaming

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.preference.ListPreference
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import me.iacn.biliroaming.XposedInit.Companion.moduleRes
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.fetchJson
import me.iacn.biliroaming.utils.sPrefs
import java.net.URL
import java.util.concurrent.Executors

class SpeedTestResult(val name: String, val value: String?, var speed: String)

class SpeedTestAdapter(context: Context) : ArrayAdapter<SpeedTestResult>(context, 0) {
    class ViewHolder(var name: String?, val value: String?, val nameView: TextView, val speedView: TextView)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val layout = moduleRes.getLayout(R.layout.cdn_speedtest_item)
        val inflater = LayoutInflater.from(context)
        val view = convertView ?: inflater.inflate(layout, null).apply {
            tag = ViewHolder(
                    getItem(position)?.name,
                    getItem(position)?.value,
                    findViewById(R.id.upos_name),
                    findViewById(R.id.upos_speed)
            )
        }
        val holder = view.tag as ViewHolder
        holder.name = getItem(position)?.name
        holder.nameView.text = holder.name
        holder.speedView.text = moduleRes.getString(R.string.speed_formatter).format(getItem(position)?.speed)
        return view
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

class SpeedTestDialog(private val pref: ListPreference, activity: Activity) : AlertDialog.Builder(activity) {
    private val scope = MainScope()
    private val speedTestDispatcher = Executors.newFixedThreadPool(1).asCoroutineDispatcher()

    companion object {
        const val fileUrl = "https://www.biliplus.com/BPplayurl.php?cid=235297653&otype=json&fnval=16&module=pgc&platform=android"
        private val HOST_REGEX = Regex("""://[^/]+/""")
    }

    private val view = ListView(activity)
    private val adapter = SpeedTestAdapter(activity)

    init {
        view.adapter = adapter

        val layout = moduleRes.getLayout(R.layout.cdn_speedtest_item)
        val inflater = LayoutInflater.from(context)
        view.addHeaderView(inflater.inflate(layout, null).apply {
            findViewById<TextView>(R.id.upos_name).text = moduleRes.getString(R.string.upos)
            findViewById<TextView>(R.id.upos_speed).text = moduleRes.getString(R.string.speed)
        }, null, false)

        view.setPadding(50, 20, 50, 20)

        setView(view)

        setPositiveButton("关闭", null)

        setOnDismissListener {
            scope.cancel()
        }

        view.setOnItemClickListener { _, view, _, _ ->
            val (name, value) = (view.tag as SpeedTestAdapter.ViewHolder).run { name to value }
            Log.d("Use $value")
            pref.value = value
            sPrefs.edit().putString(pref.key, value).apply()
            Log.toast("已启用UPOS服务器：${name}")
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
            moduleRes.getStringArray(R.array.upos_entries).zip(moduleRes.getStringArray(R.array.upos_values)).asFlow().map {
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
                val url = URL(rawUrl.replace(HOST_REGEX, "://${upos}/"))
                val connection = url.openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("User-Agent", "Bilibili Freedoooooom/MarkII")
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
                stream.close() // release connection
                size / (System.currentTimeMillis() - start) // KB/s
            }
        }
    } catch (e: Throwable) {
        0L
    }

    private suspend fun getTestUrl() = withContext(Dispatchers.Default) {
        fetchJson(fileUrl)?.optJSONObject("dash")?.getJSONArray("audio")?.run {
            (0 until length()).map { idx -> optJSONObject(idx) }
        }?.minWithOrNull { a, b -> a.optInt("bandwidth") - b.optInt("bandwidth") }?.optString("base_url")?.replace("https", "http")
    }
}