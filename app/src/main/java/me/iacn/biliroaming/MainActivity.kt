@file:Suppress("DEPRECATION")

package me.iacn.biliroaming

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap.CompressFormat
import android.net.Uri
import android.os.Bundle
import android.preference.Preference
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.Preference.OnPreferenceClickListener
import android.preference.PreferenceCategory
import android.preference.PreferenceFragment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import me.iacn.biliroaming.utils.CheckVersionTask
import me.iacn.biliroaming.utils.OnTaskReturn
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
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
        private var runningStatusPref: Preference? = null
        private var counter: Int = 0
        private var prefs: SharedPreferences? = null
        private var toast: Toast? = null

        private val fileSectionCode = 0


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
            findPreference("group").onPreferenceClickListener = this
            findPreference("beautify_splash").onPreferenceChangeListener = this
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
                "beautify_splash" -> {
                    if (newValue as Boolean)
                        selectSplashImage()
                    else
                        preferenceManager.sharedPreferences.edit().remove("splash_image").apply()
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
            val text = "再按${7 - counter}次开启隐藏功能"
            if (counter == 7) {
                preferenceScreen.removeAll()
                addPreferencesFromResource(R.xml.prefs_setting)
                prefs?.edit()?.putBoolean("hidden", true)?.apply()
                toast?.setText("已开启隐藏功能")
                toast?.duration = LENGTH_SHORT
                toast?.show()
                preferenceScreen.removeAll()
                onCreate()
                onResume()
            } else if (counter >= 4) {
                toast?.let {
                    it.setText(text)
                    it.duration = LENGTH_SHORT
                    it.show()
                } ?: run {
                    toast = Toast.makeText(activity, text, LENGTH_SHORT)
                    toast?.setText(text)
                    toast?.show()
                }
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

        private fun onTestCDNClick(): Boolean {
            val uri = Uri.parse(resources.getString(R.string.cdn_url))
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

        private fun onGroupChick(): Boolean {
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

        private fun selectSplashImage(): Boolean {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.type = "*/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)

            try {
                startActivityForResult(Intent.createChooser(intent, "选择一张图片"), fileSectionCode)
            } catch (ex: ActivityNotFoundException) {
                Toast.makeText(activity, "请安装文件管理器",
                        LENGTH_SHORT).show()
            }
            return true
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
            when (requestCode) {
                fileSectionCode -> if (resultCode == RESULT_OK) {
                    val uri = data.data ?: return
                    val stream = ByteArrayOutputStream()
                    stream.flush()
                    MediaStore.Images.Media.getBitmap(activity.contentResolver, uri).compress(CompressFormat.PNG, 100, stream)
                    val encodedImage = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
                    preferenceManager.sharedPreferences.edit().putString("splash_image", encodedImage).apply()
                }
            }
            super.onActivityResult(requestCode, resultCode, data)
        }


        override fun onPreferenceClick(preference: Preference?): Boolean {
            return when (preference?.key) {
                "version" -> onVersionClick()
                "author" -> onAuthorClick()
                "test_cdn" -> onTestCDNClick()
                "update" -> onUpdateCheck()
                "group" -> onGroupChick()
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