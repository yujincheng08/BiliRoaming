package me.iacn.biliroaming

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import me.iacn.biliroaming.utils.addBackgroundRipple
import me.iacn.biliroaming.utils.children
import me.iacn.biliroaming.utils.dp

open class BaseWidgetDialog(context: Context) : AlertDialog.Builder(context) {
    protected fun string(resId: Int) = context.getString(resId)

    protected fun categoryTitle(title: String) = TextView(context).apply {
        text = title
        typeface = Typeface.DEFAULT_BOLD
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18F)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 10.dp }
    }

    protected fun textInputTitle(title: String) = TextView(context).apply {
        text = title
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18F)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    protected fun textInputItem(
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
        namMinWidth: Int,
        showRegex: Boolean,
        onAdd: (v: View) -> Unit,
    ): Triple<LinearLayout, Button, Switch> {
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
            minWidth = namMinWidth
        }
        val addView = Button(context).apply {
            text = string(R.string.keyword_group_add)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            minWidth = 60.dp
            minimumWidth = 60.dp
            setOnClickListener(onAdd)
        }
        val clearView = Button(context).apply {
            text = string(R.string.keyword_group_clear)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
            minWidth = 60.dp
            minimumWidth = 60.dp
            setOnClickListener { group.removeAllViews() }
        }
        val regexModeView = TextView(context).apply {
            text = string(R.string.regex_mode)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 4.dp }
        }
        val regexModeSwitch = Switch(context).apply {
            isSoundEffectsEnabled = false
            isHapticFeedbackEnabled = false
        }
        if (!showRegex) {
            regexModeView.visibility = View.GONE
            regexModeSwitch.visibility = View.GONE
        }
        layout.addView(nameView)
        layout.addView(addView)
        layout.addView(clearView)
        layout.addView(regexModeView)
        layout.addView(regexModeSwitch)
        return Triple(layout, clearView, regexModeSwitch)
    }

    protected fun keywordInputItem(
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16F)
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
            minWidth = 60.dp
            minimumWidth = 60.dp
            setOnClickListener { parent.removeView(layout) }
        }
        layout.addView(editText)
        layout.addView(button)
        return Pair(layout, editText)
    }

    protected fun LinearLayout.addKeywordGroup(
        name: String,
        namMinWidth: Int = 64.dp,
        showRegex: Boolean = false,
        inputType: Int = EditorInfo.TYPE_CLASS_TEXT,
    ): Pair<ViewGroup, Switch> {
        val group = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val (_, clearButton, regexModeSwitch) = keywordTypeHeader(
            group, name, namMinWidth, showRegex
        ) {
            keywordInputItem(group, type = inputType).let {
                group.addView(it.first)
                it.second.requestFocus()
            }
        }.also { addView(it.first) }
        group.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View, child: View) {
                if (group.childCount == 0) {
                    clearButton.visibility = View.GONE
                } else {
                    clearButton.visibility = View.VISIBLE
                }
            }

            override fun onChildViewRemoved(parent: View, child: View) {
                if (group.childCount == 0) {
                    clearButton.visibility = View.GONE
                } else {
                    clearButton.visibility = View.VISIBLE
                }
            }
        })
        addView(group)
        return Pair(group, regexModeSwitch)
    }

    protected fun switchPrefsItem(title: String): Pair<LinearLayout, Switch> {
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
            ).apply { weight = 1F; marginEnd = 10.dp }
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

    protected fun ViewGroup.getKeywords() = children
        .filterIsInstance<ViewGroup>()
        .flatMap { it.children }
        .filterIsInstance<EditText>()
        .map { it.text.toString().trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}
