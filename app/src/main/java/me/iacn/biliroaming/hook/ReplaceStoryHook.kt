package me.iacn.biliroaming.hook

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import me.iacn.biliroaming.utils.sPrefs
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookBeforeMethod

class ReplaceStoryHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("replace_story_video", false)) return;
        if (instance.storyVideoActivityClass == null) {
            Log.e("storyVideoActivityClass not found");
        }
        Log.d("storyVideoActivityClass=${instance.storyVideoActivityClass}")
        instance.storyVideoActivityClass?.hookBeforeMethod(
            "onCreate",
            Bundle::class.java
        ) { param ->
            val thiz = param.thisObject as Activity
            val intent = thiz.intent
            val url = intent.data
            var avid: String? = null
            if (url != null && url.scheme == "bilibili" && url.host == "story" && url.path?.matches(Regex("/\\d+")) == true) {
                url.path!!.substring(1).also { avid = it }
            }
            if (avid == null) {
                Log.toast("解析失败，无法替换竖版视频")
                return@hookBeforeMethod
            }
            val newIntent = Intent()
            newIntent.setPackage(thiz.packageName)
            newIntent.data = Uri.parse("bilibili://video/${avid}")
            thiz.startActivity(newIntent)
            Log.toast("已替换竖版视频")
            thiz.finish()
        }
    }
}