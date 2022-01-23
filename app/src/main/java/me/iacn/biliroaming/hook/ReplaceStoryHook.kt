package me.iacn.biliroaming.hook

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import me.iacn.biliroaming.utils.sPrefs
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookBeforeMethod

class ReplaceStoryHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("replace_story_video", false)) return;
        instance.storyVideoActivityClass?.hookBeforeMethod(
            "onCreate",
            Bundle::class.java
        ) { param ->
            val thiz = param.thisObject as Activity
            val intent = thiz.intent
            val url = intent.data
            val avid: String? = if (url != null && url.scheme == "bilibili" && url.host == "story" && url.path?.matches(Regex("/\\d+")) == true) {
                url.path!!.substring(1)
            } else null
            if (avid == null) {
                Log.toast("解析失败，无法替换竖版视频")
                return@hookBeforeMethod
            }
            intent.component = null
            intent.setPackage(thiz.packageName)
            intent.data = Uri.parse("bilibili://video/${avid}")
            thiz.startActivity(intent)
            Log.toast("已替换竖版视频")
            thiz.finish()
        }
    }
}
