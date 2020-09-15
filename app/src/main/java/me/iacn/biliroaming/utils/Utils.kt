package me.iacn.biliroaming.utils

import android.app.AndroidAppHelper
import android.content.Context
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import java.math.BigInteger
import java.util.*
import kotlin.collections.HashMap

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

fun getPackageVersion(packageName: String): String {
    return try {
        systemContext.packageManager.getPackageInfo(packageName, 0).run {
            @Suppress("DEPRECATION")
            String.format("${packageName}@%s(%d)", versionName, versionCode)
        }
    } catch (e: Throwable) {
        Log.e(e)
        "(unknown)"
    }
}

fun getVersionCode(packageName: String): String? {
    return try {
        systemContext.packageManager.getPackageInfo(packageName, 0).run {
            @Suppress("DEPRECATION")
            versionCode.toString()
        }
    } catch (e: Throwable) {
        Log.e(e)
        null
    }
}

val appKey = mapOf(
        "tv.danmaku.bili" to "1d8b6e7d45233436",
        "com.bilibili.app.blue" to "07da50c9a0bf829f",
        "com.bilibili.app.in" to "bb3101000e232e27",
)

val platform = mapOf(
        "tv.danmaku.bili" to "android",
        "com.bilibili.app.blue" to "android_b",
        "com.bilibili.app.in" to "android_i",
)

fun signQuery(query: String?): String? {
    val queryMap = TreeMap<String, String>()
    val pairs = query?.split("&".toRegex())?.toTypedArray() ?: return null
    for (pair in pairs) {
        val idx = pair.indexOf("=")
        queryMap[pair.substring(0, idx)] = pair.substring(idx + 1)
    }
    val packageName = AndroidAppHelper.currentPackageName()
    queryMap["appkey"] = appKey[packageName] ?: "1d8b6e7d45233436"
    queryMap["build"] = getVersionCode(packageName) ?: "6080000"
    queryMap["device"] = "android"
    queryMap["mobi_app"] = platform[packageName] ?: "android"
    queryMap["platform"] = platform[packageName] ?: "android"
    return instance.libBiliClass?.callStaticMethod(instance.signQueryName(), queryMap).toString()
}