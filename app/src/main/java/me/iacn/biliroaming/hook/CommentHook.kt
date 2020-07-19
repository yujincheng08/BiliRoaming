package me.iacn.biliroaming.hook

import de.robv.android.xposed.XC_MethodHook
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.getObjectField
import me.iacn.biliroaming.utils.hookMethod
import me.iacn.biliroaming.utils.setIntField

/**
 * Created by iAcn on 2020/2/27
 * Email i@iacn.me
 */
class CommentHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("comment_floor", false)) return
        Log.d("startHook: Comment")
        val floorHook: XC_MethodHook = object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                val config = param.thisObject.getObjectField("config")
                config?.run {
                    setIntField("mShowFloor", 1)
                }
            }
        }
        "com.bilibili.app.comm.comment2.model.BiliCommentList".hookMethod(mClassLoader, "isShowFloor", floorHook)
        "com.bilibili.app.comm.comment2.model.BiliCommentCursorList".hookMethod(mClassLoader, "isShowFloor", floorHook)
        "com.bilibili.app.comm.comment2.model.BiliCommentDialogue".hookMethod(mClassLoader, "isShowFloor", floorHook)
        "com.bilibili.app.comm.comment2.model.BiliCommentDetail".hookMethod(mClassLoader, "isShowFloor", floorHook)
    }
}