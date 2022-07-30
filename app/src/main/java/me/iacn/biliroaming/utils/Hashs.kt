package me.iacn.biliroaming.utils

import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest

private val HEX_DIGITS =
    charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

fun ByteArray.toHexString(): String {
    val hexDigits = HEX_DIGITS
    val len = size
    if (len <= 0) return ""
    val ret = CharArray(len shl 1)
    var i = 0
    var j = 0
    while (i < len) {
        ret[j++] = hexDigits[this[i].toInt() shr 4 and 0x0f]
        ret[j++] = hexDigits[this[i].toInt() and 0x0f]
        i++
    }
    return String(ret)
}

private fun hashFile(file: File, algorithm: String): ByteArray? {
    return try {
        file.inputStream().use { fis ->
            val md = MessageDigest.getInstance(algorithm)
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            DigestInputStream(fis, md).use {
                while (true) {
                    if (it.read(buffer) == -1)
                        break
                }
            }
            md.digest()
        }
    } catch (_: Exception) {
        null
    }
}

val File.sha256sum: String
    get() = hashFile(this, "SHA256")?.toHexString() ?: ""