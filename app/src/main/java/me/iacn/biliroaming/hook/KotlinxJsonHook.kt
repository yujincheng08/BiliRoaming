package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.hook.kotlinx.KotlinxProcessor
import me.iacn.biliroaming.hook.kotlinx.KotlinxSplashListProcessor
import me.iacn.biliroaming.hook.kotlinx.KotlinxSplashShowProcessor
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.getObjectField
import me.iacn.biliroaming.utils.getObjectFieldAs
import me.iacn.biliroaming.utils.hookAfterAllMethods

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

        jsonClass.hookAfterAllMethods("decodeFromString") { param ->
            dispatchResult(param.args.getOrNull(0), param.result)
        }
    }

    private fun dispatchResult(deserializer: Any?, result: Any?) {
        if (deserializer == null || result == null) return

        val serialName = deserializer
            .getObjectField("descriptor")
            ?.getObjectFieldAs<String>("serialName")
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
