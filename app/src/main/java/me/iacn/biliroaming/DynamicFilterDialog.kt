package me.iacn.biliroaming

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Typeface
import android.util.TypedValue
import android.view.inputmethod.EditorInfo
import android.widget.*
import me.iacn.biliroaming.XposedInit.Companion.moduleRes
import me.iacn.biliroaming.utils.Log

class DynamicFilterDialog(val activity: Activity, prefs: SharedPreferences) :
    AlertDialog.Builder(activity) {
    init {
        val dynamicItems = moduleRes.getStringArray(R.array.dynamic_entries).zip(
            moduleRes.getStringArray(R.array.dynamic_values)
        )
        val scrollView = ScrollView(context).apply {
            scrollBarStyle = ScrollView.SCROLLBARS_INSIDE_INSET
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
        dynamicItems.forEach { item ->
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
        val byKeyword = TextView(context).apply {
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
        root.addView(byKeyword)

        val contentLine = keywordViewLine("内容", "customize_dynamic_keywords_content")
        val contentValue = prefs.getString(contentLine.second.tag.toString(), null) ?: ""
        contentLine.second.setText(contentValue)
        root.addView(contentLine.first)

        val upNameLine = keywordViewLine("UP主", "customize_dynamic_keywords_upname")
        val upNameValue = prefs.getString(upNameLine.second.tag.toString(), null) ?: ""
        upNameLine.second.setText(upNameValue)
        root.addView(upNameLine.first)

        val uidLine = keywordViewLine("UID", "customize_dynamic_keywords_uid")
        val uidValue = prefs.getString(uidLine.second.tag.toString(), null) ?: ""
        uidLine.second.setText(uidValue)
        root.addView(uidLine.first)

        setTitle(moduleRes.getString(R.string.customize_dynamic_title))

        setPositiveButton(android.R.string.ok) { _, _ ->
            val typeValues = buildSet {
                for (i in 0 until gridLayout.childCount step 2) {
                    val view = gridLayout.getChildAt(i) as CheckBox
                    if (view.isChecked) add(view.tag.toString())
                }
            }
            val content = contentLine.second.text.toString()
                .removePrefix("|").removeSuffix("|")
            val upName = upNameLine.second.text.toString()
                .removePrefix("|").removeSuffix("|")
            val uid = uidLine.second.text.toString()
                .removePrefix("|").removeSuffix("|")

            prefs.edit().apply {
                putStringSet("customize_dynamic_type", typeValues)
                putString(contentLine.second.tag.toString(), content)
                putString(upNameLine.second.tag.toString(), upName)
                putString(uidLine.second.tag.toString(), uid)
            }.apply()
            Log.toast("保存成功 重启后生效")
        }

        scrollView.setPadding(50, 20, 50, 20)

        setView(scrollView)
    }

    private fun keywordViewLine(kwName: String, kwKey: String): Pair<LinearLayout, EditText> {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(TextView(context).apply {
            text = kwName
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setSingleLine()
        })
        val editText = EditText(context).apply {
            tag = kwKey
            inputType = EditorInfo.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { weight = 1F }
            isSingleLine = false
        }
        layout.addView(editText)
        val button = Button(context).apply {
            text = "添加分隔符"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setSingleLine()
            setOnClickListener {
                when {
                    editText.text.isEmpty() -> {
                        Log.toast("你好像还没有输入内容> <", force = true)
                    }
                    editText.text.endsWith('|') -> {
                        Log.toast("啊嘞 上一个分隔符后面好像还没有东西> <", force = true)
                    }
                    else -> {
                        editText.text.append('|')
                    }
                }
            }
        }
        layout.addView(button)
        return Pair(layout, editText)
    }
}
