package me.iacn.biliroaming

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Fragment
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import me.iacn.biliroaming.XposedInit.Companion.moduleRes
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.callMethodAs
import me.iacn.biliroaming.utils.gson
import java.io.File

class VideoExportDialog(activity: Activity, fragment: Fragment) : AlertDialog.Builder(activity) {
    companion object {
        /**
         * 指向视频页的文件
         */
        var videosToExport = emptySet<File>()
    }

    private val view = ListView(activity)
    private val selectedVideos = mutableSetOf<File>()

    init {
        if (gson == null) {
            setMessage("无Gson 不支持")
            setPositiveButton("确定", null)
        } else {
            val allVideos = mutableListOf<VideoEntry>()
            File(activity.externalCacheDir, "../download").listFiles()?.forEach { video ->

                video.listFiles()?.forEach { page ->
                    try {
                        val videoEntry = gson!!.callMethodAs<VideoEntry>("fromJson", File(page, "entry.json").inputStream().reader(), VideoEntry::class.java)
                        videoEntry.path = page
                        allVideos.add(videoEntry)
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        Log.toast("${e.message}", true)
                    }
                }
            }
            view.adapter = VideoExportAdapter(activity, allVideos, selectedVideos)
            view.setPadding(50, 20, 50, 20)

            setNegativeButton("取消", null)

            setPositiveButton("导出") { _, _ ->
                videosToExport = selectedVideos
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                try {
                    fragment.startActivityForResult(Intent.createChooser(intent, "导出视频"), SettingDialog.VIDEO_EXPORT)
                } catch (ex: ActivityNotFoundException) {
                    Log.toast("请安装文件管理器")
                }
            }

            setView(view)
        }
    }

    class VideoEntry(
            val title: String,
            val avid: Long,
            val bvid: String?,
            val page_data: PageData?,
            val ep: Ep?,
            var path: File? = null
    ) {
        class PageData(
                val part: String
        )

        class Ep(
                val av_id: Long,
                val bvid: String?,
                val index: String,
                val index_title: String
        )


        val aBvid
            get() = bvid ?: ep?.bvid
        val aid get() = ep?.av_id ?: avid
        val pageTitle get() = page_data?.part ?: ep?.let { "${it.index} ${it.index_title}" }
    }

    class VideoExportAdapter(context: Context, private val allVideos: List<VideoEntry>, private val selectedVideos: MutableSet<File>) : ArrayAdapter<VideoEntry>(context, 0) {
        @SuppressLint("SetTextI18n")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val layout = moduleRes.getLayout(R.layout.video_choose)
            val inflater = LayoutInflater.from(context)
            return convertView ?: inflater.inflate(layout, null).apply {
                allVideos[position].let {
                    findViewById<TextView>(R.id.tv_title).text = it.title
                    findViewById<TextView>(R.id.tv_pageTitle).text = it.pageTitle
                    findViewById<TextView>(R.id.tv_aid).text = "av${it.aid}"
                    findViewById<TextView>(R.id.tv_bvid).text = it.aBvid
                    findViewById<CheckBox>(R.id.cb).setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked)
                            it.path?.let { it1 -> selectedVideos.add(it1) }
                        else
                            selectedVideos.remove(it.path)
                    }
                }
            }
        }

        override fun getCount() = allVideos.size
    }
}