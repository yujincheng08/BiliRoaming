package me.iacn.biliroaming

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.widget.ScrollView
import me.iacn.biliroaming.utils.dp
import me.iacn.biliroaming.utils.sPrefs
import kotlin.math.absoluteValue

class CustomVodServerDialog(activity: Activity, prefs: SharedPreferences) :
    AlertDialog.Builder(activity) {
    init {
        setTitle(R.string.custom_video_vod_server_title)
        val scrollView = ScrollView(context).apply {
            scrollBarStyle = ScrollView.SCROLLBARS_OUTSIDE_OVERLAY
        }
        val vodServerValues = stringArray(R.array.video_vod_server_values)
        val currentServer = sPrefs.getString("video_vod_server", null) ?: vodServerValues[1]
        val index = vodServerValues.indexOf(currentServer).absoluteValue
        setSingleChoiceItems(stringArray(R.array.video_vod_server_entries), index) { dialog, newIndex ->
            prefs.edit().putString("video_vod_server", vodServerValues[newIndex]).apply()
            dialog.dismiss()
        }
        scrollView.setPadding(16.dp, 10.dp, 16.dp, 10.dp)
        setView(scrollView)
    }

    private fun stringArray(resId: Int) = context.resources.getStringArray(resId)
}
