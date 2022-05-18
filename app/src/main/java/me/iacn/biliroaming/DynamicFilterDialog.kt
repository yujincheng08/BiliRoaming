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
                setMargins(0, 10.dp, 0, 0)
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
                setMargins(0, 10.dp, 0, 0)
            }
        }
        root.addView(byKeywordTitle)

        val contentGroup = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(keywordTypeHeader(contentGroup, string(R.string.customize_dynamic_content)) {
            keywordInputItem(contentGroup).let {
                contentGroup.addView(it.first)
                it.second.requestFocus()
            }
        })
        root.addView(contentGroup)
        val contentPrefs = prefs.getStringSet("customize_dynamic_keyword_content", null) ?: setOf()
        contentPrefs.forEach {
            contentGroup.addView(keywordInputItem(contentGroup, it).first)
        }

        val upNameGroup = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(keywordTypeHeader(upNameGroup, string(R.string.customize_dynamic_up)) {
            keywordInputItem(upNameGroup).let {
                upNameGroup.addView(it.first)
                it.second.requestFocus()
            }
        })
        root.addView(upNameGroup)
        val upNamePrefs = prefs.getStringSet("customize_dynamic_keyword_upname", null) ?: setOf()
        upNamePrefs.forEach {
            upNameGroup.addView(keywordInputItem(upNameGroup, it).first)
        }

        val uidGroup = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(keywordTypeHeader(uidGroup, string(R.string.customize_dynamic_uid)) {
            keywordInputItem(uidGroup, type = EditorInfo.TYPE_CLASS_NUMBER).let {
                uidGroup.addView(it.first)
                it.second.requestFocus()
            }
        })
        root.addView(uidGroup)
        val uidPrefs = prefs.getStringSet("customize_dynamic_keyword_uid", null) ?: setOf()
        uidPrefs.forEach {
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

            fun getInputValues(viewGroup: ViewGroup) = viewGroup.children
                .filterIsInstance<ViewGroup>()
                .flatMap { it.children }
                .filterIsInstance<EditText>()
                .map { it.text.toString().trim() }
                .filter { it.isNotEmpty() }
                .toSet()

            val contentValues = getInputValues(contentGroup)
            val upNameValues = getInputValues(upNameGroup)
            val uidValues = getInputValues(uidGroup)

            prefs.edit().apply {
                putBoolean("customize_dynamic_all_rm_topic", rmTopicOfAllSwitch.isChecked)
                putBoolean("customize_dynamic_all_rm_up", rmUpOfAllSwitch.isChecked)
                putBoolean("customize_dynamic_video_rm_up", rmUpOfVideoSwitch.isChecked)
                putStringSet("customize_dynamic_type", typeValues)
                putStringSet("customize_dynamic_keyword_content", contentValues)
                putStringSet("customize_dynamic_keyword_upname", upNameValues)
                putStringSet("customize_dynamic_keyword_uid", uidValues)
            }.apply()
            Log.toast(string(R.string.customize_dynamic_save_success))
        }

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
    ): LinearLayout {
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
            minWidth = 60.dp
        }
        val addView = Button(context).apply {
            text = string(R.string.customize_dynamic_add)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener(onClick)
        }
        val clearView = Button(context).apply {
            text = string(R.string.customize_dynamic_clear)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(6.dp, 0, 0, 0)
            }
            setOnClickListener { group.removeAllViews() }
        }
        layout.addView(nameView)
        layout.addView(addView)
        layout.addView(clearView)
        return layout
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
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { weight = 1F }
            setSingleLine()
        }
        val button = Button(context).apply {
            text = string(R.string.customize_dynamic_delete)
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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1F
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 8.dp, 0, 8.dp)
                setMargins(0, 0, 10.dp, 0)
            }
        }
        val switcher = Switch(context).apply {
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
}
