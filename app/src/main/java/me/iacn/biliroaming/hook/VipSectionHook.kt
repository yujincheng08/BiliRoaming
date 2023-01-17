package me.iacn.biliroaming.hook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.findFieldByExactType
import me.iacn.biliroaming.utils.from
import me.iacn.biliroaming.utils.hookAfterMethod
import me.iacn.biliroaming.utils.sPrefs

class VipSectionHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("hidden", false)
            || !sPrefs.getBoolean("remove_vip_section", false)
        ) return
        val vipEntranceViewClass =
            "tv.danmaku.bili.ui.main2.mine.widgets.MineVipEntranceView".from(mClassLoader)
        val vipEntranceViewField =
            vipEntranceViewClass?.let { instance.homeUserCenterClass?.findFieldByExactType(it) }
        instance.homeUserCenterClass?.hookAfterMethod(
            "onCreateView",
            LayoutInflater::class.java,
            ViewGroup::class.java,
            Bundle::class.java
        ) {
            val self = it.thisObject
            (vipEntranceViewField?.get(self) as? View)?.visibility = View.GONE
            vipEntranceViewField?.set(self, null)
        }
    }
}
