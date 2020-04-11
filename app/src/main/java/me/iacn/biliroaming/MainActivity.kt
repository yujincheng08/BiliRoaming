package me.iacn.biliroaming

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.preference.Preference
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.Preference.OnPreferenceClickListener
import android.preference.PreferenceCategory
import android.preference.PreferenceFragment
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
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

    class PrefsFragment : PreferenceFragment(), OnPreferenceChangeListener, OnPreferenceClickListener {
        private var runningStatusPref: Preference? = null
        private var counter: Int = 0;
        private var prefs: SharedPreferences? = null
        private var toast: Toast? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            onCreate()
        }

        private fun onCreate() {
            addPreferencesFromResource(R.xml.prefs_setting)
            prefs = preferenceManager.sharedPreferences
            runningStatusPref = findPreference("running_status")
            if (!prefs?.getBoolean("hidden", false)!!) {
                val hiddenGroup = findPreference("hidden_group") as PreferenceCategory
                preferenceScreen.removePreference(hiddenGroup)
            }
            findPreference("hide_icon").onPreferenceChangeListener = this
            findPreference("version").summary = BuildConfig.VERSION_NAME
            findPreference("version").onPreferenceClickListener = this
            findPreference("author").onPreferenceClickListener = this
            findPreference("test_cdn").onPreferenceClickListener = this
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
                isModuleActive() -> {
                    runningStatusPref!!.setTitle(R.string.running_status_enable)
                    runningStatusPref!!.setSummary(R.string.runtime_xposed)
                }
                isTaiChiModuleActive(activity) -> {
                    runningStatusPref!!.setTitle(R.string.running_status_enable)
                    runningStatusPref!!.setSummary(R.string.runtime_taichi)
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

        private fun onVersionClick(): Boolean {
            if (prefs?.getBoolean("hidden", false)!! || counter == 7) {
                return true
            }
            counter++
            val text = "再按${7 - counter}次开启净化功能"
            if (counter == 7) {
                preferenceScreen.removeAll()
                addPreferencesFromResource(R.xml.prefs_setting)
                prefs?.edit()?.putBoolean("hidden", true)?.commit()
                toast?.setText("已开启净化功能")
                toast?.duration = LENGTH_SHORT;
                toast?.show();
                preferenceScreen.removeAll()
                onCreate()
                onResume()
            } else if (counter >= 4) {
                toast?.let {
                    it.setText(text);
                    it.duration = LENGTH_SHORT;
                    it.show();
                } ?: run {
                    toast = Toast.makeText(activity, text, LENGTH_SHORT)
                    toast?.show()
                }
            }
            return true
        }

        private fun onAuthorClick(): Boolean {
            val uri = Uri.parse("https://github.com/yujincheng08/BiliRoaming")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
            return true
        }

        private fun onTestCDNClick(): Boolean {
            val uri = Uri.parse("https://yujincheng08.github.io/BiliRoaming/cdn_test.html")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
            return true
        }

        override fun onPreferenceClick(preference: Preference?): Boolean {
            return when (preference?.key) {
                "version" -> onVersionClick()
                "author" -> onAuthorClick()
                "test_cdn" -> onTestCDNClick()
                else -> false
            }
        }
    }

    companion object {

        @android.support.annotation.Keep
        fun isModuleActive(): Boolean {
            Log.i("大不自多", "海纳江河")
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