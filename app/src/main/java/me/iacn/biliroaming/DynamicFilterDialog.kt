package me.iacn.biliroaming

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import me.iacn.biliroaming.XposedInit.Companion.moduleRes
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.children
import kotlin.math.roundToInt

class DynamicFilterDialog(val activity: Activity, prefs: SharedPreferences) :
    AlertDialog.Builder(activity) {
    init {
        val dynamicTypes = moduleRes.getStringArray(R.array.dynamic_entries).zip(
            moduleRes.getStringArray(R.array.dynamic_values)
        )
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
        val byTypeTitle = TextView(context).apply {
            text = "按类型"
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18F)
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
            text = "按关键字"
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 10, 0, 0)
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18F)
        }
        root.addView(byKeywordTitle)

        val contentGroup = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(keywordTypeHeader(contentGroup, "内容") {
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
        root.addView(keywordTypeHeader(upNameGroup, "UP主") {
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
        root.addView(keywordTypeHeader(uidGroup, "UID") {
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

        setTitle(moduleRes.getString(R.string.customize_dynamic_title))

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
                putStringSet("customize_dynamic_type", typeValues)
                putStringSet("customize_dynamic_keyword_content", contentValues)
                putStringSet("customize_dynamic_keyword_upname", upNameValues)
                putStringSet("customize_dynamic_keyword_uid", uidValues)
            }.apply()
            Log.toast("保存成功 重启后生效")
        }

        root.setPadding(50, 20, 50, 20)

        setView(scrollView)
    }

    private val Int.dp
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            toFloat(),
            context.resources.displayMetrics
        ).roundToInt()

    private fun keywordTypeHeader(
        group: ViewGroup,
        name: String,
        onClick: (v: View) -> Unit,
    ): LinearLayout {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
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
            text = "添加"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener(onClick)
        }
        val clearView = Button(context).apply {
            text = "删除所有"
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
            text = "删除"
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
}
