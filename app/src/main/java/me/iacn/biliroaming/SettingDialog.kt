@file:Suppress("DEPRECATION")

package me.iacn.biliroaming

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Activity.RESULT_CANCELED
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.*
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
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
        private var customSubtitleDialog: CustomSubtitleDialog? = null
        private val searchItems = mutableListOf<SearchItem>()
        private lateinit var searchPopupWindow: ListPopupWindow
        private lateinit var searchAdapter: SearchResultAdapter
        private var searchJob: Job? = null

        @Deprecated("Deprecated in Java")
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
            findPreference("customize_accessKey")?.onPreferenceClickListener = this
            findPreference("share_log")?.onPreferenceClickListener = this
            findPreference("customize_drawer")?.onPreferenceClickListener = this
            findPreference("custom_link")?.onPreferenceClickListener = this
            findPreference("add_custom_button")?.onPreferenceChangeListener = this
            findPreference("customize_dynamic")?.onPreferenceClickListener = this
            checkCompatibleVersion()
            loadSearchItems(preferenceScreen)
            checkUpdate()
        }

        @Deprecated("Deprecated in Java")
        override fun onDestroy() {
            super.onDestroy()
            scope.cancel()
        }

        @Deprecated("Deprecated in Java")
        override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            initSearchPopupWindow()
        }

        private fun initSearchPopupWindow() {
            searchAdapter = SearchResultAdapter(context)
            val listView = view?.findViewById<ListView>(android.R.id.list) ?: return
            val searchView = context.inflateLayout(R.layout.search)
            listView.addHeaderView(searchView)
            val editView = searchView.findViewById<EditText>(R.id.search)
            val clearView = searchView.findViewById<View>(R.id.clear)
            searchView.setOnClickListener {
                editView.requestFocus()
                context.getSystemService(InputMethodManager::class.java)
                    ?.showSoftInput(editView, 0)
            }
            editView.addTextChangedListener(object : TextWatcher {
                override fun onTextChanged(
                    s: CharSequence?, start: Int, before: Int, count: Int
                ) = onSearchTextChanged(s?.toString().orEmpty())

                override fun afterTextChanged(s: Editable?) {}
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int
                ) {
                }
            })
            clearView.setOnClickListener {
                editView.setText("")
            }
            searchPopupWindow = object : ListPopupWindow(context, null) {
                init {
                    isModal = true
                    val displayWidth = context.getSystemService(WindowManager::class.java)?.let {
                        Point().apply { it.defaultDisplay.getSize(this) }.x
                    } ?: 0
                    width = if (displayWidth != 0) {
                        displayWidth - 36.dp
                    } else ViewGroup.LayoutParams.MATCH_PARENT
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    setAdapter(searchAdapter)
                    anchorView = searchView
                    setOnItemClickListener { parent, _, position, _ ->
                        dismiss()
                        val item = parent.getItemAtPosition(position) as? SearchItem
                            ?: return@setOnItemClickListener
                        scope.launch {
                            // wait popup to be dismissed
                            delay(200)
                            selectPreference(item)
                        }
                    }
                    inputMethodMode = INPUT_METHOD_NOT_NEEDED
                    softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                }

                override fun show() {
                    super.show()
                    val ta = context.obtainStyledAttributes(intArrayOf(android.R.attr.listDivider))
                    getListView()?.divider = ta.getDrawable(0)
                    ta.recycle()
                }
            }
        }

        private var position = 1
        private fun loadSearchItems(preferenceGroup: PreferenceGroup) {
            for (i in 0 until preferenceGroup.preferenceCount) {
                val preference = preferenceGroup.getPreference(i)
                val entries = when (preference) {
                    is ListPreference -> preference.entries
                    is MultiSelectListPreference -> preference.entries
                    else -> arrayOf()
                }.orEmpty().toList()
                val searchItem = SearchItem(
                    preference.key.orEmpty(),
                    preference.title?.toString().orEmpty(),
                    preference.summary?.toString().orEmpty(),
                    entries,
                    position++,
                    isGroup = preference is PreferenceGroup,
                )
                searchItem.appendExtraKeywords()
                searchItems.add(searchItem)
                if (preference is PreferenceGroup) {
                    loadSearchItems(preference)
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

            else -> false
        }

        private fun SearchItem.similarWith(text: String): Boolean {
            return text.isNotEmpty() && ((title.isNotEmpty() && text in title)
                    || (summary.isNotEmpty() && text in summary)
                    || (entries.isNotEmpty() && entries.any { text in it })
                    || (extra.isNotEmpty() && extra.any { text in it }))
        }

        private fun onSearchTextChanged(text: String) {
            searchJob?.cancel(null)
            searchJob = null
            val listView = view?.findViewById<ListView>(android.R.id.list) ?: return
            if (text.isEmpty()) {
                // stop scrolling
                listView.smoothScrollBy(0, 0)
                listView.setSelection(0)
                searchPopupWindow.dismiss()
                return
            }
            searchJob = scope.launch {
                // wait a while, in case user are clearing
                delay(100)
                if (!isActive) return@launch
                val results = searchItems.filter { it.similarWith(text) }
                searchAdapter.clear()
                searchAdapter.addAll(results)
                if (results.isEmpty()) {
                    searchPopupWindow.dismiss()
                } else {
                    if (!isActive) return@launch
                    val searchView = listView.findViewById<EditText>(R.id.search)
                    context.getSystemService(InputMethodManager::class.java)
                        ?.takeIf { it.isActive }
                        ?.hideSoftInputFromWindow(searchView.windowToken, 0)
                    if (!isActive) return@launch
                    // wait keyboard to be hidden
                    delay(200)
                    if (!isActive) return@launch
                    searchPopupWindow.show()
                }
            }
        }

        private fun selectPreference(searchItem: SearchItem) {
            val listView = view?.findViewById<ListView>(android.R.id.list) ?: return
            // stop scrolling
            listView.smoothScrollBy(0, 0)
            listView.setSelection(searchItem.position)
            listView.post {
                val view = listView.getViewByPosition(searchItem.position)
                val origBg = view.background
                view.setRippleBackground()
                val background = view.background
                scope.launch {
                    // wait selection
                    delay(100)
                    repeat(3) {
                        val state = intArrayOf(
                            android.R.attr.state_pressed,
                            android.R.attr.state_enabled
                        )
                        background.setState(state)
                        delay(300)
                        background.setState(intArrayOf())
                        // wait effect disappear
                        delay(200)
                    }
                }.invokeOnCompletion {
                    // restore original background
                    view.background = origBg
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
                    findPreference("version").summary = "${versionName}（最新版$newestVer）"
                    (findPreference("about") as PreferenceCategory).addPreference(
                        Preference(activity).apply {
                            key = "update"
                            title = context.getString(R.string.update_title)
                            summary =
                                result.optString("body").substringAfterLast("更新日志\r\n").run {
                                    ifEmpty { context.getString(R.string.update_summary) }
                                }
                            onPreferenceClickListener = this@PrefsFragment
                            order = 1
                        })
                    position = 1
                    searchItems.clear()
                    loadSearchItems(preferenceScreen)
                }
            }
        }

        private fun checkCompatibleVersion() {
            val versionCode = getVersionCode(packageName)
            var supportMusicNotificationHook = true
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
            when (platform) {
                "android_hd" -> {
                    supportCustomizeTab = false
                    supportDrawer = false
                    supportDrawerStyle = false
                    supportAddTag = false
                }
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
                supportMusicNotificationHook = false
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
                disablePreference(
                    "music_notification",
                    context.getString(R.string.os_not_support)
                )
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
                                file.outputStream().use { output ->
                                    activity.contentResolver.openInputStream(uri)
                                        ?.use { it.copyTo(output) }
                                }
                            } catch (e: Exception) {
                                Log.toast(e.message ?: "未知错误", true, alsoLog = true)
                            }
                            Log.toast("请至少重新打开哔哩漫游设置", true)
                        }

                        PREF_EXPORT -> {
                            try {
                                file.inputStream().use { input ->
                                    activity.contentResolver.openOutputStream(uri)
                                        ?.use { input.copyTo(it) }
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
                val view = context.inflateLayout(R.layout.danmaku_filter_dialog)
                val seekBar = view.findViewById<SeekBar>(R.id.seekBar)
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

        private fun onTestUposClick(): Boolean {
            SpeedTestDialog(findPreference("upos_host") as ListPreference, activity).show()
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
            else -> false
        }
    }

    init {
        val activity = context as Activity
        activity.addModuleAssets()
        val prefsFragment = PrefsFragment()
        activity.fragmentManager.beginTransaction().add(prefsFragment, "Setting").commit()
        activity.fragmentManager.executePendingTransactions()

        prefsFragment.onActivityCreated(null)

        val unhook =
            Preference::class.java.hookAfterMethod("onCreateView", ViewGroup::class.java) { param ->
                if (PreferenceCategory::class.java.isInstance(param.thisObject)
                    && TextView::class.java.isInstance(param.result)
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
        setNeutralButton("回到顶部", null)
        setPositiveButton("确定并重启客户端") { _, _ ->
            unhook?.unhook()
            prefsFragment.preferenceManager.forceSavePreference()
            restartApplication(activity)
        }
    }

    data class SearchItem(
        val key: String = "",
        val title: String = "",
        val summary: String = "",
        val entries: List<CharSequence> = listOf(),
        val position: Int = 0,
        val isGroup: Boolean = false,
        val extra: MutableList<String> = mutableListOf(),
    )

    class SearchResultAdapter(context: Context) : ArrayAdapter<SearchItem>(context, 0) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return (convertView ?: context.inflateLayout(R.layout.search_result_item)).apply {
                getItem(position).let {
                    findViewById<TextView>(R.id.title).text = it?.title
                    findViewById<TextView>(R.id.summary).text = it?.summary
                }
            }
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
            SettingDialog(context).create().apply {
                setOnShowListener {
                    window?.clearFlags(
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                    )
                    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                    getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                        window?.findViewById<ListView>(android.R.id.list)?.run {
                            // stop scrolling
                            smoothScrollBy(0, 0)
                            setSelection(0)
                        }
                    }
                }
            }.show()
        }

        const val SPLASH_SELECTION = 0
        const val LOGO_SELECTION = 1
        const val PREF_IMPORT = 2
        const val PREF_EXPORT = 3
        const val VIDEO_EXPORT = 4
    }
}
