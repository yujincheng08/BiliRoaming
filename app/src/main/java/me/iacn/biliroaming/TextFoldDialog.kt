package me.iacn.biliroaming

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.TypedValue
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import me.iacn.biliroaming.hook.TextFoldHook
import me.iacn.biliroaming.utils.sPrefs
import kotlin.math.roundToInt

class TextFoldDialog(val activity: Activity, prefs: SharedPreferences) :
    AlertDialog.Builder(activity) {
    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val commentMaxLines =
            prefs.getInt("text_fold_comment_max_lines", TextFoldHook.DEF_COMMENT_MAX_LINES)
        val commentMaxLinesTitle = string(R.string.text_fold_comment_max_lines_title)
        val commentMaxLinesItem = seekBarItem(commentMaxLinesTitle, current = commentMaxLines).let {
            root.addView(it.first)
            it.second
        }
        val dynMaxLines = prefs.getInt("text_fold_dyn_max_lines", TextFoldHook.DEF_DYN_MAX_LINES)
        val dynMaxLinesTitle = string(R.string.text_fold_dyn_max_lines_title)
        val dynMaxLinesItem = seekBarItem(dynMaxLinesTitle, current = dynMaxLines).let {
            root.addView(it.first)
            it.second
        }
        val dynLinesToAll =
            prefs.getInt("text_fold_dyn_lines_to_all", TextFoldHook.DEF_DYN_LINES_TO_ALL)
        val dynLinesToAllTitle = string(R.string.text_fold_dyn_lines_to_all_title)
        val dynLinesToAllItem = seekBarItem(dynLinesToAllTitle, current = dynLinesToAll).let {
            root.addView(it.first)
            it.second
        }

        setTitle(string(R.string.text_fold_title))

        setPositiveButton(android.R.string.ok) { _, _ ->
            sPrefs.edit().apply {
                putInt(
                    "text_fold_comment_max_lines",
                    commentMaxLinesItem.progress.takeIf { it != 0 }
                        ?: TextFoldHook.DEF_COMMENT_MAX_LINES
                )
                putInt(
                    "text_fold_dyn_max_lines",
                    dynMaxLinesItem.progress.takeIf { it != 0 }
                        ?: TextFoldHook.DEF_DYN_MAX_LINES
                )
                putInt(
                    "text_fold_dyn_lines_to_all",
                    dynLinesToAllItem.progress.takeIf { it != 0 }
                        ?: TextFoldHook.DEF_DYN_LINES_TO_ALL
                )
            }.apply()
        }

        setNegativeButton(android.R.string.cancel, null)

        root.setPadding(16.dp, 10.dp, 16.dp, 10.dp)

        setView(root)
    }

    private val Int.dp
        inline get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            toFloat(),
            context.resources.displayMetrics
        ).roundToInt()

    @Suppress("NOTHING_TO_INLINE")
    private inline fun string(resId: Int) = context.getString(resId)

    private fun seekBarItem(
        name: String,
        current: Int,
        indicator: String = string(R.string.text_fold_line),
        max: Int = 100,
    ): Pair<LinearLayout, SeekBar> {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 8.dp, 0, 8.dp)
        }
        val nameView = TextView(activity).apply {
            text = name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16F)
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val progressView = TextView(activity).apply {
            text = indicator.format(current)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14F)
            TypedValue().apply {
                context.theme.resolveAttribute(android.R.attr.textColorSecondary, this, true)
            }.data.let { setTextColor(it) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val seekBarView = SeekBar(activity).apply {
            progress = current
            this.max = max
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dp
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    progressView.text = indicator.format(progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        layout.addView(nameView)
        layout.addView(progressView)
        layout.addView(seekBarView)
        return Pair(layout, seekBarView)
    }
}
