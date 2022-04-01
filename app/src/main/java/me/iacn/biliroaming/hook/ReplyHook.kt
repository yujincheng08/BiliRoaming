package me.iacn.biliroaming.hook

import android.os.Build
import java.lang.reflect.Array
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.iacn.biliroaming.utils.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.network.BiliRoamingApi.checkUpReply

class ReplyHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val jsonNonStrict = lazy {
        instance.kotlinJsonClass?.getStaticObjectField("Companion")?.callMethod("getNonstrict")
    }
    private val isSerializable by lazy {
        "com.bilibili.bangumi.data.page.detail.entity.BangumiUniformSeason\$\$serializer".findClassOrNull(
            mClassLoader
        ) != null
    }
    private val isGson by lazy {
        instance.bangumiUniformSeasonClass?.annotations?.fold(false) { last, it ->
            last || it.annotationClass.java.name.startsWith("gsonannotator")
        } ?: false && instance.gsonFromJson() != null && instance.gsonToJson() != null
    }
    private val gson by lazy {
        instance.gson()?.let { instance.gsonConverterClass?.getStaticObjectField(it) }
    }
    private val serializerFeatures = lazy {
        val serializerFeatureClass =
            "com.alibaba.fastjson.serializer.SerializerFeature".findClassOrNull(mClassLoader)
                ?: return@lazy null
        val keyAsString = serializerFeatureClass.getStaticObjectField("WriteNonStringKeyAsString")
        val noDefault = serializerFeatureClass.getStaticObjectField("NotWriteDefaultValue")
        val serializerFeatures = Array.newInstance(serializerFeatureClass, 2)
        Array.set(serializerFeatures, 0, keyAsString)
        Array.set(serializerFeatures, 1, noDefault)
        serializerFeatures
    }
    private fun Any.toJson() = when {
        isSerializable -> jsonNonStrict.value?.callMethodAs<String>(
            "stringify",
            javaClass.getStaticObjectField("Companion")?.callMethod("serializer"),
            this
        ).toJSONObject()
        isGson -> gson?.callMethodAs<String>(instance.gsonToJson(), this)?.toJSONObject()
        else -> instance.fastJsonClass?.callStaticMethodAs<String>(
            "toJSONString",
            this,
            serializerFeatures.value
        ).toJSONObject()
    }
    override fun startHook() {
        if (!sPrefs.getBoolean("check_reply", false)) return
        Log.d("startHook: Reply")
        if (isBuiltIn && is64 && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.e("Not support")
            Log.toast("Android O以下系统不支持64位Xpatch版，请使用32位版")
        } else {
            instance.retrofitResponseClass?.hookBeforeAllConstructors { param ->
                val url = getUrl(param.args[0])
                val body = param.args[1] ?: return@hookBeforeAllConstructors
                if (url != null) {
                    if (url.startsWith("https://api.bilibili.com/x/v2/reply/add")) {
                        val rpid = body.getObjectField("data")?.getLongField("rpid")
                        if (rpid != 0L){
                            Log.d("ReplyHook rpid: $rpid ")
                            MainScope().launch {
                                delay(1000)
                                val code = checkUpReply(rpid.toString()).toJSONObject().getInt("code")
                                if (code !=0){
                                    Log.e("ReplyHook reply return code: $code")
                                    Log.toast("你刚刚的评论已被阿瓦隆屏蔽")
                                }
                            }
                        }
                    }
                }
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