package me.iacn.biliroaming.utils.json

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.callMethod
import me.iacn.biliroaming.utils.findField
import java.lang.reflect.Field

class GsonHelper : JsonHelper {
    lateinit var _data: Any

    override var data: Any
        get() = _data
        set(value) {
            _data = value
        }

    override fun getField(key: String): Field {
        val field = data.javaClass.findField { field ->
            val annotation = field.annotations.find {
                it.toString().startsWith("@com.google.gson.annotations.SerializedName(")
            }
            annotation?.callMethod("value") == key
        } ?: throw NoSuchFieldException("No field found for key: $key in ${data.javaClass.name} or its superclasses")
        return field
    }
}