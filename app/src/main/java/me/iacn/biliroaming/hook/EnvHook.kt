package me.iacn.biliroaming.hook

import android.content.SharedPreferences
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookAfterMethod

class EnvHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        Log.d("startHook: Env")
        "com.bilibili.lib.blconfig.internal.EnvContext\$preBuiltConfig\$2".hookAfterMethod(mClassLoader, "invoke") { param ->
            @Suppress("UNCHECKED_CAST")
            val result = param.result as MutableMap<String, String?>
            for (config in configSet) {
                (if (XposedInit.sPrefs.getBoolean(config.config, false)) config.trueValue else config.falseValue)
                        ?.let { result[config.key] = it } ?: result.remove(config.key)
            }
        }
        "com.bilibili.lib.blconfig.internal.TypedContext\$dataSp\$2".hookAfterMethod(mClassLoader, "invoke") { param ->
            val result = param.result as SharedPreferences
            // this indicates the proper instance
            if (!result.contains("bv.enable_bv")) return@hookAfterMethod
            for (config in configSet) {
                (if (XposedInit.sPrefs.getBoolean(config.config, false)) config.trueValue else config.falseValue)
                        ?.let { result.edit().putString(config.key, it).apply() }
                        ?: result.edit().remove(config.key).apply()
            }
        }
    }

    companion object {
        private val encryptedValueMap = hashMapOf(
                Pair("0", "Irb5O7Q8Ka0ojD4qqScgqg=="),
                Pair("1", "Y260Cyvp6HZEboaGO+YGMw==")
        )

        class ConfigTuple(val key: String, val config: String, val trueValue: String?, val falseValue: String?)

        val configSet = listOf(
                ConfigTuple("bv.enable_bv", "enable_av", encryptedValueMap["0"], encryptedValueMap["1"]),
                ConfigTuple("comment.rpc_enable", "comment_floor", encryptedValueMap["0"], null)
        )
    }
}