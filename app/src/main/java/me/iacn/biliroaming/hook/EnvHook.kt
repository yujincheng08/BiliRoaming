package me.iacn.biliroaming.hook

import android.content.SharedPreferences
import me.iacn.biliroaming.Protos
import me.iacn.biliroaming.utils.*
import java.util.regex.Pattern

class EnvHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        Log.d("startHook: Env")
        "com.bilibili.lib.blconfig.internal.EnvContext\$preBuiltConfig\$2".hookAfterMethod(
            mClassLoader,
            "invoke"
        ) { param ->
            @Suppress("UNCHECKED_CAST")
            val result = param.result as MutableMap<String, String?>
            for (config in configSet) {
                (if (sPrefs.getBoolean(
                        config.config,
                        false
                    )
                ) config.trueValue else config.falseValue)
                    ?.let { result[config.key] = it } ?: result.remove(config.key)
            }
        }
        "com.bilibili.lib.blconfig.internal.TypedContext\$dataSp\$2".hookAfterMethod(
            mClassLoader,
            "invoke"
        ) { param ->
            val result = param.result as SharedPreferences
            // this indicates the proper instance
            if (!result.contains("bv.enable_bv")) return@hookAfterMethod
            for (config in configSet) {
                (if (sPrefs.getBoolean(
                        config.config,
                        false
                    )
                ) config.trueValue else config.falseValue)
                    ?.let { result.edit().putString(config.key, it).apply() }
                    ?: result.edit().remove(config.key).apply()
            }
        }

        if (sPrefs.getBoolean("add_4k", false)) {
            "com.bilibili.lib.moss.internal.impl.common.header.HeadersKt\$reqDevice\$2".hookAfterMethod(
                mClassLoader,
                "invoke"
            ) { param ->
                param.result = Protos.Device.newBuilder().run {
                    mergeFrom(Protos.Device.parseFrom(param.result as ByteArray))
                    mobiApp = "android"
                    if (build < 6000000)
                        build = 6000000
                    build()
                }.toByteArray()
            }
        }
//        // Disable tinker
//        "com.tencent.tinker.loader.app.TinkerApplication".findClass(mClassLoader)?.hookBeforeAllConstructors { param ->
//            param.args[0] = 0
//        }
    }

    override fun lateInitHook() {
        Log.d("lateHook: Env")
        if (sPrefs.getBoolean("enable_av", false)) {
            val compatClass = "com.bilibili.droid.BVCompat".findClassOrNull(mClassLoader)
            compatClass?.declaredFields?.forEach {
                val field = compatClass.getStaticObjectField(it.name)
                if (field is Pattern && field.pattern() == "av[1-9]\\d*")
                    compatClass.setStaticObjectField(
                        it.name,
                        Pattern.compile("(av[1-9]\\d*)|(BV1[1-9A-NP-Za-km-z]{9})", field.flags())
                    )
            }
        }
    }

    companion object {

        private val encryptedValueMap = hashMapOf(
            "0" to "Irb5O7Q8Ka0ojD4qqScgqg==",
            "1" to "Y260Cyvp6HZEboaGO+YGMw=="
        )

        class ConfigTuple(
            val key: String,
            val config: String,
            val trueValue: String?,
            val falseValue: String?
        )

        val configSet = listOf(
            ConfigTuple(
                "bv.enable_bv",
                "enable_av",
                encryptedValueMap["0"],
                encryptedValueMap["1"]
            ),
        )
    }
}
