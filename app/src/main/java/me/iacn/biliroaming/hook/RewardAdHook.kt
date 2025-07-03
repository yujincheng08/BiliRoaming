package me.iacn.biliroaming.hook

import android.os.Bundle
import android.widget.TextView
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class RewardAdHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {

        if (!sPrefs.getBoolean("skip_reward_ad", false)) return

        Log.d("startHook: RewardAd")

        instance.rewardAdClass?.hookAfterMethod("onCreate", Bundle::class.java) { params ->
            params.thisObject.setBooleanField(instance.rewardFlag(), true)
            (params.thisObject.javaClass.declaredFields.firstOrNull {
                it.type == TextView::class.java
            }?.apply { isAccessible = true }?.get(params.thisObject) as? TextView)?.performClick()
        }

    }
}
