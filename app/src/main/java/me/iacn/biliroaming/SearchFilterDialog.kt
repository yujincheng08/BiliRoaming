package me.iacn.biliroaming

import android.app.Activity
import android.content.SharedPreferences
import android.view.inputmethod.EditorInfo
import android.widget.*
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.dp

class SearchFilterDialog(activity: Activity, prefs: SharedPreferences) :
    BaseWidgetDialog(activity) {
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

        val categoryVideoTitle = categoryTitle(string(R.string.filter_search_video))
        root.addView(categoryVideoTitle)

        val (contentGroup, contentRegexSwitch) = root.addKeywordGroup(
            string(R.string.keyword_group_name_content), 40.dp, true
        )
        contentRegexSwitch.isChecked = prefs.getBoolean("search_filter_content_regex_mode", false)
        val upNameGroup = root.addKeywordGroup(string(R.string.keyword_group_name_up), 40.dp).first
        val uidGroup = root.addKeywordGroup(
            string(R.string.keyword_group_name_uid),
            40.dp,
            inputType = EditorInfo.TYPE_CLASS_NUMBER
        ).first
        prefs.getStringSet("search_filter_keyword_content", null)?.forEach {
            contentGroup.addView(keywordInputItem(contentGroup, it).first)
        }
        prefs.getStringSet("search_filter_keyword_upname", null)?.forEach {
            upNameGroup.addView(keywordInputItem(upNameGroup, it).first)
        }
        prefs.getStringSet("search_filter_keyword_uid", null)?.forEach {
            uidGroup.addView(keywordInputItem(uidGroup, it, EditorInfo.TYPE_CLASS_NUMBER).first)
        }

        val removeRelatePromoteSwitch = switchPrefsItem(string(R.string.filter_search_remove_relate_promote))
            .let { root.addView(it.first); it.second }
        removeRelatePromoteSwitch.isChecked = prefs.getBoolean("search_filter_remove_relate_promote", false)

        setTitle(string(R.string.filter_search_title))

        setPositiveButton(android.R.string.ok) { _, _ ->

            val contents = contentGroup.getKeywords()
            val contentRegexMode = contentRegexSwitch.isChecked
            if (contentRegexMode && contents.runCatching { forEach { it.toRegex() } }.isFailure) {
                Log.toast(string(R.string.invalid_regex), force = true)
                return@setPositiveButton
            }

            prefs.edit().apply {
                putStringSet("search_filter_keyword_content", contents)
                putStringSet("search_filter_keyword_upname", upNameGroup.getKeywords())
                putStringSet("search_filter_keyword_uid", uidGroup.getKeywords())
                putBoolean("search_filter_content_regex_mode", contentRegexMode)
                putBoolean("search_filter_remove_relate_promote", removeRelatePromoteSwitch.isChecked)
            }.apply()
            Log.toast(string(R.string.prefs_save_success_and_reboot))
        }
        setNegativeButton(android.R.string.cancel, null)

        root.setPadding(16.dp, 10.dp, 16.dp, 10.dp)

        setView(scrollView)
    }
}
