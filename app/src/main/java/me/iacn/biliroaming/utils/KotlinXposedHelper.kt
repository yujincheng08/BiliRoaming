@file:Suppress("unused")

package me.iacn.biliroaming.utils

import android.content.res.XResources
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge.*
import de.robv.android.xposed.XposedHelpers.*
import de.robv.android.xposed.callbacks.XC_LayoutInflated
import java.lang.reflect.Field
import java.lang.reflect.Member

fun Class<*>.hookMethod(method: String?, vararg args: Any?): XC_MethodHook.Unhook? {
    return try {
        findAndHookMethod(this, method, *args)
    } catch (e: NoSuchMethodError) {
        Log.e(e)
        null
    } catch (e: ClassNotFoundError) {
        Log.e(e)
        null
    }
}

fun Member.hookMethod(callback: XC_MethodHook): XC_MethodHook.Unhook? {
    return try {
        hookMethod(this, callback)
    } catch (e: Throwable) {
        Log.e(e)
        null
    }
}

inline fun Member.hookAfterMethod(crossinline hooker: (MethodHookParam) -> Unit): XC_MethodHook.Unhook? {
    return hookMethod(object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            try {
                hooker(param)
            } catch (e: Throwable) {
                Log.e(e)
            }
        }
    })
}

inline fun Member.hookBeforeMethod(crossinline hooker: (MethodHookParam) -> Unit): XC_MethodHook.Unhook? {
    return hookMethod(object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            try {
                hooker(param)
            } catch (e: Throwable) {
                Log.e(e)
            }
        }
    })
}

inline fun Class<*>.hookBeforeMethod(method: String?, vararg args: Any?, crossinline hooker: (MethodHookParam) -> Unit): XC_MethodHook.Unhook? {
    return hookMethod(method, *args, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            try {
                hooker(param)
            } catch (e: Throwable) {
                Log.e(e)
            }
        }
    })
}

inline fun Class<*>.hookAfterMethod(method: String?, vararg args: Any?, crossinline hooker: (MethodHookParam) -> Unit): XC_MethodHook.Unhook? {
    return hookMethod(method, *args, object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            try {
                hooker(param)
            } catch (e: Throwable) {
                Log.e(e)
            }
        }
    })
}

inline fun Class<*>.replaceMethod(method: String?, vararg args: Any?, crossinline hooker: (MethodHookParam) -> Any?): XC_MethodHook.Unhook? {
    return hookMethod(method, *args, object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam): Any? {
            return try {
                hooker(param)
            } catch (e: Throwable) {
                Log.e(e)
                null
            }
        }
    })
}

fun Class<*>.hookAllMethods(methodName: String?, hooker: XC_MethodHook): Set<XC_MethodHook.Unhook> {
    return try {
        hookAllMethods(this, methodName, hooker)
    } catch (e: NoSuchMethodError) {
        Log.e(e)
        HashSet()
    } catch (e: ClassNotFoundError) {
        Log.e(e)
        HashSet()
    }
}

inline fun Class<*>.hookBeforeAllMethods(methodName: String?, crossinline hooker: (MethodHookParam) -> Unit): Set<XC_MethodHook.Unhook> {
    return hookAllMethods(methodName, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            try {
                hooker(param)
            } catch (e: Throwable) {
                Log.e(e)
            }
        }
    })
}

inline fun Class<*>.hookAfterAllMethods(methodName: String?, crossinline hooker: (MethodHookParam) -> Unit): Set<XC_MethodHook.Unhook> {
    return hookAllMethods(methodName, object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            try {
                hooker(param)
            } catch (e: Throwable) {
                Log.e(e)
            }
        }
    })
}

inline fun Class<*>.replaceAfterAllMethods(methodName: String?, crossinline hooker: (MethodHookParam) -> Any?): Set<XC_MethodHook.Unhook> {
    return hookAllMethods(methodName, object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam): Any? {
            return try {
                hooker(param)
            } catch (e: Throwable) {
                Log.e(e)
                null
            }
        }
    })
}

fun Class<*>.hookConstructor(vararg args: Any?): XC_MethodHook.Unhook? {
    return try {
        findAndHookConstructor(this, *args)
    } catch (e: NoSuchMethodError) {
        Log.e(e)
        null
    } catch (e: ClassNotFoundError) {
        Log.e(e)
        null
    }
}

inline fun Class<*>.hookBeforeConstructor(vararg args: Any?, crossinline hooker: (MethodHookParam) -> Unit): XC_MethodHook.Unhook? {
    return hookConstructor(*args, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            try {
                hooker(param)
            } catch (e: Throwable) {
                Log.e(e)
            }
        }
    })
}

inline fun Class<*>.hookAfterConstructor(vararg args: Any?, crossinline hooker: (MethodHookParam) -> Unit): XC_MethodHook.Unhook? {
    return hookConstructor(*args, object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            try {
                hooker(param)
            } catch (e: Throwable) {
                Log.e(e)
            }
        }
    })
}

inline fun Class<*>.replaceConstructor(vararg args: Any?, crossinline hooker: (MethodHookParam) -> Unit): XC_MethodHook.Unhook? {
    return hookConstructor(*args, object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam) {
            try {
                hooker(param)
            } catch (e: Throwable) {
                Log.e(e)
            }
        }
    })
}

fun Class<*>.hookAllConstructors(hooker: XC_MethodHook): Set<XC_MethodHook.Unhook> {
    return try {
        hookAllConstructors(this, hooker)
    } catch (e: NoSuchMethodError) {
        Log.e(e)
        HashSet()
    } catch (e: ClassNotFoundError) {
        Log.e(e)
        HashSet()
    }
}

inline fun Class<*>.hookAfterAllConstructors(crossinline hooker: (MethodHookParam) -> Unit): Set<XC_MethodHook.Unhook> {
    return hookAllConstructors(object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            try {
                hooker(param)
            } catch (e: Throwable) {
                Log.e(e)
            }
        }
    })
}

inline fun Class<*>.hookBeforeAllConstructors(crossinline hooker: (MethodHookParam) -> Unit): Set<XC_MethodHook.Unhook> {
    return hookAllConstructors(object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            try {
                hooker(param)
            } catch (e: Throwable) {
                Log.e(e)
            }
        }
    })
}

inline fun Class<*>.replaceAfterAllConstructors(crossinline hooker: (MethodHookParam) -> Unit): Set<XC_MethodHook.Unhook> {
    return hookAllConstructors(object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam) {
            try {
                hooker(param)
            } catch (e: Throwable) {
                Log.e(e)
            }
        }
    })
}

fun String.hookMethod(classLoader: ClassLoader, method: String?, vararg args: Any?): XC_MethodHook.Unhook? {
    return try {
        findClass(classLoader)?.hookMethod(method, *args)
    } catch (e: ClassNotFoundError) {
        Log.e(e)
        null
    }
}

inline fun String.hookBeforeMethod(classLoader: ClassLoader, method: String?, vararg args: Any?, crossinline hooker: (MethodHookParam) -> Unit): XC_MethodHook.Unhook? {
    return try {
        findClass(classLoader)?.hookBeforeMethod(method, *args, hooker = hooker)
    } catch (e: ClassNotFoundError) {
        Log.e(e)
        null
    }
}

inline fun String.hookAfterMethod(classLoader: ClassLoader, method: String?, vararg args: Any?, crossinline hooker: (MethodHookParam) -> Unit): XC_MethodHook.Unhook? {
    return try {
        findClass(classLoader)?.hookAfterMethod(method, *args, hooker = hooker)
    } catch (e: ClassNotFoundError) {
        Log.e(e)
        null
    }
}

inline fun String.replaceMethod(classLoader: ClassLoader, method: String?, vararg args: Any?, crossinline hooker: (MethodHookParam) -> Any?): XC_MethodHook.Unhook? {
    return try {
        findClass(classLoader)?.replaceMethod(method, *args, hooker = hooker)
    } catch (e: ClassNotFoundError) {
        Log.e(e)
        null
    }
}

fun MethodHookParam.invokeOriginalMethod(): Any? {
    return invokeOriginalMethod(method, thisObject, args)
}

fun Any.getObjectField(field: String?): Any? {
    return getObjectField(this, field)
}

@Suppress("UNCHECKED_CAST")
fun <T> Any.getObjectFieldAs(field: String?): T {
    return getObjectField(this, field) as T
}

fun Any.getIntField(field: String?): Int {
    return getIntField(this, field)
}

fun Any.getLongField(field: String?): Long {
    return getLongField(this, field)
}

fun Any.getBooleanField(field: String?): Boolean {
    return getBooleanField(this, field)
}

fun Any.callMethod(methodName: String?, vararg args: Any?): Any? {
    return callMethod(this, methodName, *args)
}

fun Class<*>.callStaticMethod(methodName: String?, vararg args: Any?): Any? {
    return callStaticMethod(this, methodName, *args)
}

@Suppress("UNCHECKED_CAST")
fun <T> Class<*>.callStaticMethodAs(methodName: String?, vararg args: Any?): T {
    return callStaticMethod(this, methodName, *args) as T
}

@Suppress("UNCHECKED_CAST")
fun <T> Class<*>.getStaticObjectFieldAs(field: String?): T {
    return getStaticObjectField(this, field) as T
}

fun Class<*>.getStaticObjectField(field: String?): Any? {
    return getStaticObjectField(this, field)
}

fun Class<*>.setStaticObjectField(field: String?, obj: Any?): Class<*> {
    setStaticObjectField(this, field, obj)
    return this
}

@Suppress("UNCHECKED_CAST")
fun <T> Any.callMethodAs(methodName: String?, vararg args: Any?): T {
    return callMethod(this, methodName, *args) as T
}

fun Any.callMethod(methodName: String?, parameterTypes: Array<Class<*>>, vararg args: Any?): Any {
    return callMethod(this, methodName, parameterTypes, *args)
}

fun Class<*>.callStaticMethod(methodName: String?, parameterTypes: Array<Class<*>>, vararg args: Any?): Any {
    return callStaticMethod(this, methodName, parameterTypes, *args)
}

fun String.findClass(classLoader: ClassLoader?): Class<*>? {
    return try {
        findClass(this, classLoader)
    } catch (e: ClassNotFoundError) {
        Log.e(e)
        null
    }
}

fun String.findClassOrNull(classLoader: ClassLoader?): Class<*>? {
    return findClassIfExists(this, classLoader)
}

fun Class<*>.new(vararg args: Any?): Any {
    return newInstance(this, *args)
}

fun Class<*>.new(parameterTypes: Array<Class<*>>, vararg args: Any?): Any {
    return newInstance(this, parameterTypes, *args)
}

fun Class<*>.findFieldOrNull(field: String?): Field? {
    return findFieldIfExists(this, field)
}

fun <T> T.setIntField(field: String?, value: Int): T {
    setIntField(this, field, value)
    return this
}

fun <T> T.setLongField(field: String?, value: Long): T {
    setLongField(this, field, value)
    return this
}

fun <T> T.setObjectField(field: String?, value: Any?): T {
    setObjectField(this, field, value)
    return this
}

fun <T> T.setBooleanField(field: String?, value: Boolean): T {
    setBooleanField(this, field, value)
    return this
}

inline fun XResources.hookLayout(id: Int, crossinline hooker: (XC_LayoutInflated.LayoutInflatedParam) -> Unit) {
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

inline fun XResources.hookLayout(pkg: String, type: String, name: String, crossinline hooker: (XC_LayoutInflated.LayoutInflatedParam) -> Unit) {
    try {
        val id = getIdentifier(name, type, pkg)
        hookLayout(id, hooker)
    } catch (e: Throwable) {
        Log.e(e)
    }
}
