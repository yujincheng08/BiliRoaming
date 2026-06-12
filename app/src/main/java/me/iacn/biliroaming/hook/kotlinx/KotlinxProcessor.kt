package me.iacn.biliroaming.hook.kotlinx

interface KotlinxProcessor {
    /** Serial descriptor name matching `serializer.descriptor.serialName` */
    val targetSerialName: String

    /** Return true if this processor's settings are enabled */
    fun shouldEnable(): Boolean

    /** Process the deserialized result object */
    fun process(result: Any)
}
