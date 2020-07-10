package me.iacn.biliroaming.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.*
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.Log

/**
 * Created by iAcn on 2020/2/27
 * Email i@iacn.me
 */
class CommentHook(classLoader: ClassLoader?) : BaseHook(classLoader!!) {
    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("comment_floor", false)) return
        Log.d("startHook: Comment")
        val floorHook: XC_MethodHook = object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                val config = getObjectField(param.thisObject, "config")
                config?.let {
                    setIntField(it, "mShowFloor", 1)
                }
            }
        }
        findAndHookMethod("com.bilibili.app.comm.comment2.model.BiliCommentList",
                mClassLoader, "isShowFloor", floorHook)
        findAndHookMethod("com.bilibili.app.comm.comment2.model.BiliCommentCursorList",
                mClassLoader, "isShowFloor", floorHook)
        findAndHookMethod("com.bilibili.app.comm.comment2.model.BiliCommentDialogue",
                mClassLoader, "isShowFloor", floorHook)
        findAndHookMethod("com.bilibili.app.comm.comment2.model.BiliCommentDetail",
                mClassLoader, "isShowFloor", floorHook)
    }
}