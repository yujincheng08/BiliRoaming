@file:Suppress("DEPRECATION")

package me.iacn.biliroaming

import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceCategory
import android.preference.PreferenceFragment
import android.view.ContextThemeWrapper
import me.iacn.biliroaming.XposedInit.Companion.toastMessage
import me.iacn.biliroaming.utils.CheckVersionTask
import me.iacn.biliroaming.utils.OnTaskReturn
import org.json.JSONObject
import java.net.URL
import kotlin.system.exitProcess


class SettingDialog(context: Context) : AlertDialog.Builder(context) {

    class PrefsFragment : PreferenceFragment(), Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener, OnTaskReturn<JSONObject> {
        private lateinit var prefs: SharedPreferences
        private var counter: Int = 0

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            preferenceManager.sharedPreferencesName = "biliroaming"
            addPreferencesFromResource(R.xml.prefs_setting)
            prefs = preferenceManager.sharedPreferences
            if (!prefs.getBoolean("hidden", false)) {
                val hiddenGroup = findPreference("hidden_group") as PreferenceCategory
                preferenceScreen.removePreference(hiddenGroup)
            }
            findPreference("version").summary = BuildConfig.VERSION_NAME
            findPreference("version").onPreferenceClickListener = this
            findPreference("author").onPreferenceClickListener = this
            findPreference("test_cdn").onPreferenceClickListener = this
            findPreference("group").onPreferenceClickListener = this
            findPreference("help").onPreferenceClickListener = this
            CheckVersionTask(this).execute(URL(XposedInit.moduleRes.getString(R.string.version_url)))
        }

        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            return true
        }

        private fun onVersionClick(): Boolean {
            if (prefs.getBoolean("hidden", false) || counter == 7) {
                return true
            }
            counter++
            if (counter == 7) {
                prefs.edit()?.putBoolean("hidden", true)?.apply()
                toastMessage("已开启隐藏功能，重启应用生效")
            } else if (counter >= 4) {
                toastMessage("再按${7 - counter}次开启隐藏功能")
            }

            return true
        }

        override fun onReturn(result: JSONObject?) {
            try {
                result?.getString("name")?.let {
                    if (BuildConfig.VERSION_NAME != it) {
                        findPreference("version").summary = "${BuildConfig.VERSION_NAME}（最新版${it}）"
                        val aboutGroup = findPreference("about") as PreferenceCategory
                        val updatePreference = Preference(activity)
                        updatePreference.key = "update"
                        updatePreference.title = XposedInit.moduleRes.getString(R.string.update_title)
                        var log = ""
                        try {
                            val body = result.getString("body")
                            log = body.substring(body.lastIndexOf("更新日志"))
                        } catch (e: Throwable) {
                        }
                        updatePreference.summary = if (log.isNotEmpty()) log else XposedInit.moduleRes.getString(R.string.update_summary)
                        updatePreference.onPreferenceClickListener = this
                        updatePreference.order = 1
                        aboutGroup.addPreference(updatePreference)
                    }
                }
            } catch (e: Throwable) {
            }
        }

        private fun onAuthorClick(): Boolean {
            val uri = Uri.parse(XposedInit.moduleRes.getString(R.string.github_url))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
            return true
        }

        private fun onTestCDNClick(): Boolean {
            val uri = Uri.parse(XposedInit.moduleRes.getString(R.string.cdn_url))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
            return true
        }

        private fun onUpdateCheck(): Boolean {
            val uri = Uri.parse(XposedInit.moduleRes.getString(R.string.update_url))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
            return true
        }

        private fun onHelpClick(): Boolean {
            val uri = Uri.parse(XposedInit.moduleRes.getString(R.string.help_url))
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

        override fun onPreferenceClick(preference: Preference?): Boolean {
            return when (preference?.key) {
                "version" -> onVersionClick()
                "author" -> onAuthorClick()
                "test_cdn" -> onTestCDNClick()
                "update" -> onUpdateCheck()
                "group" -> onGroupClick()
                "help" -> onHelpClick()
                else -> false
            }
        }
    }


    init {
        val activity = context as Activity
        val backupRes = replaceResource(activity, XposedInit.moduleRes)
        val prefsFragment = PrefsFragment()
        activity.fragmentManager.beginTransaction().add(prefsFragment, "Setting").commit()
        activity.fragmentManager.executePendingTransactions()

        prefsFragment.onActivityCreated(null)

        setView(prefsFragment.view)
        setTitle("哔哩漫游设置")
        restoreResource(activity, backupRes)
        setNegativeButton("返回", null)
        setPositiveButton("确定并重启客户端") { _, _ ->
            val intent = activity.baseContext.packageManager
                    .getLaunchIntentForPackage(activity.baseContext.packageName)
            val restartIntent = PendingIntent.getActivity(activity.applicationContext, 0, intent, PendingIntent.FLAG_ONE_SHOT)
            val mgr = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, restartIntent)
            exitProcess(0)
        }
    }

    class BackupRes(val res: Resources, val theme: Resources.Theme)

    companion object {
        @JvmStatic
        fun replaceResource(context: Activity?, res: Resources?): BackupRes? {
            context ?: return null
            res ?: return null
            val resField = ContextThemeWrapper::class.java.getDeclaredField("mResources")
            resField.isAccessible = true
            val oldRes = resField.get(context) as Resources
            val themeField = ContextThemeWrapper::class.java.getDeclaredField("mTheme")
            themeField.isAccessible = true
            val oldTheme = themeField.get(context) as Resources.Theme
            val newTheme = res.newTheme()
            when (oldRes.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_NO -> {
                    newTheme.applyStyle(R.style.LightTheme, true)
                }
                Configuration.UI_MODE_NIGHT_YES -> {
                    newTheme.applyStyle(R.style.DarkTheme, true)
                }
            }
            resField.set(context, res)
            themeField.set(context, newTheme)
            return BackupRes(oldRes, oldTheme)
        }

        @JvmStatic
        fun restoreResource(context: Activity?, backupRes: BackupRes?) {
            backupRes ?: return
            val resField = ContextThemeWrapper::class.java.getDeclaredField("mResources")
            resField.isAccessible = true
            resField.set(context, backupRes.res)
            val themeField = ContextThemeWrapper::class.java.getDeclaredField("mTheme")
            themeField.isAccessible = true
            themeField.set(context, backupRes.theme)
        }
    }

}
