package me.iacn.biliroaming

import android.app.Activity
import android.content.SharedPreferences
import android.view.inputmethod.EditorInfo
import android.widget.*
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.dp

class CommentFilterDialog(activity: Activity, prefs: SharedPreferences) :
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

        val (contentGroup, contentRegexSwitch) = root.addKeywordGroup(
            string(R.string.keyword_group_name_content), 40.dp, true
        )
        contentRegexSwitch.isChecked = prefs.getBoolean("comment_filter_content_regex_mode", false)
        val upNameGroup = root.addKeywordGroup(string(R.string.keyword_group_name_at_up), 40.dp).first
        val uidGroup = root.addKeywordGroup(
            string(R.string.keyword_group_name_at_uid),
            40.dp,
            inputType = EditorInfo.TYPE_CLASS_NUMBER
        ).first
        prefs.getStringSet("comment_filter_keyword_content", null)?.forEach {
            contentGroup.addView(keywordInputItem(contentGroup, it).first)
        }
        prefs.getStringSet("comment_filter_keyword_at_upname", null)?.forEach {
            upNameGroup.addView(keywordInputItem(upNameGroup, it).first)
        }
        prefs.getStringSet("comment_filter_keyword_at_uid", null)?.forEach {
            uidGroup.addView(keywordInputItem(uidGroup, it, EditorInfo.TYPE_CLASS_NUMBER).first)
        }

        val blockAtCommentSwitch = switchPrefsItem(
            string(R.string.comment_filter_block_at_comment_title)
        ).let { root.addView(it.first); it.second }
        blockAtCommentSwitch.isChecked = prefs.getBoolean("comment_filter_block_at_comment", false)

        setTitle(string(R.string.filter_comment_title))

        setPositiveButton(android.R.string.ok) { _, _ ->

            val contents = contentGroup.getKeywords()
            val contentRegexMode = contentRegexSwitch.isChecked
            if (contentRegexMode && contents.runCatching { forEach { it.toRegex() } }.isFailure) {
                Log.toast(string(R.string.invalid_regex), force = true)
                return@setPositiveButton
            }

            prefs.edit().apply {
                putStringSet("comment_filter_keyword_content", contents)
                putStringSet("comment_filter_keyword_at_upname", upNameGroup.getKeywords())
                putStringSet("comment_filter_keyword_at_uid", uidGroup.getKeywords())
                putBoolean("comment_filter_content_regex_mode", contentRegexMode)
                putBoolean("comment_filter_block_at_comment", blockAtCommentSwitch.isChecked)
            }.apply()
            Log.toast(string(R.string.prefs_save_success_and_reboot))
        }
        setNegativeButton(android.R.string.cancel, null)

        root.setPadding(16.dp, 10.dp, 16.dp, 10.dp)

        setView(scrollView)
    }
}
