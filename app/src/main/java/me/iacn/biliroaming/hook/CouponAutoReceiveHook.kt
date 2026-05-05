package me.iacn.biliroaming.hook

import android.widget.Toast
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

class CouponAutoReceiveHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    companion object {
        fun check() {
            if (!sPrefs.getBoolean("auto_receive_coupon", false)) return
            Thread {
                try {
                    val couponInfo = getCouponInfo()
                    val couponSuccessCount = couponInfo?.list?.count { item ->
                        if (item.type <= 7 && item.state != 1 && item.vipType > 0)
                            receiveCoupon(item.type)
                        else false
                    } ?: 0
                    Log.d("CouponAutoReceiveHook.couponSuccessCount: $couponSuccessCount")

                    val experienceSuccessCount = couponInfo?.list?.count { item ->
                        if (item.type == 9 && item.state == 0 && item.vipType > 0)
                            receiveExperience()
                        else false
                    } ?: 0
                    Log.d("CouponAutoReceiveHook.experienceSuccessCount: $experienceSuccessCount")

                    val coupon = if (couponSuccessCount > 0) "${couponSuccessCount}张卡券" else null
                    val vipExp = if (experienceSuccessCount > 0) "大会员每日经验" else null
                    val shareExp = if (receiveShareExperience()) "每日视频分享经验" else null

                    arrayOf(coupon, vipExp, shareExp).filterNotNull().joinToString("、")
                        .takeIf { it.isNotEmpty() }?.let { message ->
                            // 使用B站客户端自己的Toast系统，避免添加"哔哩漫游："前缀
                            instance.toastHelperClass?.runCatchingOrNull {
                                callStaticMethod(instance.cancelShowToast())
                                callStaticMethod(
                                    instance.showToast(),
                                    currentContext,
                                    "${message}自动领取成功！",
                                    Toast.LENGTH_LONG
                                )
                            } ?: run {
                                // 备用：使用系统Toast
                                Log.toast("${message}自动领取成功！", force = true, duration = Toast.LENGTH_LONG)
                            }
                        }
                } catch (e: Throwable) {
                    Log.e(e)
                }
            }.start()
        }

        private fun getCouponInfo(): CouponInfo? {
            val url = "https://api.bilibili.com/x/vip/privilege/my"
            return try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("Cookie", "SESSDATA=${getCookie("SESSDATA")}")
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val json = getStreamContent(connection.inputStream, connection.contentEncoding)?.toJSONObject()
                    json?.optJSONObject("data")?.let { parseCouponInfo(it) }
                } else {
                    null
                }
            } catch (e: Throwable) {
                Log.e(e)
                null
            }
        }

        private fun parseCouponInfo(json: JSONObject): CouponInfo {
            val list = mutableListOf<CouponInfo.Item>()
            val jsonArray = json.optJSONArray("list")
            if (jsonArray != null) {
                for (i in 0 until jsonArray.length()) {
                    val itemJson = jsonArray.optJSONObject(i)
                    itemJson?.let {
                        list.add(CouponInfo.Item(
                            type = it.optInt("type", 0),
                            state = it.optInt("state", 0),
                            nextReceiveDays = it.optLong("next_receive_days", 0),
                            vipType = it.optInt("vip_type", 0)
                        ))
                    }
                }
            }
            return CouponInfo(list)
        }

        private fun receiveCoupon(type: Int): Boolean {
            val url = "https://api.bilibili.com/x/vip/privilege/receive"
            return try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("Cookie", "SESSDATA=${getCookie("SESSDATA")}")
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true

                val body = "type=$type&csrf=${getCookie("bili_jct")}"
                connection.outputStream.use { it.write(body.toByteArray()) }
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val json = getStreamContent(connection.inputStream, connection.contentEncoding)?.toJSONObject()
                    val success = json?.optInt("code", -1) == 0
                    Log.d("CouponAutoReceiveHook.receiveCoupon, type: $type, success: $success")
                    success
                } else {
                    false
                }
            } catch (e: Throwable) {
                Log.e(e)
                false
            }
        }

        private fun receiveExperience(): Boolean {
            val url = "https://api.bilibili.com/x/vip/experience/add"
            return try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("Cookie", "SESSDATA=${getCookie("SESSDATA")}")
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true

                val body = "csrf=${getCookie("bili_jct")}"
                connection.outputStream.use { it.write(body.toByteArray()) }
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val json = getStreamContent(connection.inputStream, connection.contentEncoding)?.toJSONObject()
                    val success = json?.optInt("code", -1) == 0
                    Log.d("CouponAutoReceiveHook.receiveExperience, success: $success")
                    success
                } else {
                    false
                }
            } catch (e: Throwable) {
                Log.e(e)
                false
            }
        }

        private fun receiveShareExperience(): Boolean {
            val oid = "170001"
            val sid = arrayOf(
                "279786", "275431", "279787", "280467", "280468",
                "280469", "274491", "267410", "267714", "270380"
            ).random()

            val query = mapOf(
                "access_key" to (instance.accessKey ?: ""),
                "oid" to oid,
                "panel_type" to "1",
                "share_channel" to "QQ",
                "share_id" to "main.ugc-video-detail.0.0.pv",
                "share_origin" to "vinfo_player",
                "sid" to sid,
                "success" to "true"
            )

            val signedQuery = signQuery(query)
            val url = "https://api.bilibili.com/x/share/finish?$signedQuery"

            return try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val json = getStreamContent(connection.inputStream, connection.contentEncoding)?.toJSONObject()
                    val success = json?.optInt("code", -1) == 0
                            && !json.optJSONObject("data")?.optString("toast").isNullOrEmpty()
                    Log.d("CouponAutoReceiveHook.receiveShareExperience, success: $success")
                    success
                } else {
                    false
                }
            } catch (e: Throwable) {
                Log.e(e)
                false
            }
        }

        private fun getCookie(name: String): String {
            biliPrefs.getString(name, null)?.let { return it }
            blkvPrefs.getString(name, null)?.let { return it }
            return ""
        }

        private fun getStreamContent(input: java.io.InputStream, encoding: String?): String? {
            return try {
                when (encoding?.lowercase()) {
                    "gzip" -> GZIPInputStream(input).bufferedReader().readText()
                    "deflate" -> InflaterInputStream(input).bufferedReader().readText()
                    else -> input.bufferedReader().readText()
                }
            } catch (e: Throwable) {
                Log.e(e)
                null
            }
        }
    }

    override fun startHook() {
        // 此功能不需要Hook，只是提供一个check()方法供调用
    }

    data class CouponInfo(val list: List<Item>) {
        data class Item(
            val type: Int = 0,
            val state: Int = 0,
            val nextReceiveDays: Long = 0,
            val vipType: Int = 0
        )
    }
}