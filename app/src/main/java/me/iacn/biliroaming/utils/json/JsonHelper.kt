package me.iacn.biliroaming.utils.json

import java.lang.reflect.Field

interface JsonHelper {
    var data: Any
    fun getField(key: String): Field
    fun getObject(key: String): Any? {
        return getField(key).get(data)
    }

    fun getObjectAsHelper(key: String): JsonHelper {
        return getObject(key)!!.toJsonHelper(this.javaClass)
    }

    fun setObject(key: String, value: Any) {
        getField(key).set(data, value)
    }
}

fun <T> JsonHelper.getObjectAs(key: String): T {
    return getObject(key) as T
}

fun JsonHelper.getObjectOrNull(key: String): Any? {
    return try {
        getObject(key)
    } catch (e: NoSuchFieldException) {
        null
    }
}

fun JsonHelper.getObjectAsHelperOrNull(key: String): JsonHelper? {
    return try {
        getObjectAsHelper(key)
    } catch (e: NoSuchFieldException) {
        null
    }
}

inline fun <reified T : JsonHelper> Any.toJsonHelper(): T {
    val jsonHelper = T::class.java.newInstance() as T
    jsonHelper.data = this
    return jsonHelper
}

fun Any.toJsonHelper(jsonHelper: Class<JsonHelper>): JsonHelper {
    return jsonHelper.newInstance().apply {
        data = this@toJsonHelper
    }
}
