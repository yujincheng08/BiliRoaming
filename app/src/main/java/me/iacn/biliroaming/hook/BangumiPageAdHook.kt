package me.iacn.biliroaming.hook

import android.os.Bundle
import java.util.List
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class BangumiPageAdHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (sPrefs.getBoolean("block_bangumi_page_ads", false)) {
            Log.d("startHook: BangumiPageAd")
            // activity toast ad
            "com.bilibili.bangumi.data.page.detail.entity.OGVActivityVo".from(mClassLoader)?.hookBeforeConstructor(
                Int::class.javaPrimitiveType, 
                "com.bilibili.bangumi.data.page.detail.entity.OGVActivityBarVo", 
                "com.bilibili.bangumi.data.page.detail.entity.OGVInvitationActivityHostVo",
                "com.bilibili.bangumi.data.page.detail.entity.OGVInvitationActivityGuestVo",
                List::class.java,
                "com.bilibili.bangumi.data.page.detail.entity.VipWatchingCountdownTaskVo",
                "com.bilibili.bangumi.data.page.detail.entity.OGVActivityDialogVo"
            ) { param ->
                param.args[0] = 0
                param.args[1] = null
                param.args[2] = null
                param.args[3] = null
                param.args[4].callMethodAs<List<*>?>("clear")
                param.args[5] = null
                param.args[6] = null
            }
            // mall
            "com.bilibili.bangumi.data.page.detail.entity.BangumiUniformSeason".from(mClassLoader)?.let { class_ ->
                val activityEntranceListMethod = class_.declaredMethods?.find {
                    it.parameterTypes.size == 0 && it.genericReturnType.toString().contains("ActivityEntrance")
                } ?.name
                class_.replaceMethod(activityEntranceListMethod) { null }
            }
        }
    }
}
