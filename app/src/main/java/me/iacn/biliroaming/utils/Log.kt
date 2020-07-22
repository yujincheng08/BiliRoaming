@file:Suppress("unused")

package me.iacn.biliroaming.utils

import de.robv.android.xposed.XposedBridge
import me.iacn.biliroaming.Constant.TAG
import java.math.BigInteger
import kotlin.reflect.KFunction2
import android.util.Log as ALog

fun Throwable.dump(): String {
    val sb = StringBuilder()
    sb.appendln(toString())
    for (s in stackTrace)
        sb.appendln(s)
    return sb.toString()
}

object Log {

    @JvmStatic
    private fun doLog(f: KFunction2<String, String, Int>, obj: Any?, toXposed: Boolean = false) {
        val str = if (obj is Throwable) obj.dump() else obj.toString()

        if (str.length > maxLength) {
            val chunkCount: Int = str.length / maxLength
            for (i in 0..chunkCount) {
                val max: Int = 4000 * (i + 1)
                if (max >= str.length) {
                    doLog(f, str.substring(maxLength * i))
                } else {
                    doLog(f, str.substring(maxLength * i, max))
                }
            }
        } else {
            f(TAG, str)
            if(toXposed)
                XposedBridge.log("$TAG : $str")
        }
    }

    @JvmStatic
    fun d(obj: Any?) {
        doLog(ALog::d, obj)
    }

    @JvmStatic
    fun i(obj: Any?) {
        doLog(ALog::i, obj)
    }

    @JvmStatic
    fun e(obj: Any?) {
        doLog(ALog::e, obj, true)
    }

    @JvmStatic
    fun v(obj: Any?) {
        doLog(ALog::v, obj)
    }

    @JvmStatic
    fun w(obj: Any?) {
        doLog(ALog::w, obj)
    }

    private const val maxLength = 4000
}

fun bv2av(bv: String): Long {
    val table = HashMap<Char, Int>()
    "fZodR9XQDSUm21yCkr6zBqiveYah8bt4xsWpHnJE7jL5VG3guMTKNPAwcF".forEachIndexed { i, b -> table[b] = i }
    val r = intArrayOf(11, 10, 3, 8, 4, 6).withIndex().map { (i, p) ->
        table[bv[p]]!! * BigInteger.valueOf(58).pow(i).toLong()
    }.sum()
    return (r - 8728348608).xor(177451812)
}
