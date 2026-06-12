package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.hook.kotlinx.KotlinxProcessor
import me.iacn.biliroaming.hook.kotlinx.KotlinxSplashListProcessor
import me.iacn.biliroaming.hook.kotlinx.KotlinxSplashShowProcessor
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.callMethodOrNull
import me.iacn.biliroaming.utils.hookAllMethods

class KotlinxJsonHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val allProcessors = listOf(
        KotlinxSplashListProcessor(),
        KotlinxSplashShowProcessor(),
    )

    private val enabledProcessors: Map<String, List<KotlinxProcessor>> by lazy {
        allProcessors
            .filter { it.shouldEnable() }
            .groupBy { it.targetSerialName }
    }

    override fun startHook() {
        if (enabledProcessors.isEmpty()) return
        Log.d("startHook: KotlinxJson")

        val jsonClass = instance.kotlinJsonClass ?: return

        jsonClass.hookAllMethods("decodeFromString") { chain ->
            val result = chain.proceed()
            dispatchResult(chain.args.getOrNull(0), result)
            result
        }
    }

    private fun dispatchResult(deserializer: Any?, result: Any?) {
        if (deserializer == null || result == null) return

        val serialName = deserializer
            .callMethodOrNull("getDescriptor")
            ?.callMethodOrNull("getSerialName")
            ?: return

        enabledProcessors[serialName]?.forEach { processor ->
            try {
                processor.process(result)
            } catch (e: Throwable) {
                Log.e("KotlinxJsonHook processor ${processor.targetSerialName} error: $e")
            }
        }
    }
}
