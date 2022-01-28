package me.iacn.biliroaming.hook

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.sPrefs

class ReplaceStoryHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("replace_story_video", false)) return
        instance.storyVideoActivityClass?.hookBeforeMethod(
            "onCreate",
            Bundle::class.java
        ) { param ->
            val thiz = param.thisObject as Activity
            val intent = thiz.intent
            val url = intent.data
            if (url != null && url.scheme == "bilibili" && url.host == "story" && url.path?.matches(
                    Regex("/\\d+")
                ) == true
            ) {
                intent.data = Uri.parse("bilibili://video/${url.path?.substring(1)}")
            } else {
                Log.toast("解析失败，无法替换竖版视频")
                return@hookBeforeMethod
            }
            intent.component = null
            intent.setPackage(thiz.packageName)
            thiz.startActivity(intent)
            Log.toast("已替换竖版视频")
            thiz.finish()
        }
    }
}
