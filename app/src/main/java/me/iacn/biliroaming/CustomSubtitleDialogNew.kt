package me.iacn.biliroaming

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.text.InputType
import android.text.TextUtils
import android.text.method.DigitsKeyListener
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.addBackgroundRipple
import kotlin.math.roundToInt

class CustomSubtitleDialogNew(val activity: Activity, prefs: SharedPreferences) :
    AlertDialog.Builder(activity) {
    init {
        val scrollView = ScrollView(context).apply {
            scrollBarStyle = ScrollView.SCROLLBARS_OUTSIDE_OVERLAY
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        scrollView.addView(root)

        val removeBgSwitch = string(R.string.custom_subtitle_remove_bg).let {
            switchPrefsItem(it).let { p -> root.addView(p.first); p.second }
        }
        removeBgSwitch.isChecked = prefs.getBoolean("custom_subtitle_remove_bg", true)
        val boldSwitch = string(R.string.custom_subtitle_bold).let {
            switchPrefsItem(it).let { p -> root.addView(p.first); p.second }
        }
        boldSwitch.isChecked = prefs.getBoolean("custom_subtitle_bold", true)
        val fillColorInput = colorPickItem(
            string(R.string.custom_subtitle_fill_color),
            prefs.getString("custom_subtitle_fill_color", null) ?: "FFFFFFFF"
        ).let { p -> root.addView(p.first); p.second }
        val strokeColorInput = colorPickItem(
            string(R.string.custom_subtitle_stroke_color),
            prefs.getString("custom_subtitle_stroke_color", null) ?: "FF000000"
        ).let { p -> root.addView(p.first); p.second }
        val strokeWidthInput = textInputItem(
            string(R.string.custom_subtitle_stroke_width),
            prefs.getFloat("custom_subtitle_stroke_width", 5.0F).toString(),
            type = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        ).let { p -> root.addView(p.first); p.second }

        setTitle(string(R.string.custom_subtitle_title))

        setPositiveButton(android.R.string.ok) { _, _ ->
            prefs.edit().apply {
                putBoolean("custom_subtitle_remove_bg", removeBgSwitch.isChecked)
                putBoolean("custom_subtitle_bold", boldSwitch.isChecked)
                putString(
                    "custom_subtitle_fill_color",
                    fillColorInput.text.toString().uppercase().ifEmpty { "FFFFFFFF" }
                )
                putString(
                    "custom_subtitle_stroke_color",
                    strokeColorInput.text.toString().uppercase().ifEmpty { "FF000000" }
                )
                putFloat(
                    "custom_subtitle_stroke_width",
                    strokeWidthInput.text.toString().toFloatOrNull() ?: 5.0F
                )
            }.apply()
            Log.toast(string(R.string.prefs_save_success_and_reboot))
        }
        setNegativeButton(android.R.string.cancel, null)

        root.setPadding(16.dp, 10.dp, 16.dp, 10.dp)

        setView(scrollView)
    }

    private val Int.dp
        inline get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            toFloat(),
            context.resources.displayMetrics
        ).roundToInt()

    private fun string(resId: Int) = context.getString(resId)

    private fun switchPrefsItem(title: String): Pair<LinearLayout, Switch> {
        val layout = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val titleView = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16F)
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 8.dp, 0, 8.dp)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1F
                marginEnd = 10.dp
            }
        }
        val switcher = Switch(context).apply {
            isClickable = false
            isSoundEffectsEnabled = false
            isHapticFeedbackEnabled = false
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        layout.setOnClickListener { switcher.toggle() }
        layout.addBackgroundRipple()
        layout.addView(titleView)
        layout.addView(switcher)
        return Pair(layout, switcher)
    }

    private fun colorPickItem(
        name: String, defColor: String
    ): Pair<LinearLayout, EditText> {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val textView = TextView(context).apply {
            text = name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16F)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            minWidth = 60.dp
        }
        val editText = EditText(context).apply {
            inputType = EditorInfo.TYPE_CLASS_TEXT
            setText(defColor)
            setSingleLine()
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { weight = 1F }
            keyListener = DigitsKeyListener.getInstance("0123456789ABCDEFabcdef")
        }
        val button = Button(context).apply {
            text = string(R.string.custom_subtitle_pick_color)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                ARGBColorChooseDialog(
                    activity,
                    Color.parseColor("#${editText.text}")
                ).apply {
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        editText.setText(String.format("%08X", 0xFFFFFFFF.toInt() and color))
                    }
                }.show()
            }
        }
        layout.addView(textView)
        layout.addView(editText)
        layout.addView(button)
        return Pair(layout, editText)
    }

    @Suppress("SameParameterValue")
    private fun textInputItem(
        name: String,
        defText: String,
        type: Int = EditorInfo.TYPE_CLASS_TEXT,
    ): Pair<LinearLayout, EditText> {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val textView = TextView(context).apply {
            text = name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16F)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            minWidth = 60.dp
        }
        val editText = EditText(context).apply {
            inputType = type
            setText(defText)
            setSingleLine()
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { weight = 1F }
        }
        layout.addView(textView)
        layout.addView(editText)
        return Pair(layout, editText)
    }
}
