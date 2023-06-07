package me.iacn.biliroaming

import android.app.Activity
import android.content.SharedPreferences
import android.view.inputmethod.EditorInfo
import android.widget.*
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.dp
import me.iacn.biliroaming.utils.migrateHomeFilterPrefsIfNeeded

class PopularFilterDialog(activity: Activity, prefs: SharedPreferences) :
    BaseWidgetDialog(activity) {
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

        val hideTopSwitch = switchPrefsItem((string(R.string.hide_top_entrance_popular_summary)))
            .let { root.addView(it.first); it.second }
        hideTopSwitch.isChecked = prefs.getBoolean("hide_top_entrance_popular", false)
        val hideFollowSwitch = switchPrefsItem((string(R.string.hide_suggest_follow_popular_summary)))
            .let { root.addView(it.first); it.second }
        hideFollowSwitch.isChecked = prefs.getBoolean("hide_suggest_follow_popular", false)

        root.addView(textInputTitle(string(R.string.hide_low_play_count_popular_summary)))
        val lowPlayCountInput = textInputItem(string(R.string.hide_low_play_count_popular_title))
            .let { root.addView(it.first); it.second }
        prefs.getLong("hide_low_play_count_popular_limit", 0).takeIf { it > 0 }
            ?.let { lowPlayCountInput.setText(it.toString()) }

        root.addView(textInputTitle(string(R.string.hide_duration_popular_summary)))
        val shortDurationInput = textInputItem(string(R.string.hide_short_duration_popular_title))
            .let { root.addView(it.first); it.second }
        val longDurationInput = textInputItem(string(R.string.hide_long_duration_popular_title))
            .let { root.addView(it.first); it.second }
        prefs.getInt("hide_short_duration_popular_limit", 0).takeIf { it > 0 }
            ?.let { shortDurationInput.setText(it.toString()) }
        prefs.getInt("hide_long_duration_popular_limit", 0).takeIf { it > 0 }
            ?.let { longDurationInput.setText(it.toString()) }

        root.addView(textInputTitle(string(R.string.keywords_filter_popular_summary)))

        val (titleGroup, titleRegexModeSwitch) = root.addKeywordGroup(
            string(R.string.keyword_group_name_title), showRegex = true
        )
        titleRegexModeSwitch.isChecked = prefs.getBoolean("popular_filter_title_regex_mode", false)

        val (upGroup, upRegexModeSwitch) = root.addKeywordGroup(
            string(R.string.keyword_group_name_up), showRegex = true
        )
        upRegexModeSwitch.isChecked = prefs.getBoolean("popular_filter_up_regex_mode", false)

        prefs.getStringSet("popular_filter_keywords_title", null)?.forEach {
            titleGroup.addView(keywordInputItem(titleGroup, it).first)
        }
        prefs.getStringSet("popular_filter_keywords_up", null)?.forEach {
            upGroup.addView(keywordInputItem(upGroup, it).first)
        }

        setTitle(string(R.string.popular_filter_title))

        setPositiveButton(android.R.string.ok) { _, _ ->
            val hideTop = hideTopSwitch.isChecked
            val hideSuggestFollow = hideFollowSwitch.isChecked
            val lowPlayCount = lowPlayCountInput.text.toString().toLongOrNull() ?: 0
            val shortDuration = shortDurationInput.text.toString().toIntOrNull() ?: 0
            val longDuration = longDurationInput.text.toString().toIntOrNull() ?: 0

            val titles = titleGroup.getKeywords()
            val titleRegexMode = titleRegexModeSwitch.isChecked
            if (titleRegexMode && titles.runCatching { forEach { it.toRegex() } }.isFailure) {
                Log.toast(string(R.string.invalid_regex), force = true)
                return@setPositiveButton
            }
            val ups = upGroup.getKeywords()
            val upRegexMode = upRegexModeSwitch.isChecked
            if (upRegexMode && ups.runCatching { forEach { it.toRegex() } }.isFailure) {
                Log.toast(string(R.string.invalid_regex), force = true)
                return@setPositiveButton
            }

            prefs.edit().apply {
                putBoolean("hide_top_entrance_popular", hideTop)
                putBoolean("hide_suggest_follow_popular", hideSuggestFollow)
                putLong("hide_low_play_count_popular_limit", lowPlayCount)
                putInt("hide_short_duration_popular_limit", shortDuration)
                putInt("hide_long_duration_popular_limit", longDuration)
                putStringSet("popular_filter_keywords_title", titles)
                putStringSet("popular_filter_keywords_up", ups)
                putBoolean("popular_filter_title_regex_mode", titleRegexMode)
                putBoolean("popular_filter_up_regex_mode", upRegexMode)
            }.apply()

            Log.toast(string(R.string.prefs_save_success_and_reboot))
        }
        setNegativeButton(android.R.string.cancel, null)

        root.setPadding(16.dp, 10.dp, 16.dp, 10.dp)

        setView(scrollView)
    }
}
