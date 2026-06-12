package me.iacn.biliroaming.hook.kotlinx

import me.iacn.biliroaming.utils.sPrefs

class KotlinxSplashShowProcessor : KotlinxProcessor {
    override val targetSerialName =
        "kntr.srcs.app.splash.model.SplashShowStrategy"

    override fun shouldEnable() =
        sPrefs.getBoolean("hidden", false) && sPrefs.getBoolean("purify_splash", false)

    override fun process(result: Any) {
        try {
            result.javaClass.declaredFields
                .filter { !it.type.isPrimitive }
                .filter { it.type != String::class.java
                    && it.type.name != "kotlinx.serialization.json.JsonObject" }
                .forEach { field ->
                    field.isAccessible = true
                    field.set(result, null)
                }
        } catch (_: Throwable) {}
    }
}
