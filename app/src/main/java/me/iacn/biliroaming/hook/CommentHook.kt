package me.iacn.biliroaming.hook

import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.replaceMethod

/**
 * Created by iAcn on 2020/2/27
 * Email i@iacn.me
 */
class CommentHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("comment_floor", false)) return
        Log.d("startHook: Comment")
        "com.bilibili.app.comm.comment2.model.BiliCommentConfig".replaceMethod(mClassLoader, "isShowFloor") {
            return@replaceMethod true
        }
    }

}