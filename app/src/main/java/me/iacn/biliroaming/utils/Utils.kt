package me.iacn.biliroaming.utils

import android.content.Context
import java.math.BigInteger

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

fun getPackageVersion(packageName: String): String? {
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