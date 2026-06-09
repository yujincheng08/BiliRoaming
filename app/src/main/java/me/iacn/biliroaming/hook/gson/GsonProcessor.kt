package me.iacn.biliroaming.hook.gson

interface GsonProcessor {
    /** Full class name of the target deserialized type */
    val targetClassName: String

    /** Return true if this processor's settings are enabled */
    fun shouldEnable(): Boolean

    /** Process the deserialized result object */
    fun process(result: Any)
}