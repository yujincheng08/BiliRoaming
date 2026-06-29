package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.hook.kotlinx.KotlinxHomeTabProcessor
import me.iacn.biliroaming.hook.kotlinx.KotlinxProcessor
import me.iacn.biliroaming.hook.kotlinx.KotlinxSplashListProcessor
import me.iacn.biliroaming.hook.kotlinx.KotlinxSplashShowProcessor
import me.iacn.biliroaming.utils.*

/**
 * hook kotlinx.serialization 路径（ktor + kotlinx）。
 * 新版底栏（HomeTabResponse）由 KotlinxHomeTabProcessor 处理；
 * 旧版底栏（FastJSON，默认路径）由 JsonHook.earlyHook 处理。也处理 splash 数据。
 */
class KotlinxJsonHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val allProcessors = listOf(
        KotlinxHomeTabProcessor(),
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

        val desc = deserializer.callMethodOrNull("getDescriptor") ?: return
        val serialName = desc.callMethodOrNull("getSerialName") as? String ?: return

        enabledProcessors[serialName]?.forEach { processor ->
            try {
                processor.process(result, deserializer)
            } catch (e: Throwable) {
                Log.e("KotlinxJsonHook processor ${processor.targetSerialName} error: $e")
            }
        }
    }
}
