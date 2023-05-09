package me.iacn.biliroaming

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import me.iacn.biliroaming.utils.inflateLayout

/**
 * Created by iAcn on 2019/7/14
 * Email i@iacn.me
 *
 * Copy & Modify from ColorChooseDialog on 2021/7/6
 */
class ARGBColorChooseDialog(context: Context, defColor: Int) : AlertDialog.Builder(context) {
    private val view = context.inflateLayout(R.layout.dialog_argb_color_choose)
    private val sampleView: View = view.findViewById(R.id.view_sample2)
    private val etColor: EditText = view.findViewById(R.id.et_color2)
    private val sbColorA: SeekBar = view.findViewById(R.id.sb_colorA2)
    private val sbColorR: SeekBar = view.findViewById(R.id.sb_colorR2)
    private val sbColorG: SeekBar = view.findViewById(R.id.sb_colorG2)
    private val sbColorB: SeekBar = view.findViewById(R.id.sb_colorB2)
    private val tvColorA: TextView = view.findViewById(R.id.tv_colorA2)
    private val tvColorR: TextView = view.findViewById(R.id.tv_colorR2)
    private val tvColorG: TextView = view.findViewById(R.id.tv_colorG2)
    private val tvColorB: TextView = view.findViewById(R.id.tv_colorB2)
    val color: Int
        get() = Color.argb(
            sbColorA.progress,
            sbColorR.progress,
            sbColorG.progress,
            sbColorB.progress
        )

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
                    val color = Color.argb(
                        sbColorA.progress,
                        sbColorR.progress,
                        sbColorG.progress,
                        sbColorB.progress
                    )
                    etColor.setText(String.format("%08X", 0xFFFFFFFF.toInt() and color))
                }
                tvColorA.text = sbColorA.progress.toString()
                tvColorR.text = sbColorR.progress.toString()
                tvColorG.text = sbColorG.progress.toString()
                tvColorB.text = sbColorB.progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        }
        sbColorA.setOnSeekBarChangeListener(listener)
        sbColorR.setOnSeekBarChangeListener(listener)
        sbColorG.setOnSeekBarChangeListener(listener)
        sbColorB.setOnSeekBarChangeListener(listener)
    }

    private fun updateValue(color: Int) {
        sampleView.setBackgroundColor(color)
        val progressA = Color.alpha(color)
        val progressR = Color.red(color)
        val progressG = Color.green(color)
        val progressB = Color.blue(color)
        sbColorA.progress = progressA
        sbColorR.progress = progressR
        sbColorG.progress = progressG
        sbColorB.progress = progressB
        tvColorA.text = progressA.toString()
        tvColorR.text = progressR.toString()
        tvColorG.text = progressG.toString()
        tvColorB.text = progressB.toString()
    }

    private fun handleUnknownColor(color: String) =
        try {
            Color.parseColor("#$color")
        } catch (e: IllegalArgumentException) {
            Color.BLACK
        }

    init {
        setView(view)
        setEditTextListener()
        setSeekBarListener()
        updateValue(defColor)
        etColor.setText(String.format("%08X", 0xFFFFFFFF.toInt() and defColor))
        setTitle("拾色器")
        setNegativeButton("取消", null)
    }
}
