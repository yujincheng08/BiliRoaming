@file:Suppress("unused")

package me.iacn.biliroaming.utils

import android.content.res.XResources
import dalvik.system.BaseDexClassLoader
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge.*
import de.robv.android.xposed.XposedHelpers.*
import de.robv.android.xposed.callbacks.XC_LayoutInflated
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.util.*

typealias MethodHookParam = MethodHookParam
typealias Replacer = (MethodHookParam) -> Any?
typealias Hooker = (MethodHookParam) -> Unit

fun Class<*>.hookMethod(method: String?, vararg args: Any?) = try {
    findAndHookMethod(this, method, *args)
} catch (e: NoSuchMethodError) {
    Log.e(e)
    null
} catch (e: ClassNotFoundError) {
    Log.e(e)
    null
} catch (e: ClassNotFoundException) {
    Log.e(e)
    null
}

fun Member.hookMethod(callback: XC_MethodHook) = try {
    hookMethod(this, callback)
} catch (e: Throwable) {
    Log.e(e)
    null
}

inline fun MethodHookParam.callHooker(crossinline hooker: Hooker) = try {
    hooker(this)
} catch (e: Throwable) {
    Log.e("Error occurred calling hooker on ${this.method}")
    Log.e(e)
}

inline fun MethodHookParam.callReplacer(crossinline replacer: Replacer) = try {
    replacer(this)
} catch (e: Throwable) {
    Log.e("Error occurred calling replacer on ${this.method}")
    Log.e(e)
    null
}

inline fun Member.replaceMethod(crossinline replacer: Replacer) =
    hookMethod(object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam) = param.callReplacer(replacer)
    })

inline fun Member.hookAfterMethod(crossinline hooker: Hooker) =
    hookMethod(object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
    })

inline fun Member.hookBeforeMethod(crossinline hooker: (MethodHookParam) -> Unit) =
    hookMethod(object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
    })

inline fun Class<*>.hookBeforeMethod(
    method: String?,
    vararg args: Any?,
    crossinline hooker: Hooker
) = hookMethod(method, *args, object : XC_MethodHook() {
    override fun beforeHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
})

inline fun Class<*>.hookAfterMethod(
    method: String?,
    vararg args: Any?,
    crossinline hooker: Hooker
) = hookMethod(method, *args, object : XC_MethodHook() {
    override fun afterHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
})

inline fun Class<*>.replaceMethod(
    method: String?,
    vararg args: Any?,
    crossinline replacer: Replacer
) = hookMethod(method, *args, object : XC_MethodReplacement() {
    override fun replaceHookedMethod(param: MethodHookParam) = param.callReplacer(replacer)
})

fun Class<*>.hookAllMethods(methodName: String?, hooker: XC_MethodHook): Set<XC_MethodHook.Unhook> =
    try {
        hookAllMethods(this, methodName, hooker)
    } catch (e: NoSuchMethodError) {
        Log.e(e)
        emptySet()
    } catch (e: ClassNotFoundError) {
        Log.e(e)
        emptySet()
    } catch (e: ClassNotFoundException) {
        Log.e(e)
        emptySet()
    }

inline fun Class<*>.hookBeforeAllMethods(methodName: String?, crossinline hooker: Hooker) =
    hookAllMethods(methodName, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
    })

inline fun Class<*>.hookAfterAllMethods(methodName: String?, crossinline hooker: Hooker) =
    hookAllMethods(methodName, object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) = param.callHooker(hooker)

    })

inline fun Class<*>.replaceAfterAllMethods(methodName: String?, crossinline replacer: Replacer) =
    hookAllMethods(methodName, object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam) = param.callReplacer(replacer)
    })

fun Class<*>.hookConstructor(vararg args: Any?) = try {
    findAndHookConstructor(this, *args)
} catch (e: NoSuchMethodError) {
    Log.e(e)
    null
} catch (e: ClassNotFoundError) {
    Log.e(e)
    null
} catch (e: ClassNotFoundException) {
    Log.e(e)
    null
}

inline fun Class<*>.hookBeforeConstructor(vararg args: Any?, crossinline hooker: Hooker) =
    hookConstructor(*args, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
    })

inline fun Class<*>.hookAfterConstructor(vararg args: Any?, crossinline hooker: Hooker) =
    hookConstructor(*args, object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
    })

inline fun Class<*>.replaceConstructor(vararg args: Any?, crossinline hooker: Hooker) =
    hookConstructor(*args, object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
    })

fun Class<*>.hookAllConstructors(hooker: XC_MethodHook): Set<XC_MethodHook.Unhook> = try {
    hookAllConstructors(this, hooker)
} catch (e: NoSuchMethodError) {
    Log.e(e)
    emptySet()
} catch (e: ClassNotFoundError) {
    Log.e(e)
    emptySet()
} catch (e: ClassNotFoundException) {
    Log.e(e)
    emptySet()
}

inline fun Class<*>.hookAfterAllConstructors(crossinline hooker: Hooker) =
    hookAllConstructors(object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
    })

inline fun Class<*>.hookBeforeAllConstructors(crossinline hooker: Hooker) =
    hookAllConstructors(object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
    })

inline fun Class<*>.replaceAfterAllConstructors(crossinline hooker: Hooker) =
    hookAllConstructors(object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam) = param.callHooker(hooker)
    })

fun String.hookMethod(classLoader: ClassLoader, method: String?, vararg args: Any?) = try {
    findClass(classLoader)?.hookMethod(method, *args)
} catch (e: ClassNotFoundError) {
    Log.e(e)
    null
} catch (e: ClassNotFoundException) {
    Log.e(e)
    null
}

inline fun String.hookBeforeMethod(
    classLoader: ClassLoader,
    method: String?,
    vararg args: Any?,
    crossinline hooker: Hooker
) = try {
    findClass(classLoader)?.hookBeforeMethod(method, *args, hooker = hooker)
} catch (e: ClassNotFoundError) {
    Log.e(e)
    null
} catch (e: ClassNotFoundException) {
    Log.e(e)
    null
}

inline fun String.hookAfterMethod(
    classLoader: ClassLoader,
    method: String?,
    vararg args: Any?,
    crossinline hooker: Hooker
) = try {
    findClass(classLoader)?.hookAfterMethod(method, *args, hooker = hooker)
} catch (e: ClassNotFoundError) {
    Log.e(e)
    null
} catch (e: ClassNotFoundException) {
    Log.e(e)
    null
}

inline fun String.replaceMethod(
    classLoader: ClassLoader,
    method: String?,
    vararg args: Any?,
    crossinline replacer: Replacer
) = try {
    findClass(classLoader)?.replaceMethod(method, *args, replacer = replacer)
} catch (e: ClassNotFoundError) {
    Log.e(e)
    null
} catch (e: ClassNotFoundException) {
    Log.e(e)
    null
}

fun MethodHookParam.invokeOriginalMethod(): Any? = invokeOriginalMethod(method, thisObject, args)

fun Any.getObjectField(field: String?): Any = getObjectField(this, field)

fun Any.getObjectFieldOrNull(field: String?): Any? = try {
    getObjectField(this, field)
} catch (thr: Throwable) {
    null
}


@Suppress("UNCHECKED_CAST")
fun <T> Any.getObjectFieldAs(field: String?) = getObjectField(this, field) as T

@Suppress("UNCHECKED_CAST")
fun <T> Any.getObjectOrNullFieldAs(field: String?) = try {
    getObjectField(this, field) as T
} catch (thr: Throwable) {
    null
}

fun Any.getIntField(field: String?) = getIntField(this, field)

fun Any.getLongField(field: String?) = getLongField(this, field)

fun Any.getBooleanField(field: String?) = getBooleanField(this, field)

fun Any.callMethod(methodName: String?, vararg args: Any?): Any? =
    callMethod(this, methodName, *args)

fun Class<*>.callStaticMethod(methodName: String?, vararg args: Any?): Any? =
    callStaticMethod(this, methodName, *args)

@Suppress("UNCHECKED_CAST")
fun <T> Class<*>.callStaticMethodAs(methodName: String?, vararg args: Any?) =
    callStaticMethod(this, methodName, *args) as T

@Suppress("UNCHECKED_CAST")
fun <T> Class<*>.getStaticObjectFieldAs(field: String?) = getStaticObjectField(this, field) as T

fun Class<*>.getStaticObjectField(field: String?): Any? = getStaticObjectField(this, field)

fun Class<*>.setStaticObjectField(field: String?, obj: Any?) = apply {
    setStaticObjectField(this, field, obj)
}

@Suppress("UNCHECKED_CAST")
fun <T> Any.callMethodAs(methodName: String?, vararg args: Any?) =
    callMethod(this, methodName, *args) as T

fun Any.callMethod(methodName: String?, parameterTypes: Array<Class<*>>, vararg args: Any?): Any =
    callMethod(this, methodName, parameterTypes, *args)

fun Class<*>.callStaticMethod(
    methodName: String?,
    parameterTypes: Array<Class<*>>,
    vararg args: Any?
): Any = callStaticMethod(this, methodName, parameterTypes, *args)

fun String.findClass(classLoader: ClassLoader?) = try {
    findClass(this, classLoader)
} catch (e: ClassNotFoundError) {
    Log.e(e)
    null
} catch (e: ClassNotFoundException) {
    Log.e(e)
    null
}

fun String.findClassOrNull(classLoader: ClassLoader?): Class<*>? =
    findClassIfExists(this, classLoader)

fun Class<*>.new(vararg args: Any?): Any = newInstance(this, *args)

fun Class<*>.new(parameterTypes: Array<Class<*>>, vararg args: Any?): Any =
    newInstance(this, parameterTypes, *args)

fun Class<*>.findFieldOrNull(field: String?): Field? = findFieldIfExists(this, field)

fun <T> T.setIntField(field: String?, value: Int) = apply {
    setIntField(this, field, value)
}

fun <T> T.setLongField(field: String?, value: Long) = apply {
    setLongField(this, field, value)
}

fun <T> T.setObjectField(field: String?, value: Any?) = apply {
    setObjectField(this, field, value)
}

fun <T> T.setBooleanField(field: String?, value: Boolean) = apply {
    setBooleanField(this, field, value)
}

inline fun XResources.hookLayout(
    id: Int,
    crossinline hooker: (XC_LayoutInflated.LayoutInflatedParam) -> Unit
) {
    try {
        hookLayout(id, object : XC_LayoutInflated() {
            override fun handleLayoutInflated(liparam: LayoutInflatedParam) {
                try {
                    hooker(liparam)
                } catch (e: Throwable) {
                    Log.e(e)
                }
            }
        })
    } catch (e: Throwable) {
        Log.e(e)
    }
}

inline fun XResources.hookLayout(
    pkg: String,
    type: String,
    name: String,
    crossinline hooker: (XC_LayoutInflated.LayoutInflatedParam) -> Unit
) {
    try {
        val id = getIdentifier(name, type, pkg)
        hookLayout(id, hooker)
    } catch (e: Throwable) {
        Log.e(e)
    }
}

fun ClassLoader.allClassesList(): List<String> {
    var classLoader = this
    while (classLoader !is BaseDexClassLoader) {
        if (classLoader.parent != null) classLoader = classLoader.parent
        else return emptyList()
    }
    return classLoader.getObjectField("pathList").getObjectOrNullFieldAs<Array<Any>>("dexElements")
        ?.flatMap {
            it.getObjectFieldOrNull("dexFile")?.callMethodAs<Enumeration<String>>("entries")
                ?.toList()
                .orEmpty()
        }.orEmpty()
}
