@file:Suppress("DEPRECATION")

package me.iacn.biliroaming

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Activity.RESULT_CANCELED
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.*
import android.provider.MediaStore
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.hook.JsonHook
import me.iacn.biliroaming.hook.SplashHook
import me.iacn.biliroaming.utils.*
import me.iacn.biliroaming.utils.UposReplaceHelper.isLocatedCn
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
        private var customSubtitleDialog: CustomSubtitleDialog? = null
        private lateinit var listView: ListView
        private lateinit var adapter: BaseAdapter
        private var searchItems = listOf<SearchItem>()

        private var ListAdapter.preferenceList: List<Preference>
            get() = getObjectFieldAs("mPreferenceList")
            set(value) {
                setObjectField("mPreferenceList", value)
            }

        @Deprecated("Deprecated in Java")
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            preferenceManager.sharedPreferencesName = "biliroaming"
            prefs = preferenceManager.sharedPreferences
            checkUposServer()
            addPreferencesFromResource(R.xml.prefs_setting)
            biliprefs = currentContext.getSharedPreferences(
                packageName + "_preferences",
                Context.MODE_MULTI_PROCESS
            )
            if (!prefs.getBoolean("hidden", false)) {
                val hiddenGroup = findPreference("hidden_group") as PreferenceCategory
                preferenceScreen.removePreference(hiddenGroup)
            }
            findPreference("version")?.summary = BuildConfig.VERSION_NAME
            findPreference("version")?.onPreferenceClickListener = this
            findPreference("custom_splash")?.onPreferenceChangeListener = this
            findPreference("custom_splash_logo")?.onPreferenceChangeListener = this
            findPreference("save_log")?.summary =
                context.getString(R.string.save_log_summary).format(logFile.absolutePath)
            findPreference("custom_server")?.onPreferenceClickListener = this
            findPreference("test_upos")?.onPreferenceClickListener = this
            findPreference("customize_bottom_bar")?.onPreferenceClickListener = this
            findPreference("pref_export")?.onPreferenceClickListener = this
            findPreference("pref_import")?.onPreferenceClickListener = this
            findPreference("export_video")?.onPreferenceClickListener = this
            findPreference("home_filter")?.onPreferenceClickListener = this
            findPreference("custom_subtitle")?.onPreferenceChangeListener = this
            findPreference("danmaku_filter")?.onPreferenceClickListener = this
            findPreference("default_speed").onPreferenceClickListener = this
            findPreference("customize_accessKey")?.onPreferenceClickListener = this
            findPreference("share_log")?.onPreferenceClickListener = this
            findPreference("customize_drawer")?.onPreferenceClickListener = this
            findPreference("custom_link")?.onPreferenceClickListener = this
            findPreference("add_custom_button")?.onPreferenceChangeListener = this
            findPreference("customize_dynamic")?.onPreferenceClickListener = this
            findPreference("filter_search")?.onPreferenceClickListener = this
            findPreference("filter_comment")?.onPreferenceClickListener = this
            findPreference("copy_access_key")?.onPreferenceClickListener = this
            checkCompatibleVersion()
            searchItems = retrieve(preferenceScreen)
            checkUpdate()
        }

        @Deprecated("Deprecated in Java")
        override fun onDestroy() {
            super.onDestroy()
            scope.cancel()
        }

        @Deprecated("Deprecated in Java")
        override fun onActivityCreated(savedInstanceState: Bundle?) {
            super.onActivityCreated(savedInstanceState)
            listView = view?.findViewById(android.R.id.list) ?: return
            adapter = listView.adapter as BaseAdapter
        }

        private fun retrieve(group: PreferenceGroup): List<SearchItem> = buildList {
            for (i in 0 until group.preferenceCount) {
                val preference = group.getPreference(i)
                val entries = when (preference) {
                    is ListPreference -> preference.entries
                    is MultiSelectListPreference -> preference.entries
                    else -> arrayOf()
                }.orEmpty()
                val searchItem = SearchItem(
                    preference,
                    preference.key.orEmpty(),
                    preference.title ?: "",
                    preference.summary ?: "",
                    entries,
                    preference is PreferenceGroup,
                )
                searchItem.appendExtraKeywords()
                add(searchItem)
                if (preference is PreferenceGroup) {
                    addAll(retrieve(preference))
                }
            }
        }

        private fun SearchItem.appendExtraKeywords() = when (key) {
            "custom_subtitle" -> {
                extra.add(context.getString(R.string.custom_subtitle_remove_bg))
                extra.add(context.getString(R.string.custom_subtitle_bold))
                extra.add(context.getString(R.string.custom_subtitle_font_size))
                extra.add(context.getString(R.string.custom_subtitle_stroke_color))
                extra.add(context.getString(R.string.custom_subtitle_stroke_width))
                extra.add(context.getString(R.string.custom_subtitle_offset))
            }

            "home_filter" -> {
                extra.add(context.getString(R.string.apply_to_relate_title))
                extra.add(context.getString(R.string.hide_low_play_count_recommend_title))
                extra.add(context.getString(R.string.hide_low_play_count_recommend_summary))
                extra.add(context.getString(R.string.hide_short_duration_recommend_title))
                extra.add(context.getString(R.string.hide_long_duration_recommend_title))
                extra.add(context.getString(R.string.hide_duration_recommend_summary))
                extra.add(context.getString(R.string.keywords_filter_recommend_summary))
            }

            "customize_bottom_bar" -> {
                extra.addAll(JsonHook.bottomItems.mapNotNull { it.name })
            }

            "customize_drawer" -> {
                extra.addAll(JsonHook.drawerItems.mapNotNull { it.name })
            }

            "customize_dynamic" -> {
                extra.add(context.getString(R.string.customize_dynamic_prefer_video_tab))
                extra.add(context.getString(R.string.purify_city_title))
                extra.add(context.getString(R.string.purify_campus_title))
                extra.add(context.getString(R.string.customize_dynamic_all_rm_topic_title))
                extra.add(context.getString(R.string.customize_dynamic_all_rm_up_title))
                extra.add(context.getString(R.string.customize_dynamic_video_rm_up_title))
                extra.add(context.getString(R.string.customize_dynamic_filter_apply_to_video))
                extra.add(context.getString(R.string.customize_dynamic_rm_blocked_title))
                extra.addAll(context.resources.getStringArray(R.array.dynamic_entries))
            }

            "pref_import", "pref_export" -> {
                extra.add(context.getString(R.string.pref_backup))
            }

            else -> false
        }

        fun search(text: String) {
            val preferences = if (text.isEmpty()) {
                searchItems.map { it.restore(); it.preference }
            } else {
                searchItems.sortedByDescending { it.calcScoreAndApplyHintBy(text) }
                    .filterNot { it.cacheScore == 0 }.map { it.preference }
            }
            adapter.preferenceList = preferences
            adapter.notifyDataSetChanged()
            listView.forceSetSelection(0)
        }

        private fun checkUposServer() {
            val currentServer = prefs.getString("upos_host", null).orEmpty()
            val serverList = context.resources.getStringArray(R.array.upos_values)
            if (currentServer !in serverList) {
                scope.launch(Dispatchers.IO) {
                    val defaultServer =
                        if (isLocatedCn) serverList[1] else """$1"""
                    prefs.edit().putString("upos_host", defaultServer).apply()
                }
            }
        }

        private fun checkUpdate() {
            val url = URL(context.getString(R.string.version_url))
            scope.launch {
                val result = fetchJson(url) ?: return@launch
                val newestVer = result.optString("name")
                val versionName = BuildConfig.VERSION_NAME
                if (newestVer.isNotEmpty() && versionName != newestVer) {
                    searchItems.forEach { it.restore() }
                    findPreference("version").summary = "${versionName}（最新版$newestVer）"
                    (findPreference("about") as PreferenceCategory).addPreference(
                        Preference(activity).apply {
                            key = "update"
                            title = context.getString(R.string.update_title)
                            summary = result.optString("body").substringAfterLast("更新日志\r\n")
                                .ifEmpty { context.getString(R.string.update_summary) }
                            onPreferenceClickListener = this@PrefsFragment
                            order = 1
                        })
                    searchItems = retrieve(preferenceScreen)
                }
            }
        }

        private fun checkCompatibleVersion() {
            val versionCode = getVersionCode(packageName)
            var supportMusicNotificationHook = versionCode >= 7500300 &&
                    // from bilibili
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Build.MANUFACTURER.lowercase().equals("huawei")
            var supportCustomizeTab = true
            val supportFullSplash = try {
                instance.splashInfoClass?.getMethod("getMode") != null
            } catch (e: Throwable) {
                false
            }
            val supportMain = !isBuiltIn || !is64 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            var supportDrawer = instance.homeUserCenterClass != null
            var supportDrawerStyle = true
            val supportRevertLive = versionCode < 6830000
            var supportAddTag = true
            var supportCustomTheme = true
            when (platform) {
                "android_hd" -> {
                    supportCustomizeTab = false
                    supportDrawer = false
                    supportDrawerStyle = false
                    supportAddTag = false
                    supportCustomTheme = false
                }
            }
            val supportSplashHook = instance.brandSplashClass != null
            val supportTeenagersMode = instance.teenagersModeDialogActivityClass != null
            val supportStoryVideo = instance.storyVideoActivityClass != null
            val supportPurifyShare = instance.shareClickResultClass != null
            val supportDownloadThread = versionCode < 6630000 || versionCode >= 6900000
            if (!supportDrawer)
                disablePreference("drawer")
            if (!supportSplashHook) {
                disablePreference("custom_splash")
                disablePreference("custom_splash_logo")
            }
            if (!supportFullSplash) {
                disablePreference("full_splash")
            }
            if (!supportMusicNotificationHook) {
                if (versionCode >= 7500300) {
                    disablePreference(
                            "music_notification",
                            context.getString(R.string.os_not_support))
                } else {
                    disablePreference("music_notification")
                }
            }
            if (!supportMain) {
                disablePreference("main_func", "Android O以下系统不支持64位Xpatch版，请使用32位版")
            }
            if (!supportTeenagersMode) {
                disablePreference("teenagers_mode_dialog")
            }
            if (!supportCustomizeTab) {
                disablePreference("customize_home_tab_title")
                disablePreference("customize_bottom_bar_title")
            }
            if (!supportStoryVideo) {
                disablePreference("replace_story_video")
            }
            if (!supportDrawerStyle) {
                disablePreference("drawer_style_switch")
                disablePreference("drawer_style")
            }
            if (!supportPurifyShare) {
                disablePreference("purify_share")
                disablePreference("mini_program")
            }
            if (!supportDownloadThread) {
                disablePreference("custom_download_thread")
            }
            if (!supportRevertLive) {
                disablePreference("revert_live_room_feed")
            }
            if (!supportAddTag) {
                disablePreference("add_bangumi")
                disablePreference("add_korea")
                disablePreference("add_movie")
            }
            if (!supportCustomTheme) {
                disablePreference("custom_theme")
            }
        }

        private fun disablePreference(
            name: String,
            message: String = context.getString(R.string.not_support)
        ) {
            findPreference(name)?.run {
                isEnabled = false
                summary = message
                if (this is SwitchPreference) this.isChecked = false
            }
        }

        private fun showCustomSubtitle() {
            CustomSubtitleDialog(activity, this, prefs).also {
                customSubtitleDialog = it
            }.show()
        }

        @Deprecated("Deprecated in Java")
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
                    if (newValue as Boolean)
                        showCustomSubtitle()
                }

                "add_custom_button" -> {
                    if (newValue as Boolean)
                        onAddCustomButtonClick()
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

        @Deprecated("Deprecated in Java")
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            customSubtitleDialog?.onActivityResult(requestCode, resultCode, data)
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
                                file.bufferedWriter().use { output ->
                                    activity.contentResolver.openInputStream(uri)
                                        ?.use { it.bufferedReader().copyTo(output) }
                                }
                            } catch (e: Exception) {
                                Log.toast(e.message ?: "未知错误", true, alsoLog = true)
                            }
                            Log.toast("请至少重新打开哔哩漫游设置", true)
                        }

                        PREF_EXPORT -> {
                            try {
                                file.bufferedReader().use { input ->
                                    activity.contentResolver.openOutputStream(uri)?.use {
                                        it.bufferedWriter().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.toast(e.message ?: "未知错误", true, alsoLog = true)
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
                                ?.let { DocumentFile.fromFile(video).copyTo(it) }
                        }
                        Log.toast("导出成功", true)
                    } catch (e: Exception) {
                        Log.toast("${e.message}", alsoLog = true)
                    }
                }
            }

            super.onActivityResult(requestCode, resultCode, data)
        }


        private fun onVersionClick(): Boolean {
            if (prefs.getBoolean("hidden", false) || counter == 7) return true
            if (++counter == 7) {
                prefs.edit()?.putBoolean("hidden", true)?.apply()
                Log.toast("已开启隐藏功能，重启应用生效", true)
            } else if (counter >= 4) {
                Log.toast("再按${7 - counter}次开启隐藏功能", true)
            }

            return true
        }

        private fun onUpdateClick(): Boolean {
            val uri = Uri.parse(context.getString(R.string.update_url))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
            return true
        }

        private fun onCustomServerClick(): Boolean {
            AlertDialog.Builder(activity).run {
                val view = context.inflateLayout(R.layout.customize_backup_dialog)
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
                            prefs.edit().putString(
                                it.tag.toString(),
                                host.replace(Regex("^https?://"), "")
                            ).apply()
                        else
                            prefs.edit().remove(it.tag.toString()).apply()
                    }
                }
                setNegativeButton("获取公共解析服务器") { _, _ ->
                    val uri = Uri.parse(context.getString(R.string.server_url))
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    startActivity(intent)
                }
                show()
            }
            return true
        }

        private fun onDanmakuFilterClick(): Boolean {
            AlertDialog.Builder(activity).run {
                val view = context.inflateLayout(R.layout.seekbar_dialog)
                val seekBar = view.findViewById<SeekBar>(R.id.seekBar)
                seekBar.max = 12
                val tvHint = view.findViewById<TextView>(R.id.tvHint)
                seekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?, progress: Int, fromUser: Boolean
                    ) {
                        tvHint.text =
                            context.getString(R.string.danmaku_filter_weight_hint, progress)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
                val current = prefs.getInt("danmaku_filter_weight", 0)
                tvHint.text = context.getString(R.string.danmaku_filter_weight_hint, current)
                seekBar.progress = current
                setTitle(R.string.danmaku_filter_title)
                setNegativeButton(android.R.string.cancel, null)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    prefs.edit().putInt("danmaku_filter_weight", seekBar.progress).apply()
                }
                setView(view)
                show()
            }
            return true
        }

        private fun onDefaultSpeedClick(): Boolean {
            AlertDialog.Builder(activity).run {
                val view = context.inflateLayout(R.layout.seekbar_dialog)
                val seekBar = view.findViewById<SeekBar>(R.id.seekBar)
                seekBar.max = 100
                val tvHint = view.findViewById<TextView>(R.id.tvHint)
                seekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                    @SuppressLint("SetTextI18n")
                    override fun onProgressChanged(
                        seekBar: SeekBar?, progress: Int, fromUser: Boolean
                    ) {
                        tvHint.text = "${progress * 10}%"
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
                val current = prefs.getInt("default_speed", 100)
                @SuppressLint("SetTextI18n")
                tvHint.text = "${current * 10}%"
                seekBar.progress = current / 10
                setTitle(R.string.default_speed_title)
                setNegativeButton(android.R.string.cancel, null)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    prefs.edit().putInt("default_speed", seekBar.progress * 10).apply()
                }
                setView(view)
                show()

            }
            return true
        }

        private fun onTestUposClick(): Boolean {
            SpeedTestDialog(activity, prefs).show()
            return true
        }

        private fun onCustomizeBottomBarClick(): Boolean {
            AlertDialog.Builder(activity).apply {
                val bottomItems = JsonHook.bottomItems
                val ids = bottomItems.map { it.id }.toHashSet()
                sPrefs.getStringSet("hided_bottom_items", null)?.forEach {
                    if (it.isEmpty() || ids.contains(it)) return@forEach
                    bottomItems.add(JsonHook.BottomItem("未知", null, it, false))
                }
                setTitle(context.getString(R.string.customize_bottom_bar_title))
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
                val view = context.inflateLayout(R.layout.customize_backup_dialog)
                val editTexts = arrayOf(
                    view.findViewById<EditText>(R.id.cn_server),
                    view.findViewById(R.id.hk_server),
                    view.findViewById(R.id.tw_server),
                    view.findViewById(R.id.th_server)
                )
                editTexts.forEach {
                    val accessKey = prefs.getString("${it.tag}_accessKey", "")
                    val platform = prefs.getString("${it.tag}_platform", null)
                    val s = if (platform != null) "$accessKey;$platform" else accessKey
                    it.setText(s)
                    it.hint = ""
                }
                setTitle(R.string.customize_accessKey_title)
                setView(view)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    val edit = prefs.edit()
                    editTexts.forEach {
                        val s = it.text.toString()
                        val accessKey = s.substringBefore(';')
                        val platform = s.substringAfter(';', "")
                        val platformKey = "${it.tag}_platform"
                        val key = "${it.tag}_accessKey"
                        if (accessKey.isNotEmpty()) edit.putString(key, accessKey).apply()
                        else edit.remove(key).apply()
                        if (platform.isNotEmpty()) edit.putString(platformKey, platform).apply()
                        else edit.remove(platformKey).apply()
                    }
                }
                show()
            }
            return true
        }

        private fun onHomeFilterClick(): Boolean {
            HomeFilterDialog(activity, prefs).show()
            return true
        }

        private fun onShareLogClick(): Boolean {
            if ((logFile.exists().not() && oldLogFile.exists().not()) || shouldSaveLog.not()) {
                Log.toast("没有保存过日志", force = true)
                return true
            }
            AlertDialog.Builder(activity)
                .setTitle(context.getString(R.string.share_log_title))
                .setItems(arrayOf("log.txt", "old_log.txt (崩溃相关发这个)")) { _, which ->
                    val toShareLog = if (which == 0) logFile else oldLogFile
                    if (toShareLog.exists()) {
                        toShareLog.copyTo(
                            File(activity.cacheDir, "boxing/log.txt"),
                            overwrite = true
                        )
                        val uri =
                            Uri.parse("content://${activity.packageName}.fileprovider/internal/log.txt")
                        activity.startActivity(Intent.createChooser(Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            setDataAndType(uri, "text/log")
                        }, context.getString(R.string.share_log_title)))
                    } else {
                        Log.toast("日志文件不存在", force = true)
                    }
                }
                .show()
            return true
        }

        private fun onCustomizeDrawerClick(): Boolean {
            AlertDialog.Builder(activity).apply {
                val drawerItems = JsonHook.drawerItems
                val ids = drawerItems.map { it.id }.toHashSet()
                sPrefs.getStringSet("hided_drawer_items", null)?.forEach {
                    if (it.isEmpty() || ids.contains(it)) return@forEach
                    JsonHook.drawerItems.add(JsonHook.BottomItem("未知", null, it, false))
                }
                setTitle(context.getString(R.string.customize_drawer_title))
                setPositiveButton(android.R.string.ok) { _, _ ->
                    val hideItems = mutableSetOf<String>()
                    JsonHook.drawerItems.forEach {
                        if (it.showing.not()) {
                            hideItems.add(it.id ?: "")
                        }
                    }
                    sPrefs.edit().putStringSet("hided_drawer_items", hideItems).apply()
                }
                setNegativeButton(android.R.string.cancel, null)
                val names = Array(drawerItems.size) { i ->
                    "${drawerItems[i].name} (${drawerItems[i].id}) (${drawerItems[i].uri})"
                }
                setNeutralButton("重置") { _, _ ->
                    sPrefs.edit().remove("hided_drawer_items").apply()
                }
                val showings = BooleanArray(drawerItems.size) { i ->
                    drawerItems[i].showing
                }
                setMultiChoiceItems(names, showings) { _, which, isChecked ->
                    drawerItems[which].showing = isChecked
                }
            }.show()
            return true
        }

        private fun onCustomLinkClick(): Boolean {
            val tv = EditText(activity)
            tv.setText(sPrefs.getString("custom_link", ""))
            tv.hint = "bilibili://user_center/vip"
            AlertDialog.Builder(activity).run {
                setTitle(context.getString(R.string.custom_link_summary))
                setView(tv)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    if (tv.text.toString().startsWith("bilibili://")) {
                        sPrefs.edit().putString("custom_link", tv.text.toString()).apply()
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(sPrefs.getString("custom_link", ""))
                        )
                        startActivity(intent)
                    } else {
                        Log.toast("格式不正确", force = true)
                    }
                }
                setNeutralButton("清空") { _, _ ->
                    sPrefs.edit().remove("custom_link").apply()
                }
                setNegativeButton(android.R.string.cancel, null)
                show()
            }
            return true
        }

        private fun onAddCustomButtonClick(): Boolean {
            AlertDialog.Builder(activity).run {
                val view = context.inflateLayout(R.layout.custom_button)
                val editTexts = arrayOf(
                    view.findViewById<EditText>(R.id.custom_button_id),
                    view.findViewById(R.id.custom_button_title),
                    view.findViewById(R.id.custom_button_uri),
                    view.findViewById(R.id.custom_button_icon)
                )
                editTexts.forEach {
                    it.setText(prefs.getString("${it.tag}", ""))
                }
                setTitle(R.string.add_custom_button_title)
                setView(view)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    editTexts.forEach {
                        val key = "${it.tag}"
                        val value = it.text.toString()
                        if (value.isNotEmpty()) prefs.edit().putString(key, value).apply()
                        else prefs.edit().remove(key).apply()
                    }
                }
                show()
            }
            return true
        }

        private fun onCustomDynamicClick(): Boolean {
            DynamicFilterDialog(activity, prefs).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.window?.clearFlags(
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                    )
                    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                }
            }.show()
            return true
        }

        private fun onFilterSearchClick(): Boolean {
            SearchFilterDialog(activity, prefs).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.window?.clearFlags(
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                    )
                    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                }
            }.show()
            return true
        }

        private fun onFilterCommentClick(): Boolean {
            CommentFilterDialog(activity, prefs).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.window?.clearFlags(
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                    )
                    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                }
            }.show()
            return true
        }

        private fun onCopyAccessKeyClick(): Boolean {
            val manager = context.getSystemService(ClipboardManager::class.java)

            manager.setPrimaryClip(ClipData.newPlainText("access_key", instance.accessKey))
            Toast.makeText(context, R.string.copy_access_key_toast, Toast.LENGTH_SHORT).show()

            return true
        }

        @Deprecated("Deprecated in Java")
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
            "home_filter" -> onHomeFilterClick()
            "share_log" -> onShareLogClick()
            "customize_drawer" -> onCustomizeDrawerClick()
            "custom_link" -> onCustomLinkClick()
            "customize_dynamic" -> onCustomDynamicClick()
            "danmaku_filter" -> onDanmakuFilterClick()
            "default_speed" -> onDefaultSpeedClick()
            "filter_search" -> onFilterSearchClick()
            "filter_comment" -> onFilterCommentClick()
            "copy_access_key" -> onCopyAccessKeyClick()
            else -> false
        }
    }

    class Hint(val hint: String, val startIdx: Int, val fullText: CharSequence)
    class SearchItem(
        val preference: Preference,
        val key: String,
        private val title: CharSequence,
        private val summary: CharSequence,
        private val entries: Array<out CharSequence>,
        private val isGroup: Boolean,
        val extra: MutableList<String> = mutableListOf(),
    ) {
        var cacheScore = 0
            private set

        fun calcScoreAndApplyHintBy(text: String): Int {
            if (text.isEmpty() || isGroup) {
                cacheScore = 0
                return 0
            }
            var score = 0
            var titleHint: Hint? = null
            var summaryHint: Hint? = null
            var otherHint: Hint? = null
            if (title.isNotEmpty() && title.indexOf(text, ignoreCase = true).takeIf { it != -1 }
                    ?.also { titleHint = Hint(text, it, title) } != null
            ) score += 12
            if (summary.isNotEmpty() && summary.indexOf(text, ignoreCase = true).takeIf { it != -1 }
                    ?.also { summaryHint = Hint(text, it, summary) } != null
            ) score += 6
            if (entries.isNotEmpty() && entries.firstNotNullOfOrNull { e ->
                    e.indexOf(text, ignoreCase = true).takeIf { it != -1 }
                        ?.also { otherHint = Hint(text, it, e) }
                } != null) {
                score += 3
            }
            if (extra.isNotEmpty() && extra.firstNotNullOfOrNull { e ->
                    e.indexOf(text, ignoreCase = true).takeIf { it != -1 }
                        ?.also { if (otherHint == null) otherHint = Hint(text, it, e) }
                } != null) {
                score += 2
            }
            cacheScore = score
            applyHint(titleHint, summaryHint, otherHint)
            return score
        }

        fun restore() {
            preference.title = title
            preference.summary = summary
        }

        private fun applyHint(titleHint: Hint?, summaryHint: Hint?, otherHint: Hint?) {
            preference.title = title.withHint(titleHint)
            if (titleHint == null && summaryHint != null) {
                preference.summary = summary.withHint(summaryHint)
            } else if (titleHint == null && otherHint != null) {
                preference.summary = SpannableStringBuilder(summary).apply {
                    if (isNotEmpty()) appendLine()
                    append(otherHint.fullText.withHint(otherHint, true))
                }
            } else {
                preference.summary = summary
            }
        }

        private fun CharSequence.withHint(hint: Hint?, other: Boolean = false): CharSequence {
            if (hint == null || hint.hint.isEmpty())
                return this
            val startIdx = hint.startIdx
            if (startIdx == -1) return this
            val endIdx = startIdx + hint.hint.length
            if (endIdx > length) return this
            val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            val hintColor = preference.context.getColor(R.color.text_search_hint)
            val colorSpan = ForegroundColorSpan(hintColor)
            val boldSpan = StyleSpan(Typeface.BOLD)
            return SpannableStringBuilder(this).apply {
                setSpan(colorSpan, startIdx, endIdx, flags)
                setSpan(boldSpan, startIdx, endIdx, flags)
                if (other) {
                    // to make other text smaller and append to summary
                    val sizeSpan = AbsoluteSizeSpan(12.sp, false)
                    setSpan(sizeSpan, 0, length, flags)
                }
            }
        }
    }

    private fun getContentView(fragment: PrefsFragment): View {
        val contentView = LinearLayout(fragment.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val searchBar = context.inflateLayout(R.layout.search_bar)
        val editView = searchBar.findViewById<EditText>(R.id.search)
        val clearView = searchBar.findViewById<View>(R.id.clear)
        searchBar.setOnClickListener {
            editView.requestFocus()
            context.getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(editView, 0)
        }
        editView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) = fragment.search(s?.toString()?.trim().orEmpty())
        })
        editView.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                fragment.search(v.text.toString().trim())
                true
            } else false
        }
        clearView.setOnClickListener {
            editView.setText("")
        }
        contentView.addView(searchBar)
        contentView.addView(fragment.view)
        return contentView
    }

    init {
        val activity = context as Activity
        activity.addModuleAssets()

        // dirty way to make list preference summary span style take effect,
        // we have no choice, see ListPreference#getSummary
        val summaryHook = ListPreference::class.java.hookBeforeMethod("getSummary") { param ->
            param.thisObject.setObjectField("mSummary", null)
        }

        val prefsFragment = PrefsFragment()
        activity.fragmentManager.beginTransaction().add(prefsFragment, "Setting").commit()
        activity.fragmentManager.executePendingTransactions()

        prefsFragment.onActivityCreated(null)

        val unhook = Preference::class.java.hookAfterMethod(
            "onCreateView", ViewGroup::class.java
        ) { param ->
            if (PreferenceCategory::class.java.isInstance(param.thisObject)
                && TextView::class.java.isInstance(param.result)
            ) {
                val textView = param.result as TextView
                if (textView.textColors.defaultColor == -13816531)
                    textView.setTextColor(Color.GRAY)
            }
        }

        setView(getContentView(prefsFragment))
        setTitle("哔哩漫游设置")
        setNegativeButton("返回", null)
        setPositiveButton("确定并重启客户端") { _, _ ->
            prefsFragment.preferenceManager.forceSavePreference()
            restartApplication(activity)
        }
        setOnDismissListener {
            unhook?.unhook()
            summaryHook?.unhook()
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

        @SuppressLint("CommitPrefEdits")
        @JvmStatic
        fun PreferenceManager.forceSavePreference() {
            sharedPreferences.let {
                val cm = (getObjectFieldOrNull("mEditor")
                    ?: it.edit()).callMethodOrNull("commitToMemory")
                val lock = it.getObjectFieldOrNull("mWritingToDiskLock") ?: return@let
                synchronized(lock) {
                    it.callMethodOrNull("writeToFile", cm, true)
                }
            }
        }

        fun show(context: Context) {
            try {
                SettingDialog(context).show()
            } catch (e: Resources.NotFoundException) {
                AlertDialog.Builder(context)
                    .setTitle("需要重启")
                    .setMessage("哔哩漫游更新了")
                    .setPositiveButton("重启") { _, _ ->
                        restartApplication(context as Activity)
                    }.show()
            }
        }

        const val SPLASH_SELECTION = 0
        const val LOGO_SELECTION = 1
        const val PREF_IMPORT = 2
        const val PREF_EXPORT = 3
        const val VIDEO_EXPORT = 4
    }
}
