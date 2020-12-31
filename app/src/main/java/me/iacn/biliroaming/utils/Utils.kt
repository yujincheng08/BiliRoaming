package me.iacn.biliroaming.utils

import android.app.AndroidAppHelper
import android.content.Context
import android.content.pm.PackageManager.GET_META_DATA
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import kotlin.collections.HashMap
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
        val activityThread = "android.app.ActivityThread".findClassOrNull(null)?.callStaticMethod("currentActivityThread")!!
        return activityThread.callMethodAs("getSystemContext")
    }

fun bv2av(bv: String): Long {
    val table = HashMap<Char, Int>()
    "fZodR9XQDSUm21yCkr6zBqiveYah8bt4xsWpHnJE7jL5VG3guMTKNPAwcF".forEachIndexed { i, b -> table[b] = i }
    val r = intArrayOf(11, 10, 3, 8, 4, 6).withIndex().map { (i, p) ->
        table[bv[p]]!! * BigInteger.valueOf(58).pow(i).toLong()
    }.sum()
    return (r - 8728348608).xor(177451812)
}

fun getPackageVersion(packageName: String) = try {
    systemContext.packageManager.getPackageInfo(packageName, 0).run {
        @Suppress("DEPRECATION")
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
)

val currentContext by lazy { AndroidAppHelper.currentApplication() as Context }

val packageName: String by lazy { currentContext.packageName }

val isBuiltIn
    get() = XposedInit.modulePath.endsWith("so")

val is64
    get() = currentContext.applicationInfo.nativeLibraryDir.contains("64")

val platform by lazy {
    currentContext.packageManager.getApplicationInfo(packageName, GET_META_DATA).metaData.getString("MOBI_APP")
            ?: when(packageName) {
                Constant.BLUE_PACKAGE_NAME -> "android_b"
                Constant.PLAY_PACKAGE_NAME -> "android_i"
                else -> "android"
            }
}

val logFile by lazy { File(currentContext.externalCacheDir, "log.txt") }

@Suppress("DEPRECATION")
val sPrefs
    get() = currentContext.getSharedPreferences("biliroaming", Context.MODE_MULTI_PROCESS)!!

fun signQuery(query: String?, Thailand:Boolean = false): String? {
    val queryMap = TreeMap<String, String>()
    val pairs = query?.split("&".toRegex())?.toTypedArray() ?: return null
    for (pair in pairs) {
        val idx = pair.indexOf("=")
        val key = pair.substring(0, idx)
        if (key !in arrayOf("t", "sign"))
            queryMap[key] = pair.substring(idx + 1)
    }
    val packageName = AndroidAppHelper.currentPackageName()
    if (Thailand) {
        queryMap.remove("sign")
        queryMap["appkey"] = "7d089525d3611b1c"
        queryMap["build"] = "1001310"
        queryMap["mobi_app"] = "bstar_a"
        queryMap["platform"] = "android"
    } else {
        queryMap["appkey"] = appKey[packageName] ?: "1d8b6e7d45233436"
        queryMap["build"] = getVersionCode(packageName).toString()
        queryMap["device"] = "android"
        queryMap["mobi_app"] = platform
        queryMap["platform"] = platform
    }
    return instance.libBiliClass?.callStaticMethod(instance.signQueryName(), queryMap).toString()
}

fun getId(name: String) = instance.ids[name]
        ?: currentContext.resources.getIdentifier(name, "id", currentContext.packageName)

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

operator fun JSONArray.iterator(): Iterator<JSONObject> = (0 until length()).asSequence().map { get(it) as JSONObject }.iterator()

fun JSONArray?.orEmpty() = this ?: JSONArray()

fun getStreamContent(input: InputStream) = try {
    input.bufferedReader().use {
        it.readText()
    }
} catch (e: Throwable) {
    Log.e(e)
    null
}