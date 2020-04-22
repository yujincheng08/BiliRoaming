package me.iacn.biliroaming.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Created by iAcn on 2019/3/26
 * Email i@iacn.me
 */
object StreamUtils {
    @JvmStatic
    fun getContent(input: InputStream?, encoding: String?): String? {
        var inputStream = input;
        var result: String? = null
        var byteArrayStream: ByteArrayOutputStream? = null
        try {
            if ("gzip".equals(encoding, ignoreCase = true)) inputStream = GZIPInputStream(inputStream)
            val buffer = ByteArray(2048)
            byteArrayStream = ByteArrayOutputStream()
            var len: Int
            while (inputStream!!.read(buffer).also { len = it } > 0) {
                byteArrayStream.write(buffer, 0, len)
            }
            byteArrayStream.flush()
            result = String(byteArrayStream.toByteArray())
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                inputStream?.close()
                byteArrayStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return result
    }
}