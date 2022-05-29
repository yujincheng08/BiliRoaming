@file:Suppress("NOTHING_TO_INLINE")

package me.iacn.biliroaming

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import me.iacn.biliroaming.XposedInit.Companion.moduleRes
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.addBackgroundRipple
import me.iacn.biliroaming.utils.children
import kotlin.math.roundToInt

class DynamicFilterDialog(val activity: Activity, prefs: SharedPreferences) :
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

        val rmCityTabTitle = string(R.string.purify_city_title)
        val rmCityTabSwitch = switchPrefsItem(rmCityTabTitle).let {
            root.addView(it.first)
            it.second
        }
        rmCityTabSwitch.isChecked = prefs.getBoolean("purify_city", false)

        val rmCampusTabTitle = string(R.string.purify_campus_title)
        val rmCampusTabSwitch = switchPrefsItem(rmCampusTabTitle).let {
            root.addView(it.first)
            it.second
        }
        rmCampusTabSwitch.isChecked = prefs.getBoolean("purify_campus", false)

        val rmTopicOfAllTitle = string(R.string.customize_dynamic_all_rm_topic_title)
        val rmTopicOfAllSwitch = switchPrefsItem(rmTopicOfAllTitle).let {
            root.addView(it.first)
            it.second
        }
        rmTopicOfAllSwitch.isChecked = prefs.getBoolean("customize_dynamic_all_rm_topic", false)

        val rmUpOfAllTitle = string(R.string.customize_dynamic_all_rm_up_title)
        val rmUpOfAllSwitch = switchPrefsItem(rmUpOfAllTitle).let {
            root.addView(it.first)
            it.second
        }
        rmUpOfAllSwitch.isChecked = prefs.getBoolean("customize_dynamic_all_rm_up", false)

        val rmUpOfVideoTitle = string(R.string.customize_dynamic_video_rm_up_title)
        val rmUpOfVideoSwitch = switchPrefsItem(rmUpOfVideoTitle).let {
            root.addView(it.first)
            it.second
        }
        rmUpOfVideoSwitch.isChecked = prefs.getBoolean("customize_dynamic_video_rm_up", false)

        val byTypeTitle = TextView(context).apply {
            text = string(R.string.customize_dynamic_by_type)
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18F)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 10.dp
            }
        }
        root.addView(byTypeTitle)

        val gridLayout = GridLayout(context).apply {
            columnCount = 4
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(gridLayout)
        val dynamicTypes = moduleRes.getStringArray(R.array.dynamic_entries).zip(
            moduleRes.getStringArray(R.array.dynamic_values)
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

        val byKeywordTitle = TextView(context).apply {
            text = string(R.string.customize_dynamic_by_keyword)
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18F)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 10.dp
            }
        }
        root.addView(byKeywordTitle)

        val contentGroup = root.addKeywordGroup(string(R.string.keyword_group_name_content))
        val upNameGroup = root.addKeywordGroup(string(R.string.keyword_group_name_up))
        val uidGroup = root.addKeywordGroup(
            string(R.string.keyword_group_name_uid),
            EditorInfo.TYPE_CLASS_NUMBER
        )
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

            fun getKeywords(viewGroup: ViewGroup) = viewGroup.children
                .filterIsInstance<ViewGroup>()
                .flatMap { it.children }
                .filterIsInstance<EditText>()
                .map { it.text.toString().trim() }
                .filter { it.isNotEmpty() }
                .toSet()

            prefs.edit().apply {
                putBoolean("purify_city", rmCityTabSwitch.isChecked)
                putBoolean("purify_campus", rmCampusTabSwitch.isChecked)
                putBoolean("customize_dynamic_all_rm_topic", rmTopicOfAllSwitch.isChecked)
                putBoolean("customize_dynamic_all_rm_up", rmUpOfAllSwitch.isChecked)
                putBoolean("customize_dynamic_video_rm_up", rmUpOfVideoSwitch.isChecked)
                putStringSet("customize_dynamic_type", typeValues)
                putStringSet("customize_dynamic_keyword_content", getKeywords(contentGroup))
                putStringSet("customize_dynamic_keyword_upname", getKeywords(upNameGroup))
                putStringSet("customize_dynamic_keyword_uid", getKeywords(uidGroup))
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

    private inline fun string(resId: Int) = moduleRes.getString(resId)

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
            minWidth = 40.dp
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
