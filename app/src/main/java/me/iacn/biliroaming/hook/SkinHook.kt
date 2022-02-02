package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Array as RArray

/**
 * Created by david on 2022/2/1
 * Email ？
 */

class SkinHook (classLoader: ClassLoader) : BaseHook(classLoader) {
    // （这里开始）直接抄 BangumiSeasonHook 的内容
    companion object {
        private val jsonNonStrict = lazy {
            instance.kotlinJsonClass?.getStaticObjectField("Companion")?.callMethod("getNonstrict")
        }
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
        val serializerFeatures = RArray.newInstance(serializerFeatureClass, 2)
        RArray.set(serializerFeatures, 0, keyAsString)
        RArray.set(serializerFeatures, 1, noDefault)
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

    private fun Class<*>.fromJson(json: String) = when {
        isSerializable -> jsonNonStrict.value?.callMethod(
            "parse",
            getStaticObjectField("Companion")?.callMethod("serializer"),
            json
        )
        isGson -> gson?.callMethod(instance.gsonFromJson(), json, this)
        else -> instance.fastJsonClass?.callStaticMethod(instance.fastJsonParse(), json, this)
    }

    private fun Class<*>.fromJson(json: JSONObject) = fromJson(json.toString())
    // （这里结束）直接抄 BangumiSeasonHook 的内容

    override fun startHook() {
        // 判断设置打开了吗？
        if (!sPrefs.getBoolean("skin_import", false) && !sPrefs.getBoolean("skin", false)) return
        Log.d("startHook: Skin")
        // 这里也是直接抄 BangumiSeasonHook 的内容
        instance.retrofitResponseClass?.hookBeforeAllConstructors { param ->
            val url = getUrl(param.args[0]) ?: return@hookBeforeAllConstructors
            val body = param.args[1] ?: return@hookBeforeAllConstructors
            if (instance.generalResponseClass?.isInstance(body) == true ||
                instance.rxGeneralResponseClass?.isInstance(body) == true
            ) {
                // 判断网址要不要处理
                if (url.startsWith("https://app.bilibili.com/x/resource/show/skin?")) {
                    val dataField =
                        if (instance.generalResponseClass?.isInstance(body) == true) "data" else instance.responseDataField().value
                    val resultClass = body.getObjectField(dataField)?.javaClass
                    try {
                        var skin = 
                        if (sPrefs.getBoolean("skin_import", false)) {
                            // 从 导入 获取
                            File(currentContext.filesDir, "../files/skin.json")
                                .readText().toJSONObject().putOpt("package_md5","")
                                .toString().replace(
                                    """package_md5":""""", """package_md5":null"""
                                )
                        } else if (sPrefs.getBoolean("skin", false)) {
                            // 从 填写 获取
                            sPrefs.getString("skin_json", "")?.toJSONObject().toString()
                        }
                        // 准备替换内容
                        val content = """{"user_equip":""" + skin +
                                ""","skin_colors":[{"id":8,"name":"简洁白","is_free":true,"color_name":"white"},{"id":2,"name":"少女粉","is_free":true,"color_name":"pink"},{"id":1,"name":"主题黑","is_free":true,"color_name":"black"},{"id":3,"name":"高能红","is_free":true,"color_name":"red"},{"id":4,"name":"咸蛋黄","is_free":true,"color_name":"yellow"},{"id":5,"name":"早苗绿","is_free":true,"color_name":"green"},{"id":6,"name":"宝石蓝","is_free":true,"color_name":"blue"},{"id":7,"name":"罗兰紫","is_free":true,"color_name":"purple"}]}"""
                        val newResult = resultClass?.fromJson(content)
                        // 替换内容
                        body.setObjectField(dataField, newResult)
                    } catch (e: Exception) {
                        Log.toast("发生错误，请关闭自制主题设置", true)
                        e.printStackTrace()
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
