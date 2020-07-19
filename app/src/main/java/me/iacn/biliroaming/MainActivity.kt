@file:Suppress("DEPRECATION")

package me.iacn.biliroaming

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.preference.Preference
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.Preference.OnPreferenceClickListener
import android.preference.PreferenceCategory
import android.preference.PreferenceFragment
import android.util.Log
import android.view.View
import me.iacn.biliroaming.utils.CheckVersionTask
import me.iacn.biliroaming.utils.OnTaskReturn
import org.json.JSONObject
import java.io.InputStream
import java.net.URL


/**
 * Created by iAcn on 2019/3/23
 * Email i@iacn.me
 */

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_NO -> {
                setTheme(R.style.LightTheme)
            }
            Configuration.UI_MODE_NIGHT_YES -> {
                setTheme(R.style.DarkTheme)
            }
        }
        fragmentManager.beginTransaction().replace(android.R.id.content, PrefsFragment()).commit()
    }

    class PrefsFragment : PreferenceFragment(), OnPreferenceChangeListener, OnPreferenceClickListener, OnTaskReturn<JSONObject> {
        private lateinit var runningStatusPref: Preference
        private lateinit var prefs: SharedPreferences

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.main_activity)
            prefs = preferenceManager.sharedPreferences
            runningStatusPref = findPreference("running_status")
            findPreference("hide_icon").onPreferenceChangeListener = this
            findPreference("version").summary = BuildConfig.VERSION_NAME
            findPreference("author").onPreferenceClickListener = this
            findPreference("group").onPreferenceClickListener = this
            findPreference("feature").onPreferenceClickListener = this
            findPreference("help").onPreferenceClickListener = this
            CheckVersionTask(this).execute(URL(resources.getString(R.string.version_url)))
        }


        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            when (preference.key) {
                "hide_icon" -> {
                    val isShow = newValue as Boolean
                    val aliasName = ComponentName(activity, MainActivity::class.java.name + "Alias")
                    val packageManager = activity.packageManager
                    val status = if (isShow) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    if (packageManager.getComponentEnabledSetting(aliasName) != status) {
                        packageManager.setComponentEnabledSetting(aliasName, status, PackageManager.DONT_KILL_APP)
                    }
                }
            }
            return true
        }

        override fun onResume() {
            super.onResume()
            when {
                isModuleActive() -> {
                    runningStatusPref.setTitle(R.string.running_status_enable)
                    runningStatusPref.setSummary(R.string.runtime_xposed)
                }
                isAppFuckActive(activity) -> {
                    runningStatusPref.setTitle(R.string.running_status_enable)
                    runningStatusPref.setSummary(R.string.runtime_app_fuck)
                }
                isTaiChiModuleActive(activity) -> {
                    runningStatusPref.setTitle(R.string.running_status_enable)
                    runningStatusPref.setSummary(R.string.runtime_taichi)
                }
                else -> {
                    runningStatusPref.setTitle(R.string.running_status_disable)
                    runningStatusPref.setSummary(R.string.not_running_summary)
                }
            }
        }

        override fun onReturn(result: JSONObject?) {
            try {
                result?.getString("name")?.let {
                    if (BuildConfig.VERSION_NAME != it) {
                        findPreference("version").summary = "${BuildConfig.VERSION_NAME}（最新版${it}）"
                        val aboutGroup = findPreference("about") as PreferenceCategory
                        val updatePreference = Preference(activity)
                        updatePreference.key = "update"
                        updatePreference.title = resources.getString(R.string.update_title)
                        var log = ""
                        try {
                            val body = result.getString("body")
                            log = body.substring(body.lastIndexOf("更新日志"))
                        } catch (e: Throwable) {
                        }
                        updatePreference.summary = if (log.isNotEmpty()) log else resources.getString(R.string.update_summary)
                        updatePreference.onPreferenceClickListener = this
                        updatePreference.order = 1
                        aboutGroup.addPreference(updatePreference)
                    }
                }
            } catch (e: Throwable) {
            }
        }

        private fun onAuthorClick(): Boolean {
            val uri = Uri.parse(resources.getString(R.string.github_url))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
            return true
        }

        private fun onUpdateCheck(): Boolean {
            val uri = Uri.parse(resources.getString(R.string.update_url))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
            return true
        }

        private fun onHelpClick(): Boolean {
            val uri = Uri.parse(resources.getString(R.string.help_url))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
            return true
        }

        private fun onGroupClick(): Boolean {
            val intent = Intent()
            val key = "Qk8NsOfgC-afK4Vqhnqg9FBF2l1oL0sp"
            intent.data = Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26k%3D$key")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                startActivity(intent)
                true
            } catch (e: Exception) {
                false
            }
        }

        private fun onFeatureClick(): Boolean {
            AlertDialog.Builder(activity).run {
                setView(View.inflate(activity, R.layout.feature, null))
                setNegativeButton("关闭", null)
                show()
            }
            return true
        }

        override fun onPreferenceClick(preference: Preference?): Boolean {
            return when (preference?.key) {
                "author" -> onAuthorClick()
                "update" -> onUpdateCheck()
                "group" -> onGroupClick()
                "feature" -> onFeatureClick()
                "help" -> onHelpClick()
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
                result?.getBoolean("active", false) ?: false
            } catch (e: Exception) {
                false
            }
        }

        private fun isAppFuckActive(context: Context): Boolean {
            try {
                val appContext = context.createPackageContext("com.bug.xposed", Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE)
                val mods = appContext.getFileStreamPath("mods")
                val bugSerializeClass = appContext.classLoader.loadClass("com.bug.utils.BugSerialize")
                val modClass = appContext.classLoader.loadClass("com.bug.xposed.ModConfig\$Mod")
                val list = bugSerializeClass.getDeclaredMethod("deserialize", InputStream::class.java).invoke(null, mods.inputStream()) as ArrayList<*>
                for (mod in list) {
                    if (modClass.getMethod("getPkg").invoke(mod) == BuildConfig.APPLICATION_ID) {
                        return true
                    }
                }
            } catch (e: Throwable) {
            }
            return false
        }
    }

}