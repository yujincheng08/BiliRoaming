package me.iacn.biliroaming.hook

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class ReplaceStoryHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("replace_story_video", false)) return
        instance.basicIndexItemClass?.hookAfterMethod(
            "getUri"
        ) { param ->
            var uri = (param.result ?: return@hookAfterMethod) as String
            if (uri.startsWith("bilibili://story/")) {
                uri = "bilibili://video/" + uri.substringAfter("bilibili://story/")
            }
            param.result = uri
        }
    }
}
