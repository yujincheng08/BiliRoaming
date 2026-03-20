package me.iacn.biliroaming.hook

import android.os.Bundle
import android.widget.TextView
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class RewardAdHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {

        if (!sPrefs.getBoolean("skip_reward_ad", false)) return

        Log.d("startHook: RewardAd")

        instance.rewardAdClass?.hookMethod("onCreate", Bundle::class.java) { chain ->
            val result = chain.proceed()
            chain.thisObject!!.setBooleanField(instance.rewardFlag(), true)
            (chain.thisObject!!.javaClass.declaredFields.firstOrNull {
                it.type == TextView::class.java
            }?.apply { isAccessible = true }?.get(chain.thisObject) as? TextView)?.performClick()
            result
        }

    }
}
