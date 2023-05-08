package me.iacn.biliroaming

import android.app.Activity
import android.content.SharedPreferences
import android.view.inputmethod.EditorInfo
import android.widget.*
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.dp
import me.iacn.biliroaming.utils.migrateHomeFilterPrefsIfNeeded

class HomeFilterDialog(activity: Activity, prefs: SharedPreferences) :
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

        val applyToRelateSwitch = switchPrefsItem(string(R.string.apply_to_relate_title))
            .let { root.addView(it.first); it.second }
        applyToRelateSwitch.isChecked = prefs.getBoolean("home_filter_apply_to_relate", false)

        root.addView(textInputTitle(string(R.string.hide_low_play_count_recommend_summary)))
        val lowPlayCountInput = textInputItem(string(R.string.hide_low_play_count_recommend_title))
            .let { root.addView(it.first); it.second }
        prefs.getLong("hide_low_play_count_recommend_limit", 0).takeIf { it > 0 }
            ?.let { lowPlayCountInput.setText(it.toString()) }

        root.addView(textInputTitle(string(R.string.hide_duration_recommend_summary)))
        val shortDurationInput = textInputItem(string(R.string.hide_short_duration_recommend_title))
            .let { root.addView(it.first); it.second }
        val longDurationInput = textInputItem(string(R.string.hide_long_duration_recommend_title))
            .let { root.addView(it.first); it.second }
        prefs.getInt("hide_short_duration_recommend_limit", 0).takeIf { it > 0 }
            ?.let { shortDurationInput.setText(it.toString()) }
        prefs.getInt("hide_long_duration_recommend_limit", 0).takeIf { it > 0 }
            ?.let { longDurationInput.setText(it.toString()) }

        root.addView(textInputTitle(string(R.string.keywords_filter_recommend_summary)))
        val (titleGroup, titleRegexModeSwitch) = root.addKeywordGroup(
            string(R.string.keyword_group_name_title), showRegex = true
        )
        titleRegexModeSwitch.isChecked = prefs.getBoolean("home_filter_title_regex_mode", false)
        val (reasonGroup, reasonRegexModeSwitch) = root.addKeywordGroup(
            string(R.string.keyword_group_name_rcmd_reason), showRegex = true
        )
        reasonRegexModeSwitch.isChecked = prefs.getBoolean("home_filter_reason_regex_mode", false)
        val uidGroup = root.addKeywordGroup(
            string(R.string.keyword_group_name_uid),
            inputType = EditorInfo.TYPE_CLASS_NUMBER
        ).first
        val (upGroup, upRegexModeSwitch) = root.addKeywordGroup(
            string(R.string.keyword_group_name_up), showRegex = true
        )
        upRegexModeSwitch.isChecked = prefs.getBoolean("home_filter_up_regex_mode", false)
        val categoryGroup = root.addKeywordGroup(string(R.string.keyword_group_name_category)).first
        val channelGroup = root.addKeywordGroup(string(R.string.keyword_group_name_channel)).first
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

            val titles = titleGroup.getKeywords()
            val titleRegexMode = titleRegexModeSwitch.isChecked
            if (titleRegexMode && titles.runCatching { forEach { it.toRegex() } }.isFailure) {
                Log.toast(string(R.string.invalid_regex), force = true)
                return@setPositiveButton
            }
            val reasons = reasonGroup.getKeywords()
            val reasonRegexMode = reasonRegexModeSwitch.isChecked
            if (reasonRegexMode && reasons.runCatching { forEach { it.toRegex() } }.isFailure) {
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
                putLong("hide_low_play_count_recommend_limit", lowPlayCount)
                putInt("hide_short_duration_recommend_limit", shortDuration)
                putInt("hide_long_duration_recommend_limit", longDuration)
                putStringSet("home_filter_keywords_title", titles)
                putStringSet("home_filter_keywords_reason", reasons)
                putStringSet("home_filter_keywords_uid", uidGroup.getKeywords())
                putStringSet("home_filter_keywords_up", ups)
                putStringSet("home_filter_keywords_category", categoryGroup.getKeywords())
                putStringSet("home_filter_keywords_channel", channelGroup.getKeywords())
                putBoolean("home_filter_title_regex_mode", titleRegexMode)
                putBoolean("home_filter_reason_regex_mode", reasonRegexMode)
                putBoolean("home_filter_up_regex_mode", upRegexMode)
                putBoolean("home_filter_apply_to_relate", applyToRelateSwitch.isChecked)
            }.apply()

            Log.toast(string(R.string.prefs_save_success_and_reboot))
        }
        setNegativeButton(android.R.string.cancel, null)

        root.setPadding(16.dp, 10.dp, 16.dp, 10.dp)

        setView(scrollView)
    }
}
