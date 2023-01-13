@file:Suppress("DEPRECATION")

package me.iacn.biliroaming

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.iacn.biliroaming.hook.SettingHook
import me.iacn.biliroaming.utils.fetchJson


/**
 * Created by iAcn on 2019/3/23
 * Email i@iacn.me
 */

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentManager.beginTransaction().replace(android.R.id.content, PrefsFragment()).commit()
    }

    class PrefsFragment : PreferenceFragment(), OnPreferenceChangeListener,
        OnPreferenceClickListener {
        private lateinit var runningStatusPref: Preference
        private val scope = MainScope()

        @Deprecated("Deprecated in Java")
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.main_activity)
            runningStatusPref = findPreference("running_status")
            findPreference("hide_icon").onPreferenceChangeListener = this
            findPreference("version").summary = BuildConfig.VERSION_NAME
            findPreference("feature").onPreferenceClickListener = this
            findPreference("setting").onPreferenceClickListener = this
            checkUpdate()
        }

        @Deprecated("Deprecated in Java")
        override fun onDestroy() {
            super.onDestroy()
            scope.cancel()
        }

        @Deprecated("Deprecated in Java")
        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            when (preference.key) {
                "hide_icon" -> {
                    val isShow = newValue as Boolean
                    val aliasName = ComponentName(activity, MainActivity::class.java.name + "Alias")
                    val packageManager = activity.packageManager
                    val status =
                        if (isShow) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    if (packageManager.getComponentEnabledSetting(aliasName) != status) {
                        packageManager.setComponentEnabledSetting(
                            aliasName,
                            status,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                }
            }
            return true
        }

        @Deprecated("Deprecated in Java")
        override fun onResume() {
            super.onResume()
            when {
                isModuleActive() -> {
                    runningStatusPref.setTitle(R.string.running_status_enable)
                    runningStatusPref.setSummary(R.string.runtime_xposed)
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

        private fun checkUpdate() = scope.launch {
            val result = fetchJson(resources.getString(R.string.version_url)) ?: return@launch
            val newestVer = result.optString("name")
            if (newestVer.isNotEmpty() && BuildConfig.VERSION_NAME != newestVer) {
                findPreference("version").summary = "${BuildConfig.VERSION_NAME}（最新版$newestVer）"
                (findPreference("about") as PreferenceCategory).addPreference(Preference(activity).apply {
                    key = "update"
                    title = resources.getString(R.string.update_title)
                    summary = result.optString("body").substringAfterLast("更新日志\r\n")
                        .ifEmpty { resources.getString(R.string.update_summary) }
                    onPreferenceClickListener = this@PrefsFragment
                    order = 1
                })
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
            val packages = Constant.BILIBILI_PACKAGE_NAME.filter { isPackageInstalled(it.value) }
            when {
                packages.size == 1 -> startSetting(packages.values.first())
                packages.isEmpty() -> Toast.makeText(activity, "未检测到已安装的客户端", Toast.LENGTH_LONG)
                    .show()
                else -> {
                    AlertDialog.Builder(activity).run {
                        val keys = packages.keys.toTypedArray()
                        setItems(keys) { _, i -> startSetting(packages[keys[i]] ?: error("")) }
                        setTitle("请选择版本")
                        show()
                    }
                }
            }
            return true
        }

        @Deprecated("Deprecated in Java")
        override fun onPreferenceClick(preference: Preference?) = when (preference?.key) {
            "update" -> onUpdateCheck()
            "feature" -> onFeatureClick()
            "setting" -> onSettingClick()
            else -> false
        }

        private fun isPackageInstalled(packageName: String) = try {
            activity.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
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
    }

}
