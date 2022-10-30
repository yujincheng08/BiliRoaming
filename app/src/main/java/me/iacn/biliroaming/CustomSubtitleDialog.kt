@file:Suppress("DEPRECATION")

package me.iacn.biliroaming

import android.app.Activity
import android.app.AlertDialog
import android.app.Fragment
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.hook.SubtitleHook
import me.iacn.biliroaming.utils.Log

class CustomSubtitleDialog(val activity: Activity, fragment: Fragment, prefs: SharedPreferences) :
    AlertDialog.Builder(activity) {
    private var fontStatus: TextView? = null

    init {
        val oldClient = instance.cronCanvasClass == null
        val layout = activity.resources.getLayout(R.layout.custom_subtitle_dialog)
        val view = LayoutInflater.from(context).inflate(layout, null)
        val noBgSwitch = view.findViewById<Switch>(R.id.noBg).apply {
            isSoundEffectsEnabled = false
            isHapticFeedbackEnabled = false
            isChecked = prefs.getBoolean(tag.toString(), true)
        }
        val llNoBg = view.findViewById<View>(R.id.ll_noBg)
        llNoBg.setOnClickListener { noBgSwitch.toggle() }
        val boldSwitch = view.findViewById<Switch>(R.id.bold).apply {
            isSoundEffectsEnabled = false
            isHapticFeedbackEnabled = false
            isChecked = prefs.getBoolean(tag.toString(), true)
        }
        val llBold = view.findViewById<View>(R.id.ll_bold)
        llBold.setOnClickListener { boldSwitch.toggle() }
        val llFont = view.findViewById<View>(R.id.ll_font)
        fontStatus = view.findViewById(R.id.tv_fontStatus)
        refreshFontStatus()
        val fontColor = view.findViewById<EditText>(R.id.font_color)
        fontColor.setText(prefs.getString(fontColor.tag.toString(), "FFFFFFFF"))
        val backgroundColor = view.findViewById<EditText>(R.id.background_color)
        backgroundColor.setText(prefs.getString(backgroundColor.tag.toString(), "20000000"))
        val fontSize = view.findViewById<EditText>(R.id.font_size)
        fontSize.setText(prefs.getInt(fontSize.tag.toString(), 30).toString())
        val fontSizePortrait = view.findViewById<EditText>(R.id.fontSizePortrait)
        fontSizePortrait.setText(prefs.getInt(fontSizePortrait.tag.toString(), 0).toString())
        val fontSizeLandscape = view.findViewById<EditText>(R.id.fontSizeLandscape)
        fontSizeLandscape.setText(prefs.getInt(fontSizeLandscape.tag.toString(), 0).toString())
        val fontBlurSolid = view.findViewById<EditText>(R.id.font_blur_solid)
        fontBlurSolid.setText(prefs.getInt(fontBlurSolid.tag.toString(), 1).toString())
        val strokeColor = view.findViewById<EditText>(R.id.stroke_color)
        strokeColor.setText(prefs.getString(strokeColor.tag.toString(), "FF000000"))
        val strokeWidth = view.findViewById<EditText>(R.id.stroke_width)
        strokeWidth.setText(prefs.getFloat(strokeWidth.tag.toString(), 5.0F).toString())
        val fixBreak = view.findViewById<CheckBox>(R.id.cb_fixBreak)
        fixBreak.isChecked = prefs.getBoolean(fixBreak.tag.toString(), false)
        val btnPv = view.findViewById<Button>(R.id.btn_pv)
        btnPv.setOnClickListener {
            val testText = view.findViewById<EditText>(R.id.et_testText).text.toString()
            val spannableString = SpannableString(testText)
            val fc = fontColor.text.toString()
            val bc = backgroundColor.text.toString()
            val fs = fontSize.text.toString().toInt()
            val fbs = fontBlurSolid.text.toString().toInt()
            val sc = strokeColor.text.toString()
            val sw = strokeWidth.text.toString().toFloat()
            SubtitleHook.subtitleStylizeRunner(
                spannableString,
                0,
                spannableString.length,
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE,
                fbs, fc, fs, bc, sc, sw,
                fixBreak.isChecked
            )
            view.findViewById<TextView>(R.id.tv_pvBlack).text = spannableString
            view.findViewById<TextView>(R.id.tv_pvWhite).text = spannableString
            view.findViewById<TextView>(R.id.tv_pvTp).text = spannableString
        }
        view.findViewById<Button>(R.id.btn_chooseColorBc).setOnClickListener {
            ARGBColorChooseDialog(activity, Color.parseColor("#${backgroundColor.text}")).apply {
                setPositiveButton(android.R.string.ok) { _, _ ->
                    backgroundColor.setText(String.format("%08X", 0xFFFFFFFF.toInt() and color))
                }
            }.show()
        }
        view.findViewById<Button>(R.id.btn_chooseColorFc).setOnClickListener {
            ARGBColorChooseDialog(activity, Color.parseColor("#${fontColor.text}")).apply {
                setPositiveButton(android.R.string.ok) { _, _ ->
                    fontColor.setText(String.format("%08X", 0xFFFFFFFF.toInt() and color))
                }
            }.show()
        }
        view.findViewById<Button>(R.id.btn_chooseColorSc).setOnClickListener {
            ARGBColorChooseDialog(activity, Color.parseColor("#${strokeColor.text}")).apply {
                setPositiveButton(android.R.string.ok) { _, _ ->
                    strokeColor.setText(String.format("%08X", 0xFFFFFFFF.toInt() and color))
                }
            }.show()
        }
        view.findViewById<Button>(R.id.btn_importFont).setOnClickListener {
            try {
                fragment.startActivityForResult(
                    Intent.createChooser(Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "font/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }, "选择字体文件"),
                    2338
                )
            } catch (ex: ActivityNotFoundException) {
                Log.toast("请安装文件管理器")
            }
        }
        view.findViewById<Button>(R.id.btn_resetFont).setOnClickListener {
            SubtitleHook.fontFile.delete()
            refreshFontStatus()
        }

        if (oldClient) {
            llNoBg.visibility = View.GONE
            llBold.visibility = View.GONE
            llFont.visibility = View.GONE
            view.findViewById<View>(R.id.ll_sizePortrait).visibility = View.GONE
            view.findViewById<View>(R.id.ll_sizeLandscape).visibility = View.GONE
        } else {
            view.findViewById<View>(R.id.pvBlack).visibility = View.GONE
            view.findViewById<View>(R.id.pvWhite).visibility = View.GONE
            view.findViewById<View>(R.id.pvTp).visibility = View.GONE
            view.findViewById<View>(R.id.ll_bg).visibility = View.GONE
            view.findViewById<View>(R.id.ll_size).visibility = View.GONE
            view.findViewById<View>(R.id.ll_blur).visibility = View.GONE
            view.findViewById<View>(R.id.ll_pv).visibility = View.GONE
            fixBreak.visibility = View.GONE
        }

        setPositiveButton(android.R.string.ok) { _, _ ->
            prefs.edit().apply {
                putBoolean(noBgSwitch.tag.toString(), noBgSwitch.isChecked)
                putBoolean(boldSwitch.tag.toString(), boldSwitch.isChecked)
                putString(fontColor.tag.toString(), fontColor.text.toString())
                putString(backgroundColor.tag.toString(), backgroundColor.text.toString())
                putInt(fontSize.tag.toString(), fontSize.text.toString().toInt())
                putInt(fontSizePortrait.tag.toString(), fontSizePortrait.text.toString().toInt())
                putInt(fontSizeLandscape.tag.toString(), fontSizeLandscape.text.toString().toInt())
                putInt(fontBlurSolid.tag.toString(), fontBlurSolid.text.toString().toInt())
                putString(strokeColor.tag.toString(), strokeColor.text.toString())
                putFloat(strokeWidth.tag.toString(), strokeWidth.text.toString().toFloat())
                putBoolean(fixBreak.tag.toString(), fixBreak.isChecked)
            }.apply()
        }

        setTitle(activity.getString(R.string.custom_subtitle_title))

        setView(view)
    }

    private fun refreshFontStatus() {
        fontStatus?.text = if (SubtitleHook.fontFile.isFile)
            activity.getString(R.string.custom_subtitle_status_custom)
        else
            activity.getString(R.string.custom_subtitle_status_default)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = data?.data
        if (requestCode != 2338 || resultCode != Activity.RESULT_OK || uri == null) return
        activity.contentResolver.openInputStream(uri)?.use {
            val fontFile = SubtitleHook.fontFile.apply { delete() }
            it.copyTo(fontFile.outputStream())
            refreshFontStatus()
        }
    }
}
