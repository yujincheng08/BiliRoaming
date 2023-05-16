package me.iacn.biliroaming

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import me.iacn.biliroaming.utils.dp
import me.iacn.biliroaming.utils.sPrefs
import kotlin.math.absoluteValue

class CustomVodServerDialog(activity: Activity, prefs: SharedPreferences) :
    AlertDialog.Builder(activity) {
    init {
        setTitle(R.string.upos_title)
        val scrollView = ScrollView(context).apply {
            scrollBarStyle = ScrollView.SCROLLBARS_OUTSIDE_OVERLAY
        }
        val vodServerValues = stringArray(R.array.upos_values)
        val currentServer = sPrefs.getString("upos_host", null) ?: vodServerValues[1]
        val index = vodServerValues.indexOf(currentServer).absoluteValue
        setSingleChoiceItems(stringArray(R.array.upos_entries), index) { dialog, newIndex ->
            prefs.edit().putString("upos_host", vodServerValues[newIndex]).apply()
            dialog.dismiss()
        }
        scrollView.setPadding(16.dp, 10.dp, 16.dp, 10.dp)
        setView(scrollView)
    }

    private fun stringArray(resId: Int) = context.resources.getStringArray(resId)
}
