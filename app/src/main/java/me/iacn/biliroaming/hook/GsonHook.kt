package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.hook.gson.GsonProcessor
import me.iacn.biliroaming.hook.gson.SplashListProcessor
import me.iacn.biliroaming.hook.gson.SplashShowProcessor
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.getObjectField
import me.iacn.biliroaming.utils.hookAfterAllMethods

class GsonHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val allProcessors = listOf(
        SplashListProcessor(),
        SplashShowProcessor(),
    )

    private val enabledProcessors: Map<String, List<GsonProcessor>> by lazy {
        allProcessors
            .filter { it.shouldEnable() }
            .groupBy { it.targetClassName }
    }

    override fun startHook() {
        if (enabledProcessors.isEmpty()) return
        Log.d("startHook: Gson")

        val gsonClass = instance.gsonClass ?: return
        val fromJsonName = instance.gsonFromJson() ?: return

        gsonClass.hookAfterAllMethods(fromJsonName) { param ->
            dispatchResult(param.result)
        }
    }

    private fun dispatchResult(result: Any?) {
        if (result == null) return

        // Network path: unwrap GeneralResponse<T> -> T
        var target = result
        if (result.javaClass == instance.generalResponseClass) {
            target = result.getObjectField("data") ?: return
        }

        enabledProcessors[target.javaClass.name]?.forEach { processor ->
            try {
                processor.process(target)
            } catch (e: Throwable) {
                Log.e("GsonHook processor ${processor.targetClassName} error: $e")
            }
        }
    }
}
