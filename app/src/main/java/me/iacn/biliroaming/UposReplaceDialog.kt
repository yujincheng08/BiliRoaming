package me.iacn.biliroaming

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.*
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.addBackgroundRipple
import me.iacn.biliroaming.utils.dp
import me.iacn.biliroaming.utils.runCatchingOrNull
import java.util.concurrent.TimeUnit

class UposReplaceDialog(val activity: Activity, prefs: SharedPreferences) :
    AlertDialog.Builder(activity) {
    init {
        val scrollView = ScrollView(context).apply {
            scrollBarStyle = ScrollView.SCROLLBARS_OUTSIDE_OVERLAY
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        scrollView.addView(root)

        fun categoryTitle(title: String) = TextView(context).apply {
            text = title
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18F)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dp }
        }

        val isLocatedCn =
            (runCatchingOrNull { XposedInit.country.get(5L, TimeUnit.SECONDS) } ?: "cn") == "cn"

        setTitle(string(R.string.upos_replace_dialog_title))

        root.addView(categoryTitle(string(R.string.upos_replace_dialog_base_title)))
        val baseUrlUposReplaceSwitchMap = mapOf(
            "replace_pcdn_upos_base" to Pair(
                switchPrefsItem(string(R.string.upos_replace_dialog_replace_pcdn_upos_title)), true
            ),
            "replace_mcdn_upos_base" to Pair(
                switchPrefsItem(string(R.string.upos_replace_dialog_replace_mcdn_upos_title)), true
            ),
            "replace_bcache_cdn_upos_base" to Pair(
                switchPrefsItem(string(R.string.upos_replace_dialog_replace_bcache_cdn_upos_title)),
                isLocatedCn
            ),
            "replace_mirror_cdn_upos_base" to Pair(
                switchPrefsItem(string(R.string.upos_replace_dialog_replace_mirror_cdn_upos_title)),
                false
            ),
            "replace_all_upos_base" to Pair(
                switchPrefsItem(string(R.string.upos_replace_dialog_replace_all_upos_title)), true
            ),
        ).let { map ->
            map.entries.forEach { entry ->
                entry.value.first.second.isChecked = prefs.getBoolean(entry.key, entry.value.second)
                root.addView(entry.value.first.first)
            }
            map
        }

        root.addView(categoryTitle(string(R.string.upos_replace_dialog_backup_title)))
        val backupUrlUposReplaceSwitchMap = mapOf(
            "replace_pcdn_upos_backup" to Pair(
                switchPrefsItem(string(R.string.upos_replace_dialog_replace_pcdn_upos_title)), true
            ),
            "replace_mcdn_upos_backup" to Pair(
                switchPrefsItem(string(R.string.upos_replace_dialog_replace_mcdn_upos_title)),
                isLocatedCn
            ),
            "replace_bcache_cdn_upos_backup" to Pair(
                switchPrefsItem(string(R.string.upos_replace_dialog_replace_bcache_cdn_upos_title)),
                isLocatedCn
            ),
            "replace_mirror_cdn_upos_backup" to Pair(
                switchPrefsItem(string(R.string.upos_replace_dialog_replace_mirror_cdn_upos_title)),
                false
            ),
            "replace_all_upos_backup" to Pair(
                switchPrefsItem(string(R.string.upos_replace_dialog_replace_all_upos_title)), false
            ),
        ).let { map ->
            map.entries.forEach { entry ->
                entry.value.first.second.isChecked = prefs.getBoolean(entry.key, entry.value.second)
                root.addView(entry.value.first.first)
            }
            map
        }

        root.addView(categoryTitle(string(R.string.upos_replace_dialog_other_title)))
        val replaceUposBwSwitch = string(R.string.upos_replace_dialog_replace_bw_title).let {
            switchPrefsItem(it).let { p -> root.addView(p.first); p.second }
        }
        replaceUposBwSwitch.isChecked = prefs.getBoolean("replace_upos_bw", true)
        val replaceOverseaUposSwitch = string(R.string.upos_replace_dialog_replace_upos_ov_title).let {
            switchPrefsItem(it).let { p -> root.addView(p.first); p.second }
        }
        replaceOverseaUposSwitch.isChecked = prefs.getBoolean("replace_oversea_upos", isLocatedCn)

        setPositiveButton(android.R.string.ok) { _, _ ->
            prefs.edit().apply {
                baseUrlUposReplaceSwitchMap.forEach { (key, value) ->
                    putBoolean(key, value.first.second.isChecked)
                }
                backupUrlUposReplaceSwitchMap.forEach { (key, value) ->
                    putBoolean(key, value.first.second.isChecked)
                }
                putBoolean("replace_upos_bw", replaceUposBwSwitch.isChecked)
                putBoolean("replace_oversea_upos", replaceOverseaUposSwitch.isChecked)
            }.apply()
            Log.toast(string(R.string.prefs_save_success_and_reboot))
        }
        setNegativeButton(android.R.string.cancel, null)

        root.setPadding(16.dp, 10.dp, 16.dp, 10.dp)

        setView(scrollView)
    }

    private fun string(resId: Int) = context.getString(resId)

    private fun switchPrefsItem(title: String): Pair<LinearLayout, Switch> {
        val layout = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val titleView = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16F)
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 8.dp, 0, 8.dp)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT
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
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        layout.setOnClickListener { switcher.toggle() }
        layout.addBackgroundRipple()
        layout.addView(titleView)
        layout.addView(switcher)
        return Pair(layout, switcher)
    }
}
