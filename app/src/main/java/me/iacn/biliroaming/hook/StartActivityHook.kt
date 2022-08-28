package me.iacn.biliroaming.hook

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import me.iacn.biliroaming.utils.hookBeforeAllMethods
import me.iacn.biliroaming.utils.packageName
import me.iacn.biliroaming.utils.sPrefs

class StartActivityHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        Instrumentation::class.java.hookBeforeAllMethods("execStartActivity") { param ->
            val intent = param.args[4] as? Intent ?: return@hookBeforeAllMethods
            val uri = intent.dataString ?: return@hookBeforeAllMethods
            if (sPrefs.getBoolean(
                    "replace_story_video",
                    false
                ) && uri.startsWith("bilibili://story/")
            ) {
                intent.component = ComponentName(
                    intent.component?.packageName ?: packageName,
                    "com.bilibili.video.videodetail.VideoDetailsActivity"
                )
                intent.data =
                    Uri.parse(intent.dataString?.replace("bilibili://story/", "bilibili://video/"))
            }
            if (sPrefs.getBoolean("force_browser", false)) {
                if (intent.component?.className?.endsWith("MWebActivity") == true) {
                    param.args[4] = Intent(Intent.ACTION_VIEW).apply {
                        data = intent.data
                    }
                }
            }
        }
    }
}
