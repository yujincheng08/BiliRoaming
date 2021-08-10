@file:Suppress("DEPRECATION")

package me.iacn.biliroaming

import android.app.Activity
import android.app.Activity.RESULT_CANCELED
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.*
import android.provider.MediaStore
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.XposedInit.Companion.modulePath
import me.iacn.biliroaming.XposedInit.Companion.moduleRes
import me.iacn.biliroaming.hook.JsonHook
import me.iacn.biliroaming.hook.SplashHook
import me.iacn.biliroaming.utils.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.system.exitProcess


class SettingDialog(context: Context) : AlertDialog.Builder(context) {

    class PrefsFragment : PreferenceFragment(), Preference.OnPreferenceChangeListener,
        Preference.OnPreferenceClickListener {
        private val scope = MainScope()
        private lateinit var prefs: SharedPreferences
        private lateinit var biliprefs: SharedPreferences
        private var counter: Int = 0

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            preferenceManager.sharedPreferencesName = "biliroaming"
            addPreferencesFromResource(R.xml.prefs_setting)
            prefs = preferenceManager.sharedPreferences
            biliprefs = currentContext.getSharedPreferences(
                packageName + "_preferences",
                Context.MODE_MULTI_PROCESS
            )
            if (!prefs.getBoolean("hidden", false)) {
                val hiddenGroup = findPreference("hidden_group") as PreferenceCategory
                preferenceScreen.removePreference(hiddenGroup)
            }
            findPreference("version").summary = BuildConfig.VERSION_NAME
            findPreference("version").onPreferenceClickListener = this
            findPreference("custom_splash").onPreferenceChangeListener = this
            findPreference("custom_splash_logo").onPreferenceChangeListener = this
            findPreference("save_log").summary =
                moduleRes.getString(R.string.save_log_summary).format(logFile.absolutePath)
            findPreference("custom_server").onPreferenceClickListener = this
            findPreference("test_upos").onPreferenceClickListener = this
            findPreference("customize_bottom_bar")?.onPreferenceClickListener = this
            findPreference("pref_export").onPreferenceClickListener = this
            findPreference("pref_import").onPreferenceClickListener = this
            findPreference("export_video")?.onPreferenceClickListener = this
            findPreference("hide_low_play_count_recommend")?.onPreferenceClickListener = this
            findPreference("keywords_filter_title_recommend")?.onPreferenceClickListener = this
            findPreference("custom_subtitle")?.onPreferenceChangeListener = this
            findPreference("customize_accessKey").onPreferenceClickListener = this
            checkCompatibleVersion()
            checkUpdate()
        }

        override fun onDestroy() {
            super.onDestroy()
            scope.cancel()
        }

        private fun checkUpdate() {
            val url = URL(moduleRes.getString(R.string.version_url))
            scope.launch {
                val result = fetchJson(url) ?: return@launch
                val newestVer = result.optString("name")
                if (newestVer.isNotEmpty() && BuildConfig.VERSION_NAME != newestVer) {
                    findPreference("version").summary = "${BuildConfig.VERSION_NAME}（最新版$newestVer）"
                    (findPreference("about") as PreferenceCategory).addPreference(
                        Preference(
                            activity
                        ).apply {
                            key = "update"
                            title = moduleRes.getString(R.string.update_title)
                            summary = result.optString("body").substringAfterLast("更新日志\r\n").run {
                                if (isNotEmpty()) this else moduleRes.getString(R.string.update_summary)
                            }
                            onPreferenceClickListener = this@PrefsFragment
                            order = 1
                        })
                }
            }
        }

        private fun checkCompatibleVersion() {
            val versionCode = getVersionCode(packageName)
            var supportLiveHook = false
            var supportAdd4K = false
            var supportMusicNotificationHook = true
            var supportDark = true
            var supportCommentFloor = false
            var supportAddChannel = false
            var supportCustomizeTab = true
            val supportFullSplash = try {
                instance.splashInfoClass?.getMethod("getMode") != null
            } catch (e: Throwable) {
                false
            }
            val supportMain = !isBuiltIn || !is64 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            when (platform) {
                "android_i" -> {
                    if (versionCode in 2050410..2080109) supportLiveHook = true
                    if (versionCode < 2050410) supportDark = false
                    if (versionCode < 3000500) supportCommentFloor = true
                    supportAdd4K = true
                }
                "android_b" -> {
                    if (versionCode < 6080000) supportAdd4K = true
                    if (versionCode < 6000000) supportDark = false
                    if (versionCode < 6180000) supportCommentFloor = true
                    if (versionCode >= 6270000) supportAddChannel = true
                }
                "android" -> {
                    if (versionCode !in 6000000 until 6120000) supportDark = false
                    if (versionCode < 6180000) supportCommentFloor = true
                    if (versionCode >= 6270000) supportAddChannel = true
                }
                "android_hd" -> {
                    supportDark = false
                    supportCustomizeTab = false
                }
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
                supportMusicNotificationHook = false
            val supportSplashHook = instance.brandSplashClass != null
            val supportDrawer = instance.homeUserCenterClass != null
            val supportTeenagersMode = instance.teenagersModeDialogActivityClass != null
            if (!supportDrawer)
                disablePreference("drawer")
            if (!supportSplashHook) {
                disablePreference("custom_splash")
                disablePreference("custom_splash_logo")
            }
            if (!supportFullSplash) {
                disablePreference("full_splash")
            }
            if (!supportLiveHook) {
                disablePreference("add_live")
            }
            if (!supportAdd4K) {
                disablePreference("add_4k")
            }
            if (!supportMusicNotificationHook) {
                disablePreference(
                    "music_notification",
                    moduleRes.getString(R.string.os_not_support)
                )
            }
            if (!supportMain) {
                disablePreference("main_func", "Android O以下系统不支持64位Xpatch版，请使用32位版")
            }
            if (!supportDark) {
                disablePreference("follow_dark")
            }
            if (!supportCommentFloor) {
                disablePreference("comment_floor")
            }
            if (!supportTeenagersMode) {
                disablePreference("teenagers_mode_dialog")
            }
            if (!supportAddChannel) {
                disablePreference("add_channel")
            }
            if (!supportCustomizeTab) {
                disablePreference("customize_home_tab_title")
                disablePreference("customize_bottom_bar_title")
            }
        }

        private fun disablePreference(
            name: String,
            message: String = moduleRes.getString(R.string.not_support)
        ) {
            findPreference(name)?.run {
                isEnabled = false
                summary = message
                if (this is SwitchPreference) this.isChecked = false
            }
        }

        private fun showCustomSubtitle() {
            CustomSubtitleDialog(activity, prefs).show()
        }

        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            when (preference.key) {
                "custom_splash" -> {
                    if (newValue as Boolean)
                        selectImage(SPLASH_SELECTION)
                }
                "custom_splash_logo" -> {
                    if (newValue as Boolean)
                        selectImage(LOGO_SELECTION)
                }
                "custom_subtitle" -> {
                    if (newValue as Boolean) {
                        showCustomSubtitle()
                    }
                }
            }
            return true
        }

        private fun selectImage(action: Int): Boolean {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)

            try {
                startActivityForResult(Intent.createChooser(intent, "选择一张图片"), action)
            } catch (ex: ActivityNotFoundException) {
                Log.toast("请安装文件管理器")
            }
            return true
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            when (requestCode) {
                SPLASH_SELECTION, LOGO_SELECTION -> {
                    val destFile = when (requestCode) {
                        SPLASH_SELECTION ->
                            File(currentContext.filesDir, SplashHook.SPLASH_IMAGE)
                        LOGO_SELECTION ->
                            File(currentContext.filesDir, SplashHook.LOGO_IMAGE)
                        else -> null
                    } ?: return
                    val uri = data?.data
                    if (resultCode == RESULT_CANCELED || uri == null) {
                        destFile.delete()
                        return
                    }
                    val stream = ByteArrayOutputStream()
                    stream.flush()
                    MediaStore.Images.Media.getBitmap(activity.contentResolver, uri)
                        .compress(Bitmap.CompressFormat.PNG, 100, stream)
                    val dest = FileOutputStream(destFile)
                    stream.writeTo(dest)
                }
                PREF_EXPORT, PREF_IMPORT -> {
                    val file = File(currentContext.filesDir, "../shared_prefs/biliroaming.xml")
                    val uri = data?.data
                    if (resultCode == RESULT_CANCELED || uri == null) return
                    when (requestCode) {
                        PREF_IMPORT -> {
                            try {
                                file.outputStream().use { out ->
                                    activity.contentResolver.openInputStream(uri)?.copyTo(out)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Log.toast(e.message ?: "未知错误", true)
                            }
                            Log.toast("请至少重新打开哔哩漫游设置", true)
                        }
                        PREF_EXPORT -> {
                            try {
                                file.inputStream().use { `in` ->
                                    activity.contentResolver.openOutputStream(uri)
                                        ?.let { `in`.copyTo(it) }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Log.toast(e.message ?: "未知错误", true)
                            }
                        }
                    }
                }
                VIDEO_EXPORT -> {
                    val videosToExport = VideoExportDialog.videosToExport
                    VideoExportDialog.videosToExport = emptySet()
                    val uri = data?.data
                    if (resultCode == RESULT_CANCELED || uri == null) return
                    val targetDir = DocumentFile.fromTreeUri(activity, uri) ?: return
                    try {
                        videosToExport.forEach { video ->
                            targetDir.findOrCreateDir(video.parentFile!!.name)
                                ?.let { DocumentFile.fromFile(video).copyTo(activity, it) }
                        }
                        Log.toast("导出成功", true)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Log.toast("${e.message}")
                    }
                }
            }

            super.onActivityResult(requestCode, resultCode, data)
        }


        private fun onVersionClick(): Boolean {
            if (prefs.getBoolean("hidden", false) || counter == 7) return true
            counter++
            if (counter == 7) {
                prefs.edit()?.putBoolean("hidden", true)?.apply()
                Log.toast("已开启隐藏功能，重启应用生效")
            } else if (counter >= 4) {
                Log.toast("再按${7 - counter}次开启隐藏功能")
            }

            return true
        }

        private fun onUpdateClick(): Boolean {
            val uri = Uri.parse(moduleRes.getString(R.string.update_url))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
            return true
        }

        private fun onCustomServerClick(): Boolean {
            AlertDialog.Builder(activity).run {
                val layout = moduleRes.getLayout(R.layout.cutomize_backup_dialog)
                val inflater = LayoutInflater.from(context)
                val view = inflater.inflate(layout, null)
                val editTexts = arrayOf(
                    view.findViewById<EditText>(R.id.cn_server),
                    view.findViewById(R.id.hk_server),
                    view.findViewById(R.id.tw_server),
                    view.findViewById(R.id.th_server)
                )
                editTexts.forEach { it.setText(prefs.getString(it.tag.toString(), "")) }
                setTitle("设置解析服务器")
                setView(view)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    editTexts.forEach {
                        val host = it.text.toString()
                        if (host.isNotEmpty())
                            prefs.edit().putString(it.tag.toString(), host).apply()
                        else
                            prefs.edit().remove(it.tag.toString()).apply()
                    }
                }
                setNegativeButton("获取公共解析服务器") { _, _ ->
                    val uri = Uri.parse(moduleRes.getString(R.string.server_url))
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    startActivity(intent)
                }
                show()
            }
            return true
        }

        private fun onTestUposClick(): Boolean {
            SpeedTestDialog(findPreference("upos_host") as ListPreference, activity).show()
            return true
        }

        private fun onCustomizeBottomBarClick(): Boolean {
            AlertDialog.Builder(activity).apply {
                val bottomItems = JsonHook.bottomItems
                setTitle(moduleRes.getString(R.string.customize_bottom_bar_title))
                setPositiveButton(android.R.string.ok) { _, _ ->
                    val hideItems = mutableSetOf<String>()
                    bottomItems.forEach {
                        if (it.showing.not()) {
                            hideItems.add(it.id ?: "")
                        }
                    }
                    sPrefs.edit().putStringSet("hided_bottom_items", hideItems).apply()
                }
                setNegativeButton(android.R.string.cancel, null)
                val names = Array(bottomItems.size) { i ->
                    "${bottomItems[i].name} (${bottomItems[i].id}) (${bottomItems[i].uri})"
                }
                setNeutralButton("重置") { _, _ ->
                    sPrefs.edit().remove("hided_bottom_items").apply()
                }
                val showings = BooleanArray(bottomItems.size) { i ->
                    bottomItems[i].showing
                }
                setMultiChoiceItems(names, showings) { _, which, isChecked ->
                    bottomItems[which].showing = isChecked
                }
            }.show()
            return true
        }

        private fun onCustomizeDanmakuConfigClick(): Boolean {
            AlertDialog.Builder(activity).run {
                val layout = moduleRes.getLayout(R.layout.cutomize_danmaku_config_dialog)
                val inflater = LayoutInflater.from(context)
                val view = inflater.inflate(layout, null)
                val editTexts = arrayOf(
                    view.findViewById<EditText>(R.id.danmaku_textsize_scale_factor),
                    view.findViewById(R.id.danmaku_stroke_width_scaling),
                    view.findViewById(R.id.danmaku_duration_factor),
                    view.findViewById(R.id.danmaku_alpha_factor),
                    view.findViewById(R.id.danmaku_max_on_screen),
                    view.findViewById(R.id.danmaku_screen_domain)
                )
                editTexts.filter {
                    biliprefs.contains(it.tag.toString())
                }.forEach {
                    it.setText(
                        biliprefs.getFloat(it.tag.toString(), it.hint.toString().toFloat())
                            .toString()
                    )
                }
                setTitle(moduleRes.getString(R.string.customize_danmaku_config_title))
                setView(view)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    editTexts.forEach {
                        val value = it.text.toString()
                        if (value.isNotEmpty())
                            biliprefs.edit().putFloat(it.tag.toString(), value.toFloat()).apply()
                        else
                            biliprefs.edit().remove(it.tag.toString()).apply()
                    }
                }
                show()
            }
            return true
        }

        private fun onPrefExportClick(): Boolean {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.type = "text/xml"
            intent.putExtra(Intent.EXTRA_TITLE, "biliroaming.xml")
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            try {
                startActivityForResult(Intent.createChooser(intent, "保存配置文件"), PREF_EXPORT)
            } catch (ex: ActivityNotFoundException) {
                Log.toast("请安装文件管理器")
            }
            return true
        }

        private fun onPrefImportClick(): Boolean {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "text/xml"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            try {
                startActivityForResult(Intent.createChooser(intent, "选择配置文件"), PREF_IMPORT)
            } catch (ex: ActivityNotFoundException) {
                Log.toast("请安装文件管理器")
            }
            return true
        }

        private fun onExportVideoClick(): Boolean {
            VideoExportDialog(activity, this).show()
            return true
        }

        private fun onCustomizeAccessKeyClick(): Boolean {
            AlertDialog.Builder(activity).run {
                val layout = moduleRes.getLayout(R.layout.cutomize_backup_dialog)
                val inflater = LayoutInflater.from(context)
                val view = inflater.inflate(layout, null)
                val editTexts = arrayOf(
                    view.findViewById<EditText>(R.id.cn_server),
                    view.findViewById(R.id.hk_server),
                    view.findViewById(R.id.tw_server),
                    view.findViewById(R.id.th_server)
                )
                editTexts.forEach {
                    it.setText(prefs.getString("${it.tag}_accessKey", ""))
                    it.hint = ""
                }
                setTitle(R.string.customize_accessKey_title)
                setView(view)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    editTexts.forEach {
                        val accessKey = it.text.toString()
                        val key = "${it.tag}_accessKey"
                        if (accessKey.isNotEmpty()) prefs.edit().putString(key, accessKey).apply()
                        else prefs.edit().remove(key).apply()
                    }
                }
                show()
            }
            return true
        }

        private fun onHideLowPlayCountRecommendClick(isChecked: Boolean): Boolean {
            if (!isChecked) return true
            val tv = EditText(activity)
            tv.inputType = InputType.TYPE_CLASS_NUMBER
            tv.filters = arrayOf(InputFilter.LengthFilter(7))
            tv.setText(sPrefs.getLong("hide_low_play_count_recommend_limit", 100).toString())
            AlertDialog.Builder(activity).run {
                setTitle("设置隐藏播放量")
                setView(tv)
                setPositiveButton(android.R.string.ok, null)
                setNegativeButton(android.R.string.cancel, null)
                setCancelable(false)
                show()
            }.let {
                it.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { _ ->
                    if (tv.text.isEmpty()) {
                        Log.toast("你好像还没有输入内容> <")
                    } else {
                        tv.text.toString().toLong().let { count ->
                            if (count > 1_000_000) {
                                Log.toast("你输入的数字太大惹 伦家会坏掉的> <")
                            } else {
                                sPrefs.edit()
                                    .putLong("hide_low_play_count_recommend_limit", count)
                                    .apply()
                                Log.toast("保存成功 重启后生效")
                                it.dismiss()
                            }
                        }
                    }
                }
            }
            return true
        }

        private fun onKeywordsFilterTitleRecommendClick(isChecked: Boolean): Boolean {
            if (!isChecked) return true
            val tv = EditText(activity)
            tv.setText(sPrefs.getString("keywords_filter_title_recommend_list", ""))
            AlertDialog.Builder(activity).run {
                setTitle("设置标题过滤关键词")
                setView(tv)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    sPrefs.edit()
                        .putString("keywords_filter_title_recommend_list", tv.text.toString())
                        .apply()
                    Log.toast("保存成功 重启后生效")
                }
                setNegativeButton(android.R.string.cancel, null)
                setNeutralButton("添加分隔符", null)
                setCancelable(false)
                show()
            }.let {
                it.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    when {
                        tv.text.isEmpty() -> {
                            Log.toast("你好像还没有输入内容> <")
                        }
                        tv.text.endsWith('|') -> {
                            Log.toast("啊嘞 上一个分隔符后面好像还没有东西> <")
                        }
                        else -> {
                            tv.text.append('|')
                        }
                    }
                }
            }
            return true
        }

        override fun onPreferenceClick(preference: Preference) = when (preference.key) {
            "version" -> onVersionClick()
            "update" -> onUpdateClick()
            "custom_server" -> onCustomServerClick()
            "test_upos" -> onTestUposClick()
            "customize_bottom_bar" -> onCustomizeBottomBarClick()
            "pref_export" -> onPrefExportClick()
            "pref_import" -> onPrefImportClick()
            "export_video" -> onExportVideoClick()
            "customize_accessKey" -> onCustomizeAccessKeyClick()
            "hide_low_play_count_recommend" -> onHideLowPlayCountRecommendClick((preference as SwitchPreference).isChecked)
            "keywords_filter_title_recommend" -> onKeywordsFilterTitleRecommendClick((preference as SwitchPreference).isChecked)
            else -> false
        }
    }

    init {
        val activity = context as Activity
        addModulePath(context)
        val prefsFragment = PrefsFragment()
        activity.fragmentManager.beginTransaction().add(prefsFragment, "Setting").commit()
        activity.fragmentManager.executePendingTransactions()

        prefsFragment.onActivityCreated(null)

        val unhook =
            Preference::class.java.hookAfterMethod("onCreateView", ViewGroup::class.java) { param ->
                if (PreferenceCategory::class.java.isInstance(param.thisObject) && TextView::class.java.isInstance(
                        param.result
                    )
                ) {
                    val textView = param.result as TextView
                    if (textView.textColors.defaultColor == -13816531)
                        textView.setTextColor(Color.GRAY)
                }
            }

        setView(prefsFragment.view)
        setTitle("哔哩漫游设置")
        setNegativeButton("返回") { _, _ ->
            unhook?.unhook()
        }
        setPositiveButton("确定并重启客户端") { _, _ ->
            unhook?.unhook()
            restartApplication(activity)
        }
    }

    companion object {
        @JvmStatic
        fun restartApplication(activity: Activity) {
            // https://stackoverflow.com/a/58530756
            val pm = activity.packageManager
            val intent = pm.getLaunchIntentForPackage(activity.packageName)
            activity.finishAffinity()
            activity.startActivity(intent)
            exitProcess(0)
        }

        @JvmStatic
        fun addModulePath(context: Context) {
            val assets = context.resources.assets
            assets.callMethod(
                "addAssetPath",
                modulePath
            )
        }

        const val SPLASH_SELECTION = 0
        const val LOGO_SELECTION = 1
        const val PREF_IMPORT = 2
        const val PREF_EXPORT = 3
        const val VIDEO_EXPORT = 4
    }

}
