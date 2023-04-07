package me.iacn.biliroaming

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import me.iacn.biliroaming.hook.PakkuSetting
import me.iacn.biliroaming.utils.Log
import org.json.JSONObject


class DanmakuFilterDialog(val activity: Activity, prefs: SharedPreferences) :
    AlertDialog.Builder(activity) {
    init {
        val layout = activity.resources.getLayout(R.layout.danmaku_filter_dialog)
        val view = LayoutInflater.from(context).inflate(layout, null)

        val switches = arrayOf(
            view.findViewById<Switch>(R.id.danmaku_filter_weight_switch)!!,
            view.findViewById<Switch>(R.id.danmaku_filter_pakku_switch)!!,
        )

        switches.forEach {
            if (prefs.contains(it.tag.toString())) {
                it.isChecked = prefs.getBoolean(it.tag.toString(), false)
            }
        }

        val spinner =
            view.findViewById<Spinner>(R.id.danmaku_filter_weight_value)!!

        spinner.run {
            val values = (0..12).map { it.toString() }
            val value = try {
                prefs.getInt(tag.toString(), 4)
            } catch (e: Exception) {
                prefs.getString(tag.toString(), "4")!!.toInt()
            }
            initSpinner(this, values, values.indexOf(value.toString()))
        }

        val pakkuEditText = view.findViewById<EditText>(R.id.danmaku_filter_pakku_setting)!!

        pakkuEditText.let {
            val hint = it.hint.toString()
            val key = it.tag.toString()
            it.setText(
                prefs.getString(
                    key,
                    hint
                )
            )
        }

        arrayOf(
            switches[1] to activity.getString(R.string.danmaku_filter_pakku_switch_tooltip),
            pakkuEditText to activity.getString((R.string.danmaku_filter_pakku_setting_tooltip))
        ).forEach { (view, tooltip) ->
            view.setOnClickListener { _ ->
                Log.toast(tooltip, false, Toast.LENGTH_LONG)
            }
            view.setOnLongClickListener { _ ->
                Log.toast(tooltip, false, Toast.LENGTH_LONG)
                true
            }
        }

        var pakkuSetting: JSONObject? = null

        val spinners = initPakkuOption(prefs, view) {
            pakkuSetting = it
        }
        val switchPairs = mutableListOf(
            switches[0] to arrayOf(spinner),
            switches[1] to (arrayOf<View>(pakkuEditText) + spinners)
        )
        switchPairs.forEach { (switch, viewArray) ->
            viewArray.forEach { view ->
                view.alpha = if (switch.isChecked) 1f else 0.2f
            }
            switch.setOnCheckedChangeListener { _, isChecked ->
                viewArray.forEach { view ->
                    view.alpha = if (isChecked) 1f else 0.2f
                }
            }
        }

        setPositiveButton(android.R.string.ok) { _, _ ->
            val editor = prefs.edit()
            pakkuEditText.let {
                val text = it.text.toString()
                if (text.isNotEmpty()) {
                    editor.putString(
                        it.tag.toString(),
                        text
                    ).apply()
                } else
                    editor.remove(it.tag.toString()).apply()
            }
            switches.forEach {
                editor.putBoolean(it.tag.toString(), it.isChecked).apply()
            }
            spinner.selectedItemPosition.let { position ->
                val value = (spinner.adapter.getItem(position) as String).toInt()
                editor.putInt(spinner.tag.toString(), value)
            }
            pakkuSetting?.let { editor.putString("danmaku_filter_pakku_setting", it.toString()) }
            editor.apply()
        }
        setTitle(activity.getString(R.string.danmaku_filter_dialog_title))
        setView(view)
    }

    private fun initPakkuOption(
        prefs: SharedPreferences,
        view: View,
        onChange: (JSONObject) -> Unit
    ): Array<Spinner> {
        val optionMap = mapOf(
            "MAX_DIST" to getOptionsMap(
                R.array.pakku_max_dist_entries,
                R.array.pakku_max_dist_values
            ),
            "MAX_COSINE" to getOptionsMap(
                R.array.pakku_max_cosine_entries,
                R.array.pakku_max_cosine_values
            ),
            "DANMU_MARK" to getOptionsMap(
                R.array.pakku_danmu_mark_entries,
                R.array.pakku_danmu_mark_values
            ),
            "REPRESENTATIVE_PERCENT" to getOptionsMap(
                R.array.pakku_representative_percent_entries,
                R.array.pakku_representative_percent_values
            )
        )
        val pakkuSetting = prefs.getString("danmaku_filter_pakku_setting", "默认")
            ?.takeIf { it.isNotEmpty() && it[0] == '{' }
            ?.let { PakkuSetting.parseStaticValuesFromJson(it); PakkuSetting.toJson() }
            ?: PakkuSetting.toJson()
        val spinners = arrayOf<Spinner>(
            view.findViewById(R.id.danmaku_filter_pakku_max_dist)!!,
            view.findViewById(R.id.danmaku_filter_pakku_max_cosine)!!,
            view.findViewById(R.id.danmaku_filter_pakku_danmu_mark)!!,
            view.findViewById(R.id.danmaku_filter_pakku_representative_percent)!!,
        )
        spinners.forEach {
            val key = it.tag.toString()
            val options = optionMap[key]!!
            val respectValues = options.values.toList()
            initSpinner(
                it,
                options.keys.toList(),
                respectValues.indexOf(pakkuSetting.getString(key))
            )
            it.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    pakkuSetting.put(key, respectValues[position])
                    onChange.invoke(pakkuSetting)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // code to handle nothing selected
                }
            }
        }
        return spinners
    }

    private fun getOptionsMap(entryId: Int, valueId: Int): Map<String, String> {
        val entries = activity.resources.getStringArray(entryId)
        val values = activity.resources.getStringArray(valueId)
        return entries.zip(values).toMap()
    }

    private fun initSpinner(spinner: Spinner, values: List<String>, position: Int) {
        val adapter = ArrayAdapter(
            activity,
            android.R.layout.simple_spinner_item,
            values
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(position)
    }
}
