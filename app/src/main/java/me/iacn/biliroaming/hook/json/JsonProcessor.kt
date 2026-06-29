package me.iacn.biliroaming.hook.json

/** FastJSON parseObject 结果处理器（对齐 gson/GsonProcessor、kotlinx/KotlinxProcessor）。
 *  targetClassName 为全限定名——onPackageReady 时机无法持有目标类 Class 引用，用字符串匹配最稳。 */
interface JsonProcessor {
    val targetClassName: String
    fun shouldEnable(): Boolean
    /** 注册前一次性初始化，返回 false 则该 processor 被移除 */
    fun init(classLoader: ClassLoader): Boolean = true
    fun process(result: Any, classLoader: ClassLoader)
}
