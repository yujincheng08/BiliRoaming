package me.iacn.biliroaming.hook

/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */
abstract class BaseHook(var mClassLoader: ClassLoader) {
    abstract fun startHook()
}