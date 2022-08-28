package me.iacn.biliroaming.hook

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookBeforeAllMethods
import me.iacn.biliroaming.utils.packageName
import me.iacn.biliroaming.utils.sPrefs

class StartActivityHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        Instrumentation::class.java.hookBeforeAllMethods("execStartActivity") { param ->
            val intent = param.args[4] as? Intent ?: return@hookBeforeAllMethods
            val uri = intent.dataString ?: return@hookBeforeAllMethods
            if (sPrefs.getBoolean("replace_story_video", false) && uri.startsWith("bilibili://story/")) {
                intent.component = ComponentName(
                    intent.component?.packageName ?: packageName,
                    "com.bilibili.video.videodetail.VideoDetailsActivity"
                )
                intent.data = Uri.parse(intent.dataString?.replace("bilibili://story/", "bilibili://video/"))
            }
            if (intent.component?.className == "com.bilibili.video.story.StoryVideoActivity") {
                Log.e("after intent: $intent")
            }
        }
    }
}
