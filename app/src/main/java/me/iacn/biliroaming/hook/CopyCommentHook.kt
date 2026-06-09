package me.iacn.biliroaming.hook

import android.content.Context
import me.iacn.biliroaming.utils.findClassOrNull
import me.iacn.biliroaming.utils.invokeOriginalMethod
import me.iacn.biliroaming.utils.replaceMethod
import me.iacn.biliroaming.utils.sPrefs


class CopyCommentHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("copy_comment", false)) return
        hookComment3CopyAction()
    }

    private fun hookComment3CopyAction() {
        if (sPrefs.getBoolean("comment_copy", false) &&
            sPrefs.getBoolean("comment_copy_enhance", false)
        ) return

        "com.bilibili.app.comment3.utils.CommentExtensionsKt"
            .findClassOrNull(mClassLoader)
            ?.declaredMethods
            ?.firstOrNull {
                it.returnType == Boolean::class.javaPrimitiveType &&
                    it.parameterTypes.contentEquals(arrayOf(String::class.java, Context::class.java))
            }
            ?.replaceMethod { param ->
                val text = param.args.getOrNull(0) as? String
                    ?: return@replaceMethod param.invokeOriginalMethod()
                val context = param.args.getOrNull(1) as? Context
                    ?: return@replaceMethod param.invokeOriginalMethod()
                SelectableCopyDialog.show(context, text)
                true
            }
    }
}
