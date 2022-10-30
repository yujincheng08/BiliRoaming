package me.iacn.biliroaming.utils

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.content.Context
import android.content.pm.PackageManager.GET_META_DATA
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.TypedValue
import android.view.*
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import com.google.protobuf.GeneratedMessageLite
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.Constant
import me.iacn.biliroaming.XposedInit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.lang.ref.WeakReference
import java.math.BigInteger
import java.net.URL
import java.util.*
import kotlin.math.roundToInt
import kotlin.reflect.KProperty

class Weak(val initializer: () -> Class<*>?) {
    private var weakReference: WeakReference<Class<*>?>? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>) = weakReference?.get() ?: let {
        weakReference = WeakReference(initializer())
        weakReference
    }?.get()

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Class<*>) {
        weakReference = WeakReference(value)
    }
}

val systemContext: Context
    get() {
        val activityThread = "android.app.ActivityThread".findClassOrNull(null)
            ?.callStaticMethod("currentActivityThread")!!
        return activityThread.callMethodAs("getSystemContext")
    }

fun bv2av(bv: String): Long {
    val table = HashMap<Char, Int>()
    "fZodR9XQDSUm21yCkr6zBqiveYah8bt4xsWpHnJE7jL5VG3guMTKNPAwcF".forEachIndexed { i, b ->
        table[b] = i
    }
    val r = intArrayOf(11, 10, 3, 8, 4, 6).withIndex().sumOf { (i, p) ->
        table[bv[p]]!! * BigInteger.valueOf(58).pow(i).toLong()
    }
    return (r - 8728348608).xor(177451812)
}

fun getPackageVersion(packageName: String) = try {
    @Suppress("DEPRECATION")
    systemContext.packageManager.getPackageInfo(packageName, 0).run {
        String.format("${packageName}@%s(%s)", versionName, getVersionCode(packageName))
    }
} catch (e: Throwable) {
    Log.e(e)
    "(unknown)"
}


fun getVersionCode(packageName: String) = try {
    @Suppress("DEPRECATION")
    systemContext.packageManager.getPackageInfo(packageName, 0).versionCode
} catch (e: Throwable) {
    Log.e(e)
    null
} ?: 6080000


val appKey = mapOf(
    "tv.danmaku.bili" to "1d8b6e7d45233436",
    "com.bilibili.app.blue" to "07da50c9a0bf829f",
    "com.bilibili.app.in" to "bb3101000e232e27",
    "tv.danmaku.bilibilihd" to "dfca71928277209b",
)

val currentContext by lazy { AndroidAppHelper.currentApplication() as Context }

val packageName: String by lazy { currentContext.packageName }

val isBuiltIn
    get() = XposedInit.modulePath.endsWith("so")

val is64
    get() = currentContext.applicationInfo.nativeLibraryDir.contains("64")

val platform by lazy {
    @Suppress("DEPRECATION")
    currentContext.packageManager.getApplicationInfo(packageName, GET_META_DATA).metaData.getString(
        "MOBI_APP"
    )
        ?: when (packageName) {
            Constant.BLUE_PACKAGE_NAME -> "android_b"
            Constant.PLAY_PACKAGE_NAME -> "android_i"
            Constant.HD_PACKAGE_NAME -> "android_hd"
            else -> "android"
        }
}

val logFile by lazy { File(currentContext.externalCacheDir, "log.txt") }

val oldLogFile by lazy { File(currentContext.externalCacheDir, "old_log.txt") }

@Suppress("DEPRECATION")
val sPrefs
    get() = currentContext.getSharedPreferences("biliroaming", Context.MODE_MULTI_PROCESS)!!

@Suppress("DEPRECATION")
val sCaches
    get() = currentContext.getSharedPreferences("biliroaming_cache", Context.MODE_MULTI_PROCESS)!!

fun checkErrorToast(json: JSONObject, isCustomServer: Boolean = false) {
    if (json.optInt("code", 0) != 0) {
        Log.toast(
            (if (isCustomServer) "请求解析服务器发生错误: " else "请求发生错误: ") + json.optString(
                "message",
                "未知错误"
            )
        )
    }
}

fun signQuery(query: String?, extraMap: Map<String, String> = emptyMap()): String? {
    val queryMap = TreeMap<String, String>()
    val pairs = query?.split("&".toRegex())?.toTypedArray() ?: return null
    for (pair in pairs) {
        val idx = pair.indexOf("=")
        val key = pair.substring(0, idx)
        if (key !in arrayOf("t", "sign"))
            queryMap[key] = pair.substring(idx + 1)
    }
    val packageName = AndroidAppHelper.currentPackageName()
    queryMap["appkey"] = appKey[packageName] ?: "1d8b6e7d45233436"
    queryMap["build"] = getVersionCode(packageName).toString()
    queryMap["device"] = "android"
    queryMap["mobi_app"] = platform
    queryMap["platform"] = platform
    queryMap.putAll(extraMap)
    return instance.libBiliClass?.callStaticMethod(instance.signQueryName(), queryMap).toString()
}

@SuppressLint("DiscouragedApi")
fun getId(name: String) = instance.ids[name]
    ?: currentContext.resources.getIdentifier(name, "id", currentContext.packageName)

@SuppressLint("DiscouragedApi")
fun getResId(name: String, type: String) =
    currentContext.resources.getIdentifier(name, type, currentContext.packageName)

fun getBitmapFromURL(src: String?, callback: (Bitmap?) -> Unit) {
    Thread {
        callback(try {
            src?.let {
                val bytes = URL(it).readBytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: IOException) {
            Log.e(e)
            null
        })
    }.start()
}

fun String?.toJSONObject() = JSONObject(this.orEmpty())

@Suppress("UNCHECKED_CAST")
fun <T> JSONArray.asSequence() = (0 until length()).asSequence().map { get(it) as T }

operator fun JSONArray.iterator(): Iterator<JSONObject> =
    (0 until length()).asSequence().map { get(it) as JSONObject }.iterator()

fun JSONArray?.orEmpty() = this ?: JSONArray()

fun getStreamContent(input: InputStream) = try {
    input.bufferedReader().use {
        it.readText()
    }
} catch (e: Throwable) {
    Log.e(e)
    null
}

/**
 * @param targetDir 目标文件夹
 */
fun DocumentFile.copyTo(targetDir: DocumentFile) {
    val name = name ?: return
    if (isDirectory) {
        val chile = targetDir.findOrCreateDir(name) ?: return
        listFiles().forEach {
            it.copyTo(chile)
        }
    } else if (isFile) {
        val type = type ?: return
        val targetFile = targetDir.createFile(type, name) ?: return
        currentContext.contentResolver.openInputStream(uri)?.use {
            currentContext.contentResolver.openOutputStream(targetFile.uri)
                ?.use { o -> it.copyTo(o) }
        }
    }
}

fun DocumentFile.findOrCreateDir(displayName: String) = this.findFile(displayName)
    ?: this.createDirectory(displayName)

val shouldSaveLog
    get() = sPrefs.getBoolean("save_log", false) || sPrefs.getBoolean("show_hint", true)

fun GeneratedMessageLite<*, *>.print(indent: Int = 0): String {
    val sb = StringBuilder()
    for (f in javaClass.declaredFields) {
        if (f.name.startsWith("bitField")) continue
        if (f.isStatic) continue
        f.isAccessible = true
        val v = f.get(this)
        val name = StringBuffer().run {
            for (i in 0 until indent) append('\t')
            append(f.name.substringBeforeLast("_"), ": ")
            toString()
        }
        when (v) {
            is GeneratedMessageLite<*, *> -> {
                sb.appendLine(name)
                sb.append(v.print(indent + 1))
            }
            is List<*> -> {
                for (vv in v) {
                    sb.append(name)
                    when (vv) {
                        is GeneratedMessageLite<*, *> -> {
                            sb.appendLine()
                            sb.append(vv.print(indent + 1))
                        }
                        else -> {
                            sb.appendLine(vv?.toString() ?: "null")
                        }
                    }
                }
            }
            else -> {
                sb.append(name)
                sb.appendLine(v?.toString() ?: "null")
            }
        }
    }
    return sb.toString()
}

operator fun ViewGroup.iterator(): MutableIterator<View> = object : MutableIterator<View> {
    private var index = 0
    override fun hasNext() = index < childCount
    override fun next() = getChildAt(index++) ?: throw IndexOutOfBoundsException()
    override fun remove() = removeViewAt(--index)
}

val ViewGroup.children: Sequence<View>
    get() = object : Sequence<View> {
        override fun iterator() = this@children.iterator()
    }

fun View.addBackgroundRipple() = with(TypedValue()) {
    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, this, true)
    setBackgroundResource(resourceId)
}

@SuppressLint("ApplySharedPref")
fun migrateHomeFilterPrefsIfNeeded() {
    if (!sPrefs.getBoolean("home_filter_prefs_migrated", false)) {
        val titleList = sPrefs.getString("keywords_filter_title_recommend_list", null)
            ?.split('|')?.filter { it.trim().isNotEmpty() }?.toSet()
        val reasonList = sPrefs.getString("keywords_filter_reason_recommend_list", null)
            ?.split('|')?.filter { it.trim().isNotEmpty() }?.toSet()
        val uidList = sPrefs.getString("keywords_filter_uid_recommend_list", null)
            ?.split('|')?.filter { it.trim().isNotEmpty() }?.toSet()
        val upList = sPrefs.getString("keywords_filter_upname_recommend_list", null)
            ?.split('|')?.filter { it.trim().isNotEmpty() }?.toSet()
        val categoryList = sPrefs.getString("keywords_filter_rname_recommend_list", null)
            ?.split('|')?.filter { it.trim().isNotEmpty() }?.toSet()
        val channelList = sPrefs.getString("keywords_filter_tname_recommend_list", null)
            ?.split('|')?.filter { it.trim().isNotEmpty() }?.toSet()

        sPrefs.edit().apply {
            putStringSet("home_filter_keywords_title", titleList)
            putStringSet("home_filter_keywords_reason", reasonList)
            putStringSet("home_filter_keywords_uid", uidList)
            putStringSet("home_filter_keywords_up", upList)
            putStringSet("home_filter_keywords_category", categoryList)
            putStringSet("home_filter_keywords_channel", channelList)
            putBoolean("home_filter_prefs_migrated", true)
        }.commit()
    }
}

fun getRetrofitUrl(response: Any): String? {
    val requestField = instance.requestField() ?: return null
    val urlField = instance.urlField() ?: return null
    val request = response.getObjectField(requestField)
    return request?.getObjectField(urlField)?.toString()
}

fun Window.blurBackground() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    attributes.blurBehindRadius = 50
    setBackgroundBlurRadius(50)
    val blurEnableListener = { enable: Boolean ->
        setDimAmount(if (enable) 0.1F else 0.6F)
    }
    decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        @RequiresApi(Build.VERSION_CODES.S)
        override fun onViewAttachedToWindow(v: View) {
            windowManager.addCrossWindowBlurEnabledListener(blurEnableListener)
        }

        @RequiresApi(Build.VERSION_CODES.S)
        override fun onViewDetachedFromWindow(v: View) {
            windowManager.removeCrossWindowBlurEnabledListener(blurEnableListener)
        }

    })
    addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
}

val Int.sp: Int
    inline get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        toFloat(),
        currentContext.resources.displayMetrics
    ).roundToInt()

@Suppress("DEPRECATION")
val currentIsLandscape: Boolean
    get() = currentContext.getSystemService(WindowManager::class.java)
        .defaultDisplay.orientation.let { it == Surface.ROTATION_90 || it == Surface.ROTATION_270 }
