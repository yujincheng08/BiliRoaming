@file:Suppress("unused")

package me.iacn.biliroaming.utils

import dalvik.system.BaseDexClassLoader
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.HookHandle
import me.iacn.biliroaming.XposedInit
import java.lang.reflect.*
import java.util.*

typealias HookCallback = (XposedInterface.Chain) -> Any?

// --- Internal reflection helpers ---

private val PRIMITIVE_TO_WRAPPER = mapOf<Class<*>?, Class<*>?>(
    Boolean::class.javaPrimitiveType to Boolean::class.javaObjectType,
    Byte::class.javaPrimitiveType to Byte::class.javaObjectType,
    Char::class.javaPrimitiveType to Char::class.javaObjectType,
    Short::class.javaPrimitiveType to Short::class.javaObjectType,
    Int::class.javaPrimitiveType to Int::class.javaObjectType,
    Long::class.javaPrimitiveType to Long::class.javaObjectType,
    Float::class.javaPrimitiveType to Float::class.javaObjectType,
    Double::class.javaPrimitiveType to Double::class.javaObjectType,
)

private fun isAssignable(target: Class<*>, source: Class<*>?): Boolean {
    if (source == null) return !target.isPrimitive
    if (target.isAssignableFrom(source)) return true
    val wrappedTarget = PRIMITIVE_TO_WRAPPER[target] ?: target
    val wrappedSource = PRIMITIVE_TO_WRAPPER[source] ?: source
    return wrappedTarget!!.isAssignableFrom(wrappedSource!!)
}

private fun getParameterTypes(args: Array<out Any?>): Array<Class<*>> =
    args.map { it?.javaClass ?: Any::class.java }.toTypedArray()

private fun resolveClassName(name: String, classLoader: ClassLoader?): Class<*> {
    if (name.endsWith("[]")) {
        val componentClass = resolveClassName(name.dropLast(2), classLoader)
        return java.lang.reflect.Array.newInstance(componentClass, 0).javaClass
    }
    return Class.forName(name, false, classLoader ?: ClassLoader.getSystemClassLoader())
}

@PublishedApi internal fun resolveParameterTypes(args: Array<out Any?>, classLoader: ClassLoader? = null): Array<Class<*>> =
    args.map {
        when (it) {
            is Class<*> -> it
            is String -> resolveClassName(it, classLoader)
            else -> throw IllegalArgumentException("Expected Class or String, got: ${it?.javaClass}")
        }
    }.toTypedArray()

private fun findFieldRecursive(clazz: Class<*>, fieldName: String): Field {
    var c: Class<*>? = clazz
    while (c != null) {
        try {
            return c.getDeclaredField(fieldName).also { it.isAccessible = true }
        } catch (_: NoSuchFieldException) {
            c = c.superclass
        }
    }
    throw NoSuchFieldException("Field $fieldName not found in ${clazz.name}")
}

@PublishedApi internal fun findMethodExact(clazz: Class<*>, methodName: String, vararg paramTypes: Class<*>): Method {
    var c: Class<*>? = clazz
    while (c != null) {
        try {
            return c.getDeclaredMethod(methodName, *paramTypes).also { it.isAccessible = true }
        } catch (_: NoSuchMethodException) {
            c = c.superclass
        }
    }
    throw NoSuchMethodException("$methodName(${paramTypes.joinToString { it.name }}) in ${clazz.name}")
}

private fun findMethodBestMatch(clazz: Class<*>, methodName: String, argTypes: Array<Class<*>>): Method {
    try {
        return findMethodExact(clazz, methodName, *argTypes)
    } catch (_: NoSuchMethodException) {
    }
    var c: Class<*>? = clazz
    while (c != null) {
        for (method in c.declaredMethods) {
            if (method.name != methodName) continue
            val params = method.parameterTypes
            if (params.size != argTypes.size) continue
            var match = true
            for (i in params.indices) {
                if (!isAssignable(params[i], argTypes[i])) {
                    match = false
                    break
                }
            }
            if (match) {
                method.isAccessible = true
                return method
            }
        }
        c = c.superclass
    }
    throw NoSuchMethodException("$methodName(${argTypes.joinToString { it.name }}) in ${clazz.name}")
}

private fun findConstructorBestMatch(clazz: Class<*>, argTypes: Array<Class<*>>): Constructor<*> {
    try {
        return clazz.getDeclaredConstructor(*argTypes).also { it.isAccessible = true }
    } catch (_: NoSuchMethodException) {
    }
    for (ctor in clazz.declaredConstructors) {
        val params = ctor.parameterTypes
        if (params.size != argTypes.size) continue
        var match = true
        for (i in params.indices) {
            if (!isAssignable(params[i], argTypes[i])) {
                match = false
                break
            }
        }
        if (match) {
            ctor.isAccessible = true
            return ctor
        }
    }
    throw NoSuchMethodException("Constructor(${argTypes.joinToString { it.name }}) in ${clazz.name}")
}

// --- Hook infrastructure ---

@PublishedApi internal fun hookExecutable(executable: Executable, hooker: XposedInterface.Hooker): HookHandle? = try {
    XposedInit.instance.hook(executable).intercept(hooker)
} catch (e: Throwable) {
    Log.e(e)
    null
}

@PublishedApi internal inline fun createHooker(crossinline callback: HookCallback) = object : XposedInterface.Hooker {
    override fun intercept(chain: XposedInterface.Chain): Any? = try {
        callback(chain)
    } catch (e: Throwable) {
        Log.e("Error occurred calling hooker on ${chain.executable}")
        Log.e(e)
        chain.proceed()
    }
}

// --- Member hook functions ---

inline fun Member.hookMethod(crossinline callback: HookCallback) =
    hookExecutable(this as Executable, createHooker(callback))

// --- Class method hook functions ---

inline fun Class<*>.hookMethod(
    method: String?,
    vararg args: Any?,
    crossinline callback: HookCallback
): HookHandle? = try {
    val paramTypes = resolveParameterTypes(args, this.classLoader)
    val m = findMethodExact(this, method!!, *paramTypes)
    hookExecutable(m, createHooker(callback))
} catch (e: NoSuchMethodError) {
    Log.e(e); null
} catch (e: NoSuchMethodException) {
    Log.e(e); null
} catch (e: NoClassDefFoundError) {
    Log.e(e); null
} catch (e: ClassNotFoundException) {
    Log.e(e); null
}

// --- hookAllMethods ---

fun Class<*>.hookAllMethods(methodName: String?, hooker: XposedInterface.Hooker): Set<HookHandle> =
    try {
        val handles = mutableSetOf<HookHandle>()
        for (method in declaredMethods) {
            if (method.name == methodName) {
                method.isAccessible = true
                try {
                    handles.add(XposedInit.instance.hook(method).intercept(hooker))
                } catch (e: Throwable) {
                    Log.e(e)
                }
            }
        }
        handles
    } catch (e: NoSuchMethodError) {
        Log.e(e)
        emptySet()
    } catch (e: NoClassDefFoundError) {
        Log.e(e)
        emptySet()
    } catch (e: ClassNotFoundException) {
        Log.e(e)
        emptySet()
    }

inline fun Class<*>.hookAllMethods(methodName: String?, crossinline callback: HookCallback) =
    hookAllMethods(methodName, createHooker(callback))

// --- Constructor hook functions ---

inline fun Class<*>.hookConstructor(vararg args: Any?, crossinline callback: HookCallback): HookHandle? =
    try {
        val paramTypes = resolveParameterTypes(args, this.classLoader)
        val ctor = getDeclaredConstructor(*paramTypes).also { it.isAccessible = true }
        hookExecutable(ctor, createHooker(callback))
    } catch (e: NoSuchMethodError) {
        Log.e(e); null
    } catch (e: NoSuchMethodException) {
        Log.e(e); null
    } catch (e: NoClassDefFoundError) {
        Log.e(e); null
    } catch (e: ClassNotFoundException) {
        Log.e(e); null
    }

// --- hookAllConstructors ---

fun Class<*>.hookAllConstructors(hooker: XposedInterface.Hooker): Set<HookHandle> = try {
    val handles = mutableSetOf<HookHandle>()
    for (ctor in declaredConstructors) {
        ctor.isAccessible = true
        try {
            handles.add(XposedInit.instance.hook(ctor).intercept(hooker))
        } catch (e: Throwable) {
            Log.e(e)
        }
    }
    handles
} catch (e: NoSuchMethodError) {
    Log.e(e)
    emptySet()
} catch (e: NoClassDefFoundError) {
    Log.e(e)
    emptySet()
} catch (e: ClassNotFoundException) {
    Log.e(e)
    emptySet()
}

inline fun Class<*>.hookAllConstructors(crossinline callback: HookCallback) =
    hookAllConstructors(createHooker(callback))

// --- String hook functions ---

inline fun String.hookMethod(
    classLoader: ClassLoader,
    method: String?,
    vararg args: Any?,
    crossinline callback: HookCallback
) = try {
    findClass(classLoader).hookMethod(method, *args, callback = callback)
} catch (e: NoClassDefFoundError) {
    Log.e(e)
    null
} catch (e: ClassNotFoundException) {
    Log.e(e)
    null
}

// --- Utility: runCatchingOrNull ---

inline fun <T, R> T.runCatchingOrNull(func: T.() -> R?) = try {
    func()
} catch (e: Throwable) {
    null
}

// --- Field access functions ---

fun Any.getObjectField(field: String?): Any? =
    findFieldRecursive(javaClass, field!!).get(this)

fun Any.getObjectFieldOrNull(field: String?): Any? = runCatchingOrNull {
    getObjectField(field)
}

@Suppress("UNCHECKED_CAST")
fun <T> Any.getObjectFieldAs(field: String?) = getObjectField(field) as T

@Suppress("UNCHECKED_CAST")
fun <T> Any.getObjectFieldOrNullAs(field: String?) = runCatchingOrNull {
    getObjectField(field) as T
}

fun Any.getIntField(field: String?) = findFieldRecursive(javaClass, field!!).getInt(this)

fun Any.getIntFieldOrNull(field: String?) = runCatchingOrNull {
    getIntField(field)
}

fun Any.getLongField(field: String?) = findFieldRecursive(javaClass, field!!).getLong(this)

fun Any.getLongFieldOrNull(field: String?) = runCatchingOrNull {
    getLongField(field)
}

fun Any.getBooleanFieldOrNull(field: String?) = runCatchingOrNull {
    findFieldRecursive(javaClass, field!!).getBoolean(this)
}

// --- Method call functions ---

fun Any.callMethod(methodName: String?, vararg args: Any?): Any? {
    val argTypes = getParameterTypes(args)
    val method = findMethodBestMatch(javaClass, methodName!!, argTypes)
    return method.invoke(this, *args)
}

fun Any.callMethodOrNull(methodName: String?, vararg args: Any?): Any? = runCatchingOrNull {
    callMethod(methodName, *args)
}

fun Class<*>.callStaticMethod(methodName: String?, vararg args: Any?): Any? {
    val argTypes = getParameterTypes(args)
    val method = findMethodBestMatch(this, methodName!!, argTypes)
    return method.invoke(null, *args)
}

fun Class<*>.callStaticMethodOrNull(methodName: String?, vararg args: Any?): Any? =
    runCatchingOrNull {
        callStaticMethod(methodName, *args)
    }

@Suppress("UNCHECKED_CAST")
fun <T> Class<*>.callStaticMethodAs(methodName: String?, vararg args: Any?) =
    callStaticMethod(methodName, *args) as T

@Suppress("UNCHECKED_CAST")
fun <T> Class<*>.callStaticMethodOrNullAs(methodName: String?, vararg args: Any?) =
    runCatchingOrNull {
        callStaticMethod(methodName, *args) as T
    }

@Suppress("UNCHECKED_CAST")
fun <T> Class<*>.getStaticObjectFieldAs(field: String?) =
    findFieldRecursive(this, field!!).get(null) as T

@Suppress("UNCHECKED_CAST")
fun <T> Class<*>.getStaticObjectFieldOrNullAs(field: String?) = runCatchingOrNull {
    getStaticObjectFieldAs<T>(field)
}

fun Class<*>.getStaticObjectField(field: String?): Any? =
    findFieldRecursive(this, field!!).get(null)

fun Class<*>.getStaticObjectFieldOrNull(field: String?): Any? = runCatchingOrNull {
    getStaticObjectField(field)
}

fun Class<*>.setStaticObjectField(field: String?, obj: Any?) = apply {
    findFieldRecursive(this, field!!).set(null, obj)
}

fun Class<*>.setStaticObjectFieldIfExist(field: String?, obj: Any?) = apply {
    try {
        setStaticObjectField(field, obj)
    } catch (ignored: Throwable) {
    }
}

inline fun <reified T> Class<*>.findFieldByExactType(): Field? =
    findFirstFieldByExactTypeOrNull(T::class.java)

fun Class<*>.findFieldByExactType(type: Class<*>): Field? =
    findFirstFieldByExactTypeOrNull(type)

@Suppress("UNCHECKED_CAST")
fun <T> Any.callMethodAs(methodName: String?, vararg args: Any?) =
    callMethod(methodName, *args) as T

@Suppress("UNCHECKED_CAST")
fun <T> Any.callMethodOrNullAs(methodName: String?, vararg args: Any?) = runCatchingOrNull {
    callMethod(methodName, *args) as T
}

fun Any.callMethod(methodName: String?, parameterTypes: Array<Class<*>>, vararg args: Any?): Any? {
    val method = findMethodExact(javaClass, methodName!!, *parameterTypes)
    return method.invoke(this, *args)
}

fun Any.callMethodOrNull(
    methodName: String?,
    parameterTypes: Array<Class<*>>,
    vararg args: Any?
): Any? = runCatchingOrNull {
    callMethod(methodName, parameterTypes, *args)
}

fun Class<*>.callStaticMethod(
    methodName: String?,
    parameterTypes: Array<Class<*>>,
    vararg args: Any?
): Any? {
    val method = findMethodExact(this, methodName!!, *parameterTypes)
    return method.invoke(null, *args)
}

fun Class<*>.callStaticMethodOrNull(
    methodName: String?,
    parameterTypes: Array<Class<*>>,
    vararg args: Any?
): Any? = runCatchingOrNull {
    callStaticMethod(methodName, parameterTypes, *args)
}

// --- Class finding ---

fun String.findClass(classLoader: ClassLoader?): Class<*> =
    Class.forName(this, false, classLoader)

infix fun String.on(classLoader: ClassLoader?): Class<*> = findClass(classLoader)

fun String.findClassOrNull(classLoader: ClassLoader?): Class<*>? = try {
    Class.forName(this, false, classLoader)
} catch (_: ClassNotFoundException) {
    null
}

infix fun String.from(classLoader: ClassLoader?): Class<*>? = findClassOrNull(classLoader)

// --- New instance ---

fun Class<*>.new(vararg args: Any?): Any {
    val argTypes = getParameterTypes(args)
    val ctor = findConstructorBestMatch(this, argTypes)
    return ctor.newInstance(*args)!!
}

fun Class<*>.new(parameterTypes: Array<Class<*>>, vararg args: Any?): Any {
    val ctor = getDeclaredConstructor(*parameterTypes).also { it.isAccessible = true }
    return ctor.newInstance(*args)!!
}

// --- Field finding ---

fun Class<*>.findField(field: String?): Field = findFieldRecursive(this, field!!)

fun Class<*>.findFieldOrNull(field: String?): Field? = try {
    findFieldRecursive(this, field!!)
} catch (_: NoSuchFieldException) {
    null
}

// --- Set field functions ---

fun <T> T.setIntField(field: String?, value: Int) = apply {
    findFieldRecursive(this!!.javaClass, field!!).setInt(this, value)
}

fun <T> T.setLongField(field: String?, value: Long) = apply {
    findFieldRecursive(this!!.javaClass, field!!).setLong(this, value)
}

fun <T> T.setObjectField(field: String?, value: Any?) = apply {
    findFieldRecursive(this!!.javaClass, field!!).set(this, value)
}

fun <T> T.setBooleanField(field: String?, value: Boolean) = apply {
    findFieldRecursive(this!!.javaClass, field!!).setBoolean(this, value)
}

fun <T> T.setFloatField(field: String?, value: Float) = apply {
    findFieldRecursive(this!!.javaClass, field!!).setFloat(this, value)
}

// --- findFirstFieldByExactType ---

fun Class<*>.findFirstFieldByExactType(type: Class<*>): Field {
    var c: Class<*>? = this
    while (c != null) {
        for (field in c.declaredFields) {
            if (field.type == type) {
                field.isAccessible = true
                return field
            }
        }
        c = c.superclass
    }
    throw NoSuchFieldException("No field of type ${type.name} in $name")
}

fun Class<*>.findFirstFieldByExactTypeOrNull(type: Class<*>?): Field? = runCatchingOrNull {
    if (type == null) null else findFirstFieldByExactType(type)
}

fun Any.getFirstFieldByExactType(type: Class<*>): Any? =
    javaClass.findFirstFieldByExactType(type).get(this)

@Suppress("UNCHECKED_CAST")
fun <T> Any.getFirstFieldByExactTypeAs(type: Class<*>) =
    javaClass.findFirstFieldByExactType(type).get(this) as? T

inline fun <reified T : Any> Any.getFirstFieldByExactType() =
    javaClass.findFirstFieldByExactType(T::class.java).get(this) as? T

fun Any.getFirstFieldByExactTypeOrNull(type: Class<*>?): Any? = runCatchingOrNull {
    javaClass.findFirstFieldByExactTypeOrNull(type)?.get(this)
}

@Suppress("UNCHECKED_CAST")
fun <T> Any.getFirstFieldByExactTypeOrNullAs(type: Class<*>?) =
    getFirstFieldByExactTypeOrNull(type) as? T

inline fun <reified T> Any.getFirstFieldByExactTypeOrNull() =
    getFirstFieldByExactTypeOrNull(T::class.java) as? T

// --- ClassLoader utilities ---

inline fun ClassLoader.findDexClassLoader(crossinline delegator: (BaseDexClassLoader) -> BaseDexClassLoader = { x -> x }): BaseDexClassLoader? {
    var classLoader = this
    while (classLoader !is BaseDexClassLoader) {
        if (classLoader.parent != null) classLoader = classLoader.parent
        else return null
    }
    return delegator(classLoader)
}

inline fun ClassLoader.allClassesList(crossinline delegator: (BaseDexClassLoader) -> BaseDexClassLoader = { x -> x }): List<String> {
    return findDexClassLoader(delegator)?.getObjectField("pathList")
        ?.getObjectFieldAs<Array<Any>>("dexElements")
        ?.flatMap {
            it.getObjectField("dexFile")?.callMethodAs<Enumeration<String>>("entries")?.toList()
                .orEmpty()
        }.orEmpty()
}

// --- Member / Class property extensions ---

val Member.isStatic: Boolean
    inline get() = Modifier.isStatic(modifiers)
val Member.isFinal: Boolean
    inline get() = Modifier.isFinal(modifiers)
val Member.isPublic: Boolean
    inline get() = Modifier.isPublic(modifiers)
val Member.isNotStatic: Boolean
    inline get() = !isStatic
val Member.isAbstract: Boolean
    inline get() = Modifier.isAbstract(modifiers)
val Member.isPrivate: Boolean
    inline get() = Modifier.isPrivate(modifiers)
val Class<*>.isAbstract: Boolean
    inline get() = !isPrimitive && Modifier.isAbstract(modifiers)
val Class<*>.isFinal: Boolean
    inline get() = !isPrimitive && Modifier.isFinal(modifiers)
