@file:Suppress("NOTHING_TO_INLINE")

package me.iacn.biliroaming

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.children
import me.iacn.biliroaming.utils.migrateHomeFilterPrefsIfNeeded
import kotlin.math.roundToInt

class HomeFilterDialog(val activity: Activity, prefs: SharedPreferences) :
    AlertDialog.Builder(activity) {
    init {
        migrateHomeFilterPrefsIfNeeded()
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

        root.addView(TextView(context).apply {
            text = string(R.string.hide_low_play_count_recommend_summary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18F)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
        val lowPlayCountInput = textInputItem(string(R.string.hide_low_play_count_recommend_title))
            .let {
                root.addView(it.first)
                it.second
            }
        prefs.getLong("hide_low_play_count_recommend_limit", 0).takeIf { it > 0 }
            ?.let { lowPlayCountInput.setText(it.toString()) }

        root.addView(TextView(context).apply {
            text = string(R.string.hide_duration_recommend_summary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18F)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
        val shortDurationInput = textInputItem(string(R.string.hide_short_duration_recommend_title))
            .let {
                root.addView(it.first)
                it.second
            }
        val longDurationInput = textInputItem(string(R.string.hide_long_duration_recommend_title))
            .let {
                root.addView(it.first)
                it.second
            }
        prefs.getInt("hide_short_duration_recommend_limit", 0).takeIf { it > 0 }
            ?.let { shortDurationInput.setText(it.toString()) }
        prefs.getInt("hide_long_duration_recommend_limit", 0).takeIf { it > 0 }
            ?.let { longDurationInput.setText(it.toString()) }

        root.addView(TextView(context).apply {
            text = string(R.string.keywords_filter_recommend_summary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18F)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
        val titleGroup = root.addKeywordGroup(string(R.string.keyword_group_name_title))
        val reasonGroup = root.addKeywordGroup(string(R.string.keyword_group_name_rcmd_reason))
        val uidGroup = root.addKeywordGroup(
            string(R.string.keyword_group_name_uid),
            EditorInfo.TYPE_CLASS_NUMBER
        )
        val upGroup = root.addKeywordGroup(string(R.string.keyword_group_name_up))
        val categoryGroup = root.addKeywordGroup(string(R.string.keyword_group_name_category))
        val channelGroup = root.addKeywordGroup(string(R.string.keyword_group_name_channel))
        prefs.getStringSet("home_filter_keywords_title", null)?.forEach {
            titleGroup.addView(keywordInputItem(titleGroup, it).first)
        }
        prefs.getStringSet("home_filter_keywords_reason", null)?.forEach {
            reasonGroup.addView(keywordInputItem(reasonGroup, it).first)
        }
        prefs.getStringSet("home_filter_keywords_uid", null)?.forEach {
            uidGroup.addView(keywordInputItem(uidGroup, it, EditorInfo.TYPE_CLASS_NUMBER).first)
        }
        prefs.getStringSet("home_filter_keywords_up", null)?.forEach {
            upGroup.addView(keywordInputItem(upGroup, it).first)
        }
        prefs.getStringSet("home_filter_keywords_category", null)?.forEach {
            categoryGroup.addView(keywordInputItem(categoryGroup, it).first)
        }
        prefs.getStringSet("home_filter_keywords_channel", null)?.forEach {
            channelGroup.addView(keywordInputItem(channelGroup, it).first)
        }

        setTitle(string(R.string.home_filter_title))

        setPositiveButton(android.R.string.ok) { _, _ ->
            val lowPlayCount = lowPlayCountInput.text.toString().toLongOrNull() ?: 0
            val shortDuration = shortDurationInput.text.toString().toIntOrNull() ?: 0
            val longDuration = longDurationInput.text.toString().toIntOrNull() ?: 0

            fun getKeywords(viewGroup: ViewGroup) = viewGroup.children
                .filterIsInstance<ViewGroup>()
                .flatMap { it.children }
                .filterIsInstance<EditText>()
                .map { it.text.toString().trim() }
                .filter { it.isNotEmpty() }
                .toSet()

            prefs.edit().apply {
                putLong("hide_low_play_count_recommend_limit", lowPlayCount)
                putInt("hide_short_duration_recommend_limit", shortDuration)
                putInt("hide_long_duration_recommend_limit", longDuration)
                putStringSet("home_filter_keywords_title", getKeywords(titleGroup))
                putStringSet("home_filter_keywords_reason", getKeywords(reasonGroup))
                putStringSet("home_filter_keywords_uid", getKeywords(uidGroup))
                putStringSet("home_filter_keywords_up", getKeywords(upGroup))
                putStringSet("home_filter_keywords_category", getKeywords(categoryGroup))
                putStringSet("home_filter_keywords_channel", getKeywords(channelGroup))
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

    private inline fun string(resId: Int) = XposedInit.moduleRes.getString(resId)

    private fun textInputItem(
        name: String,
        type: Int = EditorInfo.TYPE_CLASS_NUMBER
    ): Pair<LinearLayout, EditText> {
        val layout = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val textView = TextView(context).apply {
            text = name
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val editText = EditText(context).apply {
            setSingleLine()
            inputType = type
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { weight = 1F }
        }
        layout.addView(textView)
        layout.addView(editText)
        return Pair(layout, editText)
    }

    private fun keywordTypeHeader(
        group: ViewGroup,
        name: String,
        onClick: (v: View) -> Unit,
    ): Pair<LinearLayout, Button> {
        val layout = LinearLayout(context).apply {
            gravity = Gravity.START
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val nameView = TextView(context).apply {
            text = name
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16F)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            minWidth = 64.dp
        }
        val addView = Button(context).apply {
            text = string(R.string.keyword_group_add)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener(onClick)
        }
        val clearView = Button(context).apply {
            text = string(R.string.keyword_group_clear)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 6.dp
            }
            visibility = View.INVISIBLE
            setOnClickListener { group.removeAllViews() }
        }
        layout.addView(nameView)
        layout.addView(addView)
        layout.addView(clearView)
        return Pair(layout, clearView)
    }

    private fun keywordInputItem(
        parent: ViewGroup,
        keyword: String = "",
        type: Int = EditorInfo.TYPE_CLASS_TEXT,
    ): Pair<LinearLayout, EditText> {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val editText = EditText(context).apply {
            inputType = type
            setText(keyword)
            setSingleLine()
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { weight = 1F }
        }
        val button = Button(context).apply {
            text = string(R.string.keyword_group_delete)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { parent.removeView(layout) }
        }
        layout.addView(editText)
        layout.addView(button)
        return Pair(layout, editText)
    }

    private fun LinearLayout.addKeywordGroup(
        name: String,
        inputType: Int = EditorInfo.TYPE_CLASS_TEXT
    ): ViewGroup {
        val group = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val clearButton = keywordTypeHeader(group, name) {
            keywordInputItem(group, type = inputType).let {
                group.addView(it.first)
                it.second.requestFocus()
            }
        }.let {
            addView(it.first)
            it.second
        }
        group.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View, child: View) {
                if (group.childCount == 0) {
                    clearButton.visibility = View.INVISIBLE
                } else {
                    clearButton.visibility = View.VISIBLE
                }
            }

            override fun onChildViewRemoved(parent: View, child: View) {
                if (group.childCount == 0) {
                    clearButton.visibility = View.INVISIBLE
                } else {
                    clearButton.visibility = View.VISIBLE
                }
            }
        })
        addView(group)
        return group
    }
}
