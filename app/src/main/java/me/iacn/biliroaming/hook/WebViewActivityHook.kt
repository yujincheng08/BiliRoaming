package me.iacn.biliroaming.hook

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.sPrefs

class WebViewActivityHook(mClassLoader: ClassLoader) : BaseHook(mClassLoader) {
    override fun startHook() {
        if (sPrefs.getBoolean("force_browser", false)) {
            Log.d("startHook: WebViewActivityHook")
            "tv.danmaku.bili.ui.webview.MWebActivity".hookBeforeMethod(
                mClassLoader, "onCreate", Bundle::class.java) {
                if (it.args[0] != null) return@hookBeforeMethod
                val activity = it.thisObject as Activity
                val url = activity.intent.data
                activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    data = url
                })
                activity.finish()
            }
        }
    }
}