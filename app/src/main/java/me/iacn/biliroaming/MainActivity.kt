@file:Suppress("DEPRECATION")

package me.iacn.biliroaming

import android.app.Activity
import android.app.AlertDialog
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
import android.view.View
import android.widget.Toast
import me.iacn.biliroaming.hook.SettingHook
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
            findPreference("feature").onPreferenceClickListener = this
            findPreference("setting").onPreferenceClickListener = this
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

        private fun onUpdateCheck(): Boolean {
            val uri = Uri.parse(resources.getString(R.string.update_url))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
            return true
        }

        private fun onFeatureClick(): Boolean {
            AlertDialog.Builder(activity).run {
                setView(View.inflate(activity, R.layout.feature, null))
                setNegativeButton("关闭", null)
                show()
            }
            return true
        }

        private fun onSettingClick(): Boolean {
            val packages = Constant.BILIBILI_PACKAGENAME.filter {
                isPackageInstalled(it)
            }
            when {
                packages.size == 1 -> {
                    startSetting(packages[0])
                }
                packages.isEmpty() -> {
                    Toast.makeText(activity, "未检测到已安装的客户端", Toast.LENGTH_LONG).show()
                }
                else -> {
                    AlertDialog.Builder(activity).run {
                        setItems(packages.toTypedArray()) { _, i ->
                            startSetting(packages[i])
                        }
                        setTitle("请选择包名")
                        show()
                    }
                }
            }
            return true
        }

        override fun onPreferenceClick(preference: Preference?): Boolean {
            return when (preference?.key) {
                "update" -> onUpdateCheck()
                "feature" -> onFeatureClick()
                "setting" -> onSettingClick()
                else -> false
            }
        }

        private fun isPackageInstalled(packageName: String): Boolean {
            return try {
                activity.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }

        private fun startSetting(packageName: String) {
            activity.packageManager.getLaunchIntentForPackage(packageName)?.run {
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                putExtra(SettingHook.START_SETTING_KEY, true)
                startActivity(this)
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