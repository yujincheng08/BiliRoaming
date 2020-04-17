package me.iacn.biliroaming.utils

import de.robv.android.xposed.XposedBridge
import me.iacn.biliroaming.Constant.TAG
import java.math.BigInteger
import android.util.Log as ALog


object Log {
    @JvmStatic
    fun d(obj: Any?) {
        ALog.d(TAG, "$obj")
        xLog(obj)
    }

    @JvmStatic
    fun i(obj: Any?) {
        ALog.i(TAG, "$obj")
        xLog(obj)
    }

    @JvmStatic
    fun e(obj: Any?) {
        ALog.e(TAG, "$obj")
        xLog(obj)
    }

    @JvmStatic
    fun v(obj: Any?) {
        ALog.v(TAG, "$obj")
        xLog(obj)
    }

    @JvmStatic
    fun w(obj: Any?) {
        ALog.w(TAG, "$obj")
        xLog(obj)
    }

    @JvmStatic
    fun xLog(obj: Any?) {
        if (obj is Throwable) {
            XposedBridge.log("$TAG: $obj")
        } else {
            XposedBridge.log("$TAG: $obj")
        }
    }

}

fun bv2av(bv: String): Long {
    val table = HashMap<Char, Int>()
    "fZodR9XQDSUm21yCkr6zBqiveYah8bt4xsWpHnJE7jL5VG3guMTKNPAwcF".forEachIndexed { i, b -> table[b] = i }
    val r = intArrayOf(11, 10, 3, 8, 4, 6).withIndex().map { (i, p) ->
        table[bv[p]]!! * BigInteger.valueOf(58).pow(i).toLong()
    }.sum()
    return (r - 8728348608).xor(177451812)
}