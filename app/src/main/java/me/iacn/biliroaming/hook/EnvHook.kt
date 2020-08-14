package me.iacn.biliroaming.hook

import android.content.SharedPreferences
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.*

class EnvHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        Log.d("startHook: Env")
        "com.bilibili.lib.blconfig.internal.TypedContext\$dataSp\$2".hookAfterMethod(mClassLoader, "invoke") { param ->
            val result = param.result as SharedPreferences
            // this indicates the proper instance
            if (!result.contains("bv.enable_bv")) return@hookAfterMethod
            if (XposedInit.sPrefs.getBoolean("enable_av", false)) {
                result.edit().putString("bv.enable_bv", encryptedValueMap["0"]).apply()
            } else {
                result.edit().putString("bv.enable_bv", encryptedValueMap["1"]).apply()
            }
            if (XposedInit.sPrefs.getBoolean("comment_floor", false)) {
                result.edit().putString("comment.rpc_enable", encryptedValueMap["0"]).apply()
            } else {
                result.edit().remove("comment.rpc_enable").apply()
            }
        }
    }

    companion object {
        val encryptedValueMap = hashMapOf(
                Pair("0", "Irb5O7Q8Ka0ojD4qqScgqg=="),
                Pair("1", "Y260Cyvp6HZEboaGO+YGMw==")
        )
    }
}