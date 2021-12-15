package me.iacn.biliroaming

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import me.iacn.biliroaming.XposedInit.Companion.moduleRes
import me.iacn.biliroaming.utils.Log

class HomeFilterDialog(val activity: Activity,prefs: SharedPreferences) : AlertDialog.Builder(activity)  {
    init {
        val layout = moduleRes.getLayout(R.layout.home_filter_dialog)
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(layout, null)

        val low_play_count = view.findViewById<EditText>(R.id.hide_low_play_count_recommend)
        low_play_count.setText(prefs.getLong(low_play_count.tag.toString(), 100).toString())
        val short_duration = view.findViewById<EditText>(R.id.hide_short_duration_recommend)
        short_duration.setText(prefs.getInt(short_duration.tag.toString(), 0).toString())
        val long_duration = view.findViewById<EditText>(R.id.hide_long_duration_recommend)
        long_duration.setText(prefs.getInt(long_duration.tag.toString(), 0).toString())

        val title = view.findViewById<EditText>(R.id.keywords_filter_title_recommend)
        title.setText(prefs.getString(title.tag.toString(), ""))
        val reason = view.findViewById<EditText>(R.id.keywords_filter_reason_recommend)
        reason.setText(prefs.getString(reason.tag.toString(), ""))
        val uid = view.findViewById<EditText>(R.id.keywords_filter_uid_recommend)
        uid.setText(prefs.getString(uid.tag.toString(), ""))
        val upname = view.findViewById<EditText>(R.id.keywords_filter_upname_recommend)
        upname.setText(prefs.getString(upname.tag.toString(), ""))
        val rname = view.findViewById<EditText>(R.id.keywords_filter_rname_recommend)
        rname.setText(prefs.getString(rname.tag.toString(), ""))
        val tname = view.findViewById<EditText>(R.id.keywords_filter_tname_recommend)
        tname.setText(prefs.getString(tname.tag.toString(), ""))

        view.findViewById<Button>(R.id.btn_add_in_title).setOnClickListener {
            when {
                title.text.isEmpty() -> {
                    Log.toast("你好像还没有输入内容> <")
                }
                title.text.endsWith('|') -> {
                    Log.toast("啊嘞 上一个分隔符后面好像还没有东西> <")
                }
                else -> {
                    title.text.append('|')
                }
            }
        }
        view.findViewById<Button>(R.id.btn_add_in_reason).setOnClickListener {
            when {
                reason.text.isEmpty() -> {
                    Log.toast("你好像还没有输入内容> <")
                }
                reason.text.endsWith('|') -> {
                    Log.toast("啊嘞 上一个分隔符后面好像还没有东西> <")
                }
                else -> {
                    reason.text.append('|')
                }
            }
        }
        view.findViewById<Button>(R.id.btn_add_in_uid).setOnClickListener {
            when {
                uid.text.isEmpty() -> {
                    Log.toast("你好像还没有输入内容> <")
                }
                uid.text.endsWith('|') -> {
                    Log.toast("啊嘞 上一个分隔符后面好像还没有东西> <")
                }
                else -> {
                    uid.text.append('|')
                }
            }
        }
        view.findViewById<Button>(R.id.btn_add_in_upname).setOnClickListener {
            when {
                upname.text.isEmpty() -> {
                    Log.toast("你好像还没有输入内容> <")
                }
                upname.text.endsWith('|') -> {
                    Log.toast("啊嘞 上一个分隔符后面好像还没有东西> <")
                }
                else -> {
                    upname.text.append('|')
                }
            }
        }
        view.findViewById<Button>(R.id.btn_add_in_rname).setOnClickListener {
            when {
                rname.text.isEmpty() -> {
                    Log.toast("你好像还没有输入内容> <")
                }
                rname.text.endsWith('|') -> {
                    Log.toast("啊嘞 上一个分隔符后面好像还没有东西> <")
                }
                else -> {
                    rname.text.append('|')
                }
            }
        }
        view.findViewById<Button>(R.id.btn_add_in_tname).setOnClickListener {
            when {
                tname.text.isEmpty() -> {
                    Log.toast("你好像还没有输入内容> <")
                }
                tname.text.endsWith('|') -> {
                    Log.toast("啊嘞 上一个分隔符后面好像还没有东西> <")
                }
                else -> {
                    tname.text.append('|')
                }
            }
        }

        setPositiveButton(android.R.string.ok) { _, _ ->
            prefs.edit().apply {
                putLong(low_play_count.tag.toString(), low_play_count.text.toString().toLong())
                putInt(short_duration.tag.toString(), short_duration.text.toString().toInt())
                putInt(long_duration.tag.toString(), long_duration.text.toString().toInt())
                putString(title.tag.toString(), title.text.toString())
                putString(reason.tag.toString(), reason.text.toString())
                putString(uid.tag.toString(), uid.text.toString())
                putString(upname.tag.toString(), upname.text.toString())
                putString(rname.tag.toString(), rname.text.toString())
                putString(tname.tag.toString(), tname.text.toString())
            }.apply()
            Log.toast("保存成功 重启后生效")
        }

        setTitle("首页推送过滤")
        
        view.setPadding(50, 20, 50, 20)

        setView(view)
    }

}
