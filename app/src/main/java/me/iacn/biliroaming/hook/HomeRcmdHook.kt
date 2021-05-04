package me.iacn.biliroaming.hook

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.network.BiliRoamingApi
import me.iacn.biliroaming.network.BiliRoamingApi.getContent
import me.iacn.biliroaming.utils.*
import org.json.JSONObject
import java.lang.reflect.Method
import java.net.URL
import java.net.URLDecoder
import java.lang.reflect.Array as RArray

/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */

class HomeRcmdHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        val lastSeasonInfo: MutableMap<String, String?> = HashMap()

        private val jsonNonStrict = lazy {
            instance.kotlinJsonClass?.getStaticObjectField("Companion")?.callMethod("getNonstrict")
        }
        const val FAIL_CODE = -404
    }

    private val isSerializable by lazy {
        "com.bilibili.bangumi.data.page.detail.entity.BangumiUniformSeason\$\$serializer".findClassOrNull(mClassLoader) != null
    }

    private val isGson by lazy {
        instance.bangumiUniformSeasonClass?.annotations?.fold(false) { last, it ->
            last || it.annotationClass.java.name.startsWith("gsonannotator")
        } ?: false && instance.gsonFromjson() != null && instance.gsonTojson() != null
    }

    private val gson by lazy {
        instance.gson()?.let { instance.gsonConverterClass?.getStaticObjectField(it) }
    }

    private val serializerFeatures = lazy {
        val serializerFeatureClass = "com.alibaba.fastjson.serializer.SerializerFeature".findClass(mClassLoader)
                ?: return@lazy null
        val keyAsString = serializerFeatureClass.getStaticObjectField("WriteNonStringKeyAsString")
        val noDefault = serializerFeatureClass.getStaticObjectField("NotWriteDefaultValue")
        val serializerFeatures = RArray.newInstance(serializerFeatureClass, 2)
        RArray.set(serializerFeatures, 0, keyAsString)
        RArray.set(serializerFeatures, 1, noDefault)
        serializerFeatures
    }

    private fun Any.toJson() = when {
        isSerializable -> jsonNonStrict.value?.callMethodAs<String>("stringify", javaClass.getStaticObjectField("Companion")?.callMethod("serializer"), this).toJSONObject()
        isGson -> gson?.callMethodAs<String>(instance.gsonTojson(), this)?.toJSONObject()
        else -> instance.fastJsonClass?.callStaticMethodAs<String>("toJSONString", this, serializerFeatures.value).toJSONObject()
    }

    private fun Class<*>.fromJson(json: String) = when {
        isSerializable -> jsonNonStrict.value?.callMethod("parse", getStaticObjectField("Companion")?.callMethod("serializer"), json)
        isGson -> gson?.callMethod(instance.gsonFromjson(), json, this)
        else -> instance.fastJsonClass?.callStaticMethod(instance.fastJsonParse(), json, this)
    }

    private fun Class<*>.fromJson(json: JSONObject) = fromJson(json.toString())

    override fun startHook() {
        if (!sPrefs.getBoolean("purify_home_rcmd", false)) return
        Log.d("startHook: IndexRcmd")

        if (isBuiltIn && is64 && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.e("Not support")
            Log.toast("Android O以下系统不支持64位Xpatch版，请使用32位版")
        } else {
            instance.retrofitResponseClass?.hookBeforeAllConstructors { param ->
                val url = getUrl(param.args[0])
                val body = param.args[1] ?: return@hookBeforeAllConstructors
                if (url != null && url.startsWith("https://app.bilibili.com/x/v2/feed/index") && !url.contains("/converge") && !url.contains("/tab") && !url.contains("/story")) {
                    removeIndexAds(body)
                }
            }
        }
    }

    /**
     * 只能这么改
     * 因为即使是
     * ```kotlin
     * val dataClass = body.getObjectField("data").javaClass
     * val dataJson = body.getObjectField("data").toJson()
     * body.setObjectField("data", dataClass.fromJson(dataJson)
     * ```
     * 也会导致崩溃
     */
    private fun removeIndexAds(body: Any) {
        body.getObjectField("data")?.getObjectFieldAs<ArrayList<Any>>("items")?.apply {
            val old = size
            removeAll {
                "ad" in (it.getObjectFieldAs("cardGoto") ?: "")
            }
            if (old - size > 0){
                Log.toast("移除广告 x${old - size}")
            }
        }
        body.getObjectField("data")?.getObjectFieldAs<ArrayList<Any>>("items")?.apply {
            removeAll {
                "large_cover_v6" in (it.getObjectFieldAs("cardType") ?: "") || 
                "large_cover_v9" in (it.getObjectFieldAs("cardType") ?: "")
            }
        }
    }

    private fun getUrl(response: Any): String? {
        val requestField = instance.requestField() ?: return null
        val urlField = instance.urlField() ?: return null
        val request = response.getObjectField(requestField)
        return request?.getObjectField(urlField)?.toString()
    }

}
