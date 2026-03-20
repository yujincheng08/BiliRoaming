package me.iacn.biliroaming.utils

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Member

class MethodHookParam(val chain: XposedInterface.Chain) {
    val method: Member get() = chain.executable
    val thisObject: Any? get() = chain.thisObject
    val args: Array<Any?> = chain.args.toTypedArray()

    var returnEarly = false

    private var _result: Any? = null
    private var _throwable: Throwable? = null

    var result: Any?
        get() = _result
        set(value) {
            _result = value
            _throwable = null
            returnEarly = true
        }

    var throwable: Throwable?
        get() = _throwable
        set(value) {
            _throwable = value
            _result = null
            returnEarly = true
        }

    @PublishedApi
    internal fun setResultInternal(value: Any?) {
        _result = value
    }

    @PublishedApi
    internal fun setThrowableInternal(value: Throwable) {
        _throwable = value
    }

    fun invokeOriginalMethod(): Any? = chain.proceed(args)
}
