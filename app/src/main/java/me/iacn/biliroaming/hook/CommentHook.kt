package me.iacn.biliroaming.hook

import android.view.View
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.replaceMethod
import me.iacn.biliroaming.utils.sPrefs

class CommentHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("comment_copy", false)) return
        instance.commentCopyClass?.replaceMethod("onLongClick", View::class.java) {
            true
        }
    }
}
