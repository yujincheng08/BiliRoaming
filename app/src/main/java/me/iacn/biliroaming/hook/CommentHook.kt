package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

/**
 * Created by iAcn on 2020/2/27
 * Email i@iacn.me
 */
class CommentHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("comment_floor", false)) return
        Log.d("startHook: Comment")
        "com.bilibili.app.comm.comment2.model.BiliCommentConfig".replaceMethod(
            mClassLoader,
            "isShowFloor"
        ) {
            return@replaceMethod true
        }
    }

    override fun lateInitHook() {
        if (!sPrefs.getBoolean("comment_floor", false)) return
        Log.d("lateHook: Comment")
        instance.commentRpcClass?.methods?.forEach {
            if (it.parameterTypes.isEmpty() && it.returnType == Boolean::class.java) {
                it.replaceMethod { return@replaceMethod false }
            }
        }
    }
}
