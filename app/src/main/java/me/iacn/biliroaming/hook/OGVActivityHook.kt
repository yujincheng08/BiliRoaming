package me.iacn.biliroaming.hook

import android.os.Bundle
import java.util.List
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class OGVActivityHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        Log.d("startHook: OGVActivity")
        "com.bilibili.bangumi.data.page.detail.entity.OGVActivityVo".from(mClassLoader)?.hookBeforeConstructor(
            Int::class.javaPrimitiveType, 
            "com.bilibili.bangumi.data.page.detail.entity.OGVActivityBarVo", 
            "com.bilibili.bangumi.data.page.detail.entity.OGVInvitationActivityHostVo",
            "com.bilibili.bangumi.data.page.detail.entity.OGVInvitationActivityGuestVo",
            List::class.java,
            "com.bilibili.bangumi.data.page.detail.entity.VipWatchingCountdownTaskVo",
            "com.bilibili.bangumi.data.page.detail.entity.OGVActivityDialogVo"
        ) { param ->
            Log.d("OGVActivity hook successfully.")
            Log.d("OGVActivity Details: ${param.args[0]}, ${param.args[1]}, ${param.args[2]}, ${param.args[3]}, ${param.args[4]}, ${param.args[5]}, ${param.args[6]}")
            param.args[0] = 0
            param.args[1] = null
            param.args[2] = null
            param.args[3] = null
            param.args[4].callMethodAs<List<*>?>("clear")
            param.args[5] = null
            param.args[6] = null
        }
    }
}