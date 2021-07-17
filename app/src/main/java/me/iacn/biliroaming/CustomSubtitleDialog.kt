package me.iacn.biliroaming

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.text.SpannableString
import android.text.Spanned
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import me.iacn.biliroaming.XposedInit.Companion.moduleRes
import me.iacn.biliroaming.hook.SubtitleHook

class CustomSubtitleDialog(activity: Activity,prefs: SharedPreferences) : AlertDialog.Builder(activity)  {
    init {
        val layout = moduleRes.getLayout(R.layout.custom_subtitle_dialog)
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(layout, null)
        val fontColor = view.findViewById<EditText>(R.id.font_color)
        fontColor.setText(prefs.getString(fontColor.tag.toString(), "FFFFFFFF"))
        val backgroundColor = view.findViewById<EditText>(R.id.background_color)
        backgroundColor.setText(prefs.getString(backgroundColor.tag.toString(), "20000000"))
        val fontSize = view.findViewById<EditText>(R.id.font_size)
        fontSize.setText(prefs.getInt(fontSize.tag.toString(), 30).toString())
        val fontBlurSolid = view.findViewById<EditText>(R.id.font_blur_solid)
        fontBlurSolid.setText(prefs.getInt(fontBlurSolid.tag.toString(), 1).toString())
        val btnPv = view.findViewById<Button>(R.id.btn_pv)
        btnPv.setOnClickListener {
            val testText = view.findViewById<EditText>(R.id.et_testText).text.toString()
            val spannableString = SpannableString(testText)
            val fc = fontColor.text.toString()
            val bc = backgroundColor.text.toString()
            val fs = fontSize.text.toString().toInt()
            val fbs = fontBlurSolid.text.toString().toInt()
            SubtitleHook.subtitleStylizeRunner(spannableString, 0, spannableString.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE, fbs, fc, fs, bc)
            view.findViewById<TextView>(R.id.tv_pvBlack).text = spannableString
            view.findViewById<TextView>(R.id.tv_pvWhite).text = spannableString
        }

        setPositiveButton(android.R.string.ok) { _, _ ->
            prefs.edit().putString(fontColor.tag.toString(), fontColor.text.toString()).apply()
            prefs.edit().putString(backgroundColor.tag.toString(), backgroundColor.text.toString()).apply()
            prefs.edit().putInt(fontSize.tag.toString(), fontSize.text.toString().toInt()).apply()
            prefs.edit().putInt(fontBlurSolid.tag.toString(), fontBlurSolid.text.toString().toInt()).apply()
        }

        setTitle("自定义字幕样式")
        setView(view)
    }
}