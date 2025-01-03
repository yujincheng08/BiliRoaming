package me.iacn.biliroaming

import android.app.Activity
import android.content.SharedPreferences
import android.view.inputmethod.EditorInfo
import android.widget.*
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.dp

class DynamicFilterDialog(activity: Activity, prefs: SharedPreferences) :
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

        val preferVideoTabSwitch = switchPrefsItem(
            string(R.string.customize_dynamic_prefer_video_tab)
        ).let { root.addView(it.first); it.second }
        preferVideoTabSwitch.isChecked = prefs.getBoolean("prefer_video_tab", false)

        val rmCityTabSwitch = switchPrefsItem(string(R.string.purify_city_title))
            .let { root.addView(it.first); it.second }
        rmCityTabSwitch.isChecked = prefs.getBoolean("purify_city", false)

        val rmCampusTabSwitch = switchPrefsItem(string(R.string.purify_campus_title))
            .let { root.addView(it.first); it.second }
        rmCampusTabSwitch.isChecked = prefs.getBoolean("purify_campus", false)

        val rmTopicOfAllSwitch = switchPrefsItem(
            string(R.string.customize_dynamic_all_rm_topic_title)
        ).let { root.addView(it.first); it.second }
        rmTopicOfAllSwitch.isChecked = prefs.getBoolean("customize_dynamic_all_rm_topic", false)

        val rmUpOfAllSwitch = switchPrefsItem(
            string(R.string.customize_dynamic_all_rm_up_title)
        ).let { root.addView(it.first); it.second }
        rmUpOfAllSwitch.isChecked = prefs.getBoolean("customize_dynamic_all_rm_up", false)

        val rmLiveOfAllSwitch = switchPrefsItem(
            string(R.string.customize_dynamic_all_rm_live_title)
        ).let { root.addView(it.first); it.second }
        rmLiveOfAllSwitch.isChecked = prefs.getBoolean("customize_dynamic_all_rm_live", false)

        val rmUpOfVideoSwitch = switchPrefsItem(
            string(R.string.customize_dynamic_video_rm_up_title)
        ).let { root.addView(it.first); it.second }
        rmUpOfVideoSwitch.isChecked = prefs.getBoolean("customize_dynamic_video_rm_up", false)

        val filterApplyToVideoSwitch = switchPrefsItem(
            string(R.string.customize_dynamic_filter_apply_to_video)
        ).let { root.addView(it.first); it.second }
        filterApplyToVideoSwitch.isChecked = prefs.getBoolean("filter_apply_to_video", false)

        val rmBlockedSwitch = switchPrefsItem(
            string(R.string.customize_dynamic_rm_blocked_title)
        ).let { root.addView(it.first); it.second }
        rmBlockedSwitch.isChecked = prefs.getBoolean("customize_dynamic_rm_blocked", false)

        val rmAdLinkSwitch = switchPrefsItem(
            string(R.string.customize_dynamic_rm_ad_link_title)
        ).let { root.addView(it.first); it.second }
        rmAdLinkSwitch.isChecked = prefs.getBoolean("customize_dynamic_rm_ad_link", false)

        val byTypeTitle = categoryTitle(string(R.string.customize_dynamic_by_type))
        root.addView(byTypeTitle)

        val gridLayout = GridLayout(context).apply {
            columnCount = 4
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(gridLayout)
        val dynamicTypes = context.resources.getStringArray(R.array.dynamic_entries).zip(
            context.resources.getStringArray(R.array.dynamic_values)
        )
        val colSpec = fun(colWeight: Float) = GridLayout.spec(GridLayout.UNDEFINED, colWeight)
        val rowSpec = { GridLayout.spec(GridLayout.UNDEFINED) }
        val typePrefs = prefs.getStringSet("customize_dynamic_type", null) ?: setOf()
        dynamicTypes.forEach { item ->
            val checkBox = CheckBox(context).apply {
                tag = item.second
                layoutParams = GridLayout.LayoutParams(rowSpec(), colSpec(1F))
                isChecked = typePrefs.contains(item.second)
            }
            val textView = TextView(context).apply {
                text = item.first
                layoutParams = GridLayout.LayoutParams(rowSpec(), colSpec(2F))
                setSingleLine()
            }
            gridLayout.addView(checkBox)
            gridLayout.addView(textView)
        }

        val byKeywordTitle = categoryTitle(string(R.string.customize_dynamic_by_keyword))
        root.addView(byKeywordTitle)

        val (contentGroup, contentRegexSwitch) = root.addKeywordGroup(
            string(R.string.keyword_group_name_content), 40.dp, true
        )
        contentRegexSwitch.isChecked = prefs.getBoolean("dynamic_content_regex_mode", false)
        val upNameGroup = root.addKeywordGroup(string(R.string.keyword_group_name_up), 40.dp).first
        val uidGroup = root.addKeywordGroup(
            string(R.string.keyword_group_name_uid),
            40.dp,
            inputType = EditorInfo.TYPE_CLASS_NUMBER
        ).first
        prefs.getStringSet("customize_dynamic_keyword_content", null)?.forEach {
            contentGroup.addView(keywordInputItem(contentGroup, it).first)
        }
        prefs.getStringSet("customize_dynamic_keyword_upname", null)?.forEach {
            upNameGroup.addView(keywordInputItem(upNameGroup, it).first)
        }
        prefs.getStringSet("customize_dynamic_keyword_uid", null)?.forEach {
            uidGroup.addView(keywordInputItem(uidGroup, it, EditorInfo.TYPE_CLASS_NUMBER).first)
        }

        setTitle(string(R.string.customize_dynamic_title))

        setPositiveButton(android.R.string.ok) { _, _ ->
            val typeValues = buildSet {
                for (i in 0 until gridLayout.childCount step 2) {
                    val view = gridLayout.getChildAt(i) as CheckBox
                    if (view.isChecked) add(view.tag.toString())
                }
            }

            val contents = contentGroup.getKeywords()
            val contentRegexMode = contentRegexSwitch.isChecked
            if (contentRegexMode && contents.runCatching { forEach { it.toRegex() } }.isFailure) {
                Log.toast(string(R.string.invalid_regex), force = true)
                return@setPositiveButton
            }

            prefs.edit().apply {
                putBoolean("prefer_video_tab", preferVideoTabSwitch.isChecked)
                putBoolean("purify_city", rmCityTabSwitch.isChecked)
                putBoolean("purify_campus", rmCampusTabSwitch.isChecked)
                putBoolean("customize_dynamic_all_rm_topic", rmTopicOfAllSwitch.isChecked)
                putBoolean("customize_dynamic_all_rm_up", rmUpOfAllSwitch.isChecked)
                putBoolean("customize_dynamic_all_rm_live", rmLiveOfAllSwitch.isChecked)
                putBoolean("customize_dynamic_video_rm_up", rmUpOfVideoSwitch.isChecked)
                putBoolean("filter_apply_to_video", filterApplyToVideoSwitch.isChecked)
                putBoolean("customize_dynamic_rm_blocked", rmBlockedSwitch.isChecked)
                putBoolean("customize_dynamic_rm_ad_link", rmAdLinkSwitch.isChecked)
                putStringSet("customize_dynamic_type", typeValues)
                putStringSet("customize_dynamic_keyword_content", contents)
                putStringSet("customize_dynamic_keyword_upname", upNameGroup.getKeywords())
                putStringSet("customize_dynamic_keyword_uid", uidGroup.getKeywords())
                putBoolean("dynamic_content_regex_mode", contentRegexMode)
            }.apply()
            Log.toast(string(R.string.prefs_save_success_and_reboot))
        }
        setNegativeButton(android.R.string.cancel, null)

        root.setPadding(16.dp, 10.dp, 16.dp, 10.dp)

        setView(scrollView)
    }
}
