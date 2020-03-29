package me.iacn.biliroaming

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.preference.Preference
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.PreferenceFragment
import android.util.Log
import java.io.File

/**
 * Created by iAcn on 2019/3/23
 * Email i@iacn.me
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar!!.elevation = 0f
        fragmentManager.beginTransaction().replace(android.R.id.content, PrefsFragment()).commit()
    }

    class PrefsFragment : PreferenceFragment(), OnPreferenceChangeListener {
        private var runningStatusPref: Preference? = null
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.prefs_setting)
            findPreference("hide_icon").onPreferenceChangeListener = this
            findPreference("version").summary = BuildConfig.VERSION_NAME
            runningStatusPref = findPreference("running_status")
        }

        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            if ("hide_icon" == preference.key) {
                val isShow = newValue as Boolean
                val aliasName = ComponentName(activity, MainActivity::class.java.name + "Alias")
                val packageManager = activity.packageManager
                val status = if (isShow) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                if (packageManager.getComponentEnabledSetting(aliasName) != status) {
                    packageManager.setComponentEnabledSetting(aliasName, status, PackageManager.DONT_KILL_APP)
                }
            }
            return true
        }

        @SuppressLint("SetWorldReadable")
        private fun setWorldReadable() {
            val dataDir = File(activity.applicationInfo.dataDir)
            val prefsDir = File(dataDir, "shared_prefs")
            val prefsFile = File(prefsDir, preferenceManager.sharedPreferencesName + ".xml")
            if (prefsFile.exists()) {
                for (file in arrayOf(dataDir, prefsDir, prefsFile)) {
                    file.setReadable(true, false)
                    file.setExecutable(true, false)
                }
            }
        }

        override fun onResume() {
            super.onResume()
            when {
                isTaiChiModuleActive(activity) -> {
                    runningStatusPref!!.setTitle(R.string.running_status_enable)
                    runningStatusPref!!.setSummary(R.string.runtime_taichi)
                }
                isModuleActive() -> {
                    runningStatusPref!!.setTitle(R.string.running_status_enable)
                    runningStatusPref!!.setSummary(R.string.runtime_xposed)
                }
                else -> {
                    runningStatusPref!!.setTitle(R.string.running_status_disable)
                    runningStatusPref!!.setSummary(R.string.not_running_summary)
                }
            }
        }

        override fun onPause() {
            super.onPause()
            setWorldReadable()
        }
    }

    companion object {

        private fun isModuleActive(): Boolean {
            Log.i("我很可爱", "请给我钱")
            return false
        }

        private fun isTaiChiModuleActive(context: Context): Boolean {
            val contentResolver = context.contentResolver
            val uri = Uri.parse("content://me.weishu.exposed.CP/")
            return try {
                val result = contentResolver.call(uri, "active", null, null)
                result!!.getBoolean("active", false)
            } catch (e: Exception) {
                false
            }
        }
    }
}