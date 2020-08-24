package me.iacn.biliroaming.hook

import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.*

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

        // For some cases where the above hooker is not working because of the short method
        val hooker: (MethodHookParam) -> Unit = { param ->
            param.thisObject.getObjectField("config")?.setIntField("mShowFloor", 1)
        }
        "com.bilibili.app.comm.comment2.model.BiliCommentList".hookBeforeMethod(mClassLoader, "isShowFloor", hooker = hooker)
        "com.bilibili.app.comm.comment2.model.BiliCommentCursorList".hookBeforeMethod(mClassLoader, "isShowFloor", hooker = hooker)
        "com.bilibili.app.comm.comment2.model.BiliCommentDialogue".hookBeforeMethod(mClassLoader, "isShowFloor", hooker = hooker)
        "com.bilibili.app.comm.comment2.model.BiliCommentDetail".hookBeforeMethod(mClassLoader, "isShowFloor", hooker = hooker)
    }

}