package me.iacn.biliroaming.utils

import de.robv.android.xposed.XposedBridge
import me.iacn.biliroaming.Constant.TAG
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