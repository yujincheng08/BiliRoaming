package me.iacn.biliroaming.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import java.util.concurrent.TimeUnit

class BilibiliSponsorBlock(
    private val bvid: String,
    private val cid: String,
) {
    companion object {
        private const val BASE_URL = "https://bsbsb.top/api/skipSegments/"
        internal val httpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }
        private val sha256Digest by lazy {
            java.security.MessageDigest.getInstance("SHA-256")
        }
    }

    suspend fun getSegments(): List<Segment>? = withContext(Dispatchers.IO) {
        try {
            val prefix = bvid.trim().sha256().take(4)
            val url = "$BASE_URL$prefix?category=sponsor"

            val request = Request.Builder()
                .url(url)
                .header("origin", "BiliRoaming")
                .header("x-ext-version", "1.7.0")
                .build()

            return@withContext httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e("HTTP request failed with code: ${resp.code}")
                    return@use null
                }

                val body = resp.body.string()
                if (body.isEmpty()) {
                    Log.e("HTTP request failed with empty body")
                    return@use null
                }

                parseSegments(body)
            }

        } catch (e: Exception) {
            Log.e(e)
            null
        }
    }

    private fun parseSegments(json: String): List<Segment>? {
        try {
            val jsonArray = JSONArray(json)
            val segments = mutableListOf<Segment>()
            if (jsonArray.length() == 0) return null

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.optString("videoID") != bvid) continue

                val segmentsArray = obj.getJSONArray("segments")
                for (j in 0 until segmentsArray.length()) {
                    val item = segmentsArray.getJSONObject(j)
                    val segmentArray = item.getJSONArray("segment")
                    segments.add(
                        Segment(
                            segment = floatArrayOf(
                                segmentArray.getDouble(0).toFloat(),
                                segmentArray.getDouble(1).toFloat()
                            ),
                            cid = item.optString("cid"),
                            UUID = item.optString("UUID"),
                            category = item.optString("category"),
                            actionType = item.optString("actionType"),
                            videoDuration = item.optInt("videoDuration")
                        )
                    )
                }
                break
            }
            return segments
                .filter { it.cid == this.cid }
                .sortedBy { it.segment[0] }
        } catch (_: JSONException) {
            return null
        }
    }

    private fun String.sha256(): String = sha256Digest
        .digest(toByteArray())
        .joinToString("") { "%02x".format(it) }

    data class Segment(
        val segment: FloatArray,
        val cid: String,
        val UUID: String,
        val category: String,
        val actionType: String,
        val videoDuration: Int
    )
}
