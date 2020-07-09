package me.iacn.biliroaming

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView

/**
 * Created by iAcn on 2019/7/14
 * Email i@iacn.me
 */
class ColorChooseDialog(context: Context, defColor: Int) : AlertDialog.Builder(context) {
    private val view = getView(context)
    private val sampleView: View = view.findViewById(R.id.view_sample)
    private val etColor: EditText = view.findViewById(R.id.et_color)
    private val sbColorR: SeekBar = view.findViewById(R.id.sb_colorR)
    private val sbColorG: SeekBar = view.findViewById(R.id.sb_colorG)
    private val sbColorB: SeekBar = view.findViewById(R.id.sb_colorB)
    private val tvColorR: TextView = view.findViewById(R.id.tv_colorR)
    private val tvColorG: TextView = view.findViewById(R.id.tv_colorG)
    private val tvColorB: TextView = view.findViewById(R.id.tv_colorB)
    val color: Int
        get() = Color.rgb(sbColorR.progress, sbColorG.progress, sbColorB.progress)

    private fun getView(context: Context): View {
        val layout = XposedInit.moduleRes!!.getLayout(R.layout.dialog_color_choose)
        val inflater = LayoutInflater.from(context)
        return inflater.inflate(layout, null)
    }

    private fun setEditTextListener() {
        etColor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                updateValue(handleUnknownColor(s.toString()))
            }

            override fun afterTextChanged(s: Editable) {}
        })
    }

    private fun setSeekBarListener() {
        val listener: OnSeekBarChangeListener = object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val color = Color.rgb(sbColorR.progress, sbColorG.progress, sbColorB.progress)
                    etColor.setText(String.format("%06X", 0xFFFFFF and color))
                }
                tvColorR.text = sbColorR.progress.toString()
                tvColorG.text = sbColorG.progress.toString()
                tvColorB.text = sbColorB.progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        }
        sbColorR.setOnSeekBarChangeListener(listener)
        sbColorG.setOnSeekBarChangeListener(listener)
        sbColorB.setOnSeekBarChangeListener(listener)
    }

    private fun updateValue(color: Int) {
        sampleView.setBackgroundColor(color)
        val progressR = Color.red(color)
        val progressG = Color.green(color)
        val progressB = Color.blue(color)
        sbColorR.progress = progressR
        sbColorG.progress = progressG
        sbColorB.progress = progressB
        tvColorR.text = progressR.toString()
        tvColorG.text = progressG.toString()
        tvColorB.text = progressB.toString()
    }

    private fun handleUnknownColor(color: String): Int {
        return try {
            Color.parseColor("#$color")
        } catch (e: IllegalArgumentException) {
            Color.BLACK
        }
    }

    init {
        setView(view)
        setEditTextListener()
        setSeekBarListener()
        updateValue(defColor)
        etColor.setText(String.format("%06X", 0xFFFFFF and defColor))
        setTitle("自选颜色")
        setNegativeButton("取消", null)
    }
}
