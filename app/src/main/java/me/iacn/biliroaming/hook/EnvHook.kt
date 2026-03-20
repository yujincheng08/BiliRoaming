package me.iacn.biliroaming.hook

import android.content.SharedPreferences
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import java.lang.reflect.Proxy
import java.util.regex.Pattern

class EnvHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        Log.d("startHook: Env")

        // EnvContext
        instance.preBuiltConfigClass?.let {
            val hooker: HookCallback = hooker@{ chain ->
                val result = chain.proceed()
                @Suppress("UNCHECKED_CAST")
                val resultMap = result as MutableMap<String, String?>
                for (config in configSet) {
                    (if (sPrefs.getBoolean(
                            config.config,
                            false
                        )
                    ) config.trueValue else config.falseValue)
                        ?.let { resultMap[config.key] = it } ?: resultMap.remove(config.key)
                }
                result
            }
            // v8.28.0 - ?
            it.hookMethod(instance.getPreBuiltConfigMethod(), callback = hooker)
            // ? - v8.48.0 ..
            it.hookMethod(instance.getPreBuiltConfigMethod(), it, callback = hooker)
        }

        // TypedContext
        instance.dataSPClass?.let {
            val hooker: HookCallback = hooker@{ chain ->
                val result = chain.proceed() as SharedPreferences
                // this indicates the proper instance
                if (!result.contains("bv.enable_bv")) return@hooker result
                for (config in configSet) {
                    (if (sPrefs.getBoolean(
                            config.config,
                            false
                        )
                    ) config.trueValue else config.falseValue)
                        ?.let { result.edit().putString(config.key, it).apply() }
                        ?: result.edit().remove(config.key).apply()
                }
                result
            }
            // v8.28.0 - ?
            it.hookMethod(instance.getDataSPMethod(), callback = hooker)
            // ? - v8.48.0 ..
            it.hookMethod(instance.getDataSPMethod(), it, callback = hooker)
        }

        "com.bilibili.lib.blconfig.internal.OverrideConfig".findClassOrNull(mClassLoader)
            ?.hookAllConstructors { chain ->
                val delegate = chain.args.getOrNull(0) ?: return@hookAllConstructors chain.proceed()
                val realConfig = chain.args.getOrNull(1) ?: return@hookAllConstructors chain.proceed()
                val delegateClass = delegate.javaClass
                val args = chain.args.toTypedArray()
                args[0] = Proxy.newProxyInstance(
                    delegateClass.classLoader,
                    delegateClass.interfaces
                ) { _, m, a ->
                    val proxyArgs = a ?: emptyArray()
                    if (m.name == "getConfig") {
                        var result: Any? = null
                        val key = proxyArgs[0]
                        for (config in configSet) {
                            if (sPrefs.getBoolean(config.config, false) && config.key == key) {
                                result = realConfig.callMethodOrNull("get", *proxyArgs)
                            }
                        }
                        result ?: m(delegate, *proxyArgs)
                    } else {
                        m(delegate, *proxyArgs)
                    }
                }
                chain.proceed(args)
            }

//        // Disable tinker
//        "com.tencent.tinker.loader.app.TinkerApplication".findClass(mClassLoader)?.hookAllConstructors { chain ->
//            val args = chain.args.toTypedArray()
//            args[0] = 0
//            chain.proceed(args)
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
