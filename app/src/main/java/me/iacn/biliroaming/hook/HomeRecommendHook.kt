package me.iacn.biliroaming.hook

import android.content.Context
import android.net.Uri
import android.os.Build
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import org.json.JSONObject
import java.net.URL
import java.lang.reflect.Array as RArray

/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */

class HomeRecommendHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("purify_home_recommend", false)) return
        Log.d("startHook: HomeRecommend")

        if (isBuiltIn && is64 && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.e("Not support")
            Log.toast("Android O以下系统不支持64位Xpatch版，请使用32位版")
        } else {
            instance.retrofitResponseClass?.hookBeforeAllConstructors { param ->
                val url = getUrl(param.args[0])
                val body = param.args[1] ?: return@hookBeforeAllConstructors
                if (url != null && url.startsWith("https://app.bilibili.com/x/v2/feed/index") && !url.contains("/converge") && !url.contains("/tab") && !url.contains("/story")) {
                    removeHomeRecommendAds(body)
                }
            }
        }
    }

    /**
     * 只能这么改
     * 因为即使是
     * ```kotlin
     * val dataClass = body.getObjectField("data").javaClass
     * val dataJson = body.getObjectField("data").toJson()
     * body.setObjectField("data", dataClass.fromJson(dataJson)
     * ```
     * 也会导致崩溃
     */
    private fun removeHomeRecommendAds(body: Any) {
        body.getObjectField("data")?.getObjectFieldAs<ArrayList<Any>>("items")?.apply {
            val old = size
            removeAll {
                "ad" in (it.getObjectFieldAs("cardGoto") ?: "")
            }
            if (old - size > 0){
                Log.toast("移除广告 x${old - size}")
            }
        }
        body.getObjectField("data")?.getObjectFieldAs<ArrayList<Any>>("items")?.apply {
            removeAll {
                "large_cover_v6" in (it.getObjectFieldAs("cardType") ?: "") || 
                "large_cover_v9" in (it.getObjectFieldAs("cardType") ?: "")
            }
        }
    }

    private fun getUrl(response: Any): String? {
        val requestField = instance.requestField() ?: return null
        val urlField = instance.urlField() ?: return null
        val request = response.getObjectField(requestField)
        return request?.getObjectField(urlField)?.toString()
    }
}
