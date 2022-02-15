package me.iacn.biliroaming.utils

import kotlinx.coroutines.delay

/**
 * V mast be a non-null type
 */
class FutureValue<V> {
    private var v: V? = null

    /**
     * @param limit seconds
     */
    fun getValue(limit: Int): V {
        if (v != null) return v as V
        (0..limit).forEach { _ ->
            Thread.sleep(1000)
            if (v != null) return v as V
        }
        throw RuntimeException("get value timed out")
    }

    /**
     * @param limit seconds
     */
    suspend fun getValueSuspend(limit: Int): V {
        if (v != null) return v as V
        (0..limit).forEach { _ ->
            delay(1000)
            if (v != null) return v as V
        }
        throw RuntimeException("get value timed out")
    }

    fun setValue(v: V) {
        if (v == null)
            this.v = v
        else
            throw RuntimeException("value is already set")
    }
}