package me.iacn.biliroaming.hook

import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookAfterMethod
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
            return@replaceMethod true;
        }
        "com.bilibili.lib.blconfig.internal.n".hookAfterMethod(mClassLoader, "a", String::class.java, Object::class.java) { param ->
            if (param.args[0] == "comment.rpc_enable")
                param.result= "0"
        }
    }
}