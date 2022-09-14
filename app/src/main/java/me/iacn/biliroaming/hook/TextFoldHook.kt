package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class TextFoldHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val commentMaxLines by lazy {
        sPrefs.getInt("text_fold_comment_max_lines", DEF_COMMENT_MAX_LINES)
    }
    private val dynMaxLines by lazy {
        sPrefs.getInt("text_fold_dyn_max_lines", DEF_DYN_MAX_LINES)
    }
    private val dynLinesToAll by lazy {
        sPrefs.getInt("text_fold_dyn_lines_to_all", DEF_DYN_LINES_TO_ALL)
    }

    private var maxLineFieldName = ""

    companion object {
        const val DEF_COMMENT_MAX_LINES = 6
        const val DEF_DYN_MAX_LINES = 4
        const val DEF_DYN_LINES_TO_ALL = 10
    }

    override fun startHook() {
        if (commentMaxLines != DEF_COMMENT_MAX_LINES) {
            "com.bapis.bilibili.main.community.reply.v1.ReplyControl".from(mClassLoader)
                ?.replaceMethod("getMaxLine") { commentMaxLines.toLong() }
        }

        if (dynMaxLines != DEF_DYN_MAX_LINES) {
            instance.ellipsizingTextViewClass?.hookBeforeMethod(
                "setMaxLines",
                Int::class.javaPrimitiveType
            ) { param ->
                val maxLines = param.args[0] as Int
                if (maxLines == DEF_DYN_MAX_LINES)
                    param.args[0] = dynMaxLines
            }
            instance.ellipsizingTextViewClass?.hookAfterAllConstructors { param ->
                val fieldName = maxLineFieldName.ifEmpty {
                    (instance.ellipsizingTextViewClass?.declaredFields
                        ?.filter { it.type == Int::class.javaPrimitiveType }
                        ?.find { param.thisObject.getIntField(it.name) == DEF_DYN_MAX_LINES }
                        ?.name ?: "").also { maxLineFieldName = it }
                }.ifEmpty { return@hookAfterAllConstructors }
                param.thisObject.setIntField(fieldName, dynMaxLines)
            }
        }

        if (dynLinesToAll != DEF_DYN_LINES_TO_ALL) {
            instance.setLineToAllCount()?.let {
                instance.ellipsizingTextViewClass?.hookBeforeMethod(
                    it, Int::class.javaPrimitiveType
                ) { param -> param.args[0] = dynLinesToAll }
            }
        }
    }
}
