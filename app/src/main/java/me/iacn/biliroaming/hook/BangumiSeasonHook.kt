package me.iacn.biliroaming.hook

import android.content.Context
import android.net.Uri
import android.os.Build
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.Constant.TYPE_EPISODE_ID
import me.iacn.biliroaming.Constant.TYPE_SEASON_ID
import me.iacn.biliroaming.Protos
import me.iacn.biliroaming.network.BiliRoamingApi
import me.iacn.biliroaming.network.BiliRoamingApi.getContent
import me.iacn.biliroaming.network.BiliRoamingApi.getSeason
import me.iacn.biliroaming.utils.*
import org.json.JSONObject
import java.lang.reflect.Method
import java.net.URL

/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */

class BangumiSeasonHook(classLoader: ClassLoader) : BaseHook(classLoader) {
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

    private fun Any.toJson() = if (isSerializable)
        jsonNonStrict.value?.callMethodAs<String>("stringify", javaClass.getStaticObjectField("Companion")?.callMethod("serializer"), this).toJSONObject()
    else
        instance.fastJsonClass?.callStaticMethodAs<String>("toJSONString", this).toJSONObject()

    private fun Class<*>.fromJson(json: String) = if (isSerializable)
        jsonNonStrict.value?.callMethod("parse", getStaticObjectField("Companion")?.callMethod("serializer"), json)
    else
        instance.fastJsonClass?.callStaticMethod(instance.fastJsonParse(), json, this)

    private fun Class<*>.fromJson(json: JSONObject) = fromJson(json.toString())

    override fun startHook() {
        if (!sPrefs.getBoolean("main_func", false)) return
        Log.d("startHook: BangumiSeason")
        instance.seasonParamsMapClass?.hookAfterAllConstructors { param ->
            @Suppress("UNCHECKED_CAST")
            val paramMap: Map<String, String> = param.thisObject as Map<String, String>
            lastSeasonInfo.clear()
            val seasonMode = when {
                param.args[1] is Int -> param.args[1] as Int
                param.args[3] != "0" -> TYPE_EPISODE_ID
                param.args[2] != "0" -> TYPE_SEASON_ID
                else -> -1
            }
            when (seasonMode) {
                TYPE_SEASON_ID -> lastSeasonInfo["season_id"] = paramMap["season_id"]
                TYPE_EPISODE_ID -> lastSeasonInfo["ep_id"] = paramMap["ep_id"]
                else -> return@hookAfterAllConstructors
            }
            lastSeasonInfo["access_key"] = paramMap["access_key"]
        }

        if (isBuiltIn && is64 && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.e("Not support")
            Log.toast("Android O以下系统不支持64位Xpatch版，请使用32位版")
        } else {
            instance.retrofitResponseClass?.hookBeforeAllConstructors { param ->
                val url = getUrl(param.args[0])
                val body = param.args[1] ?: return@hookBeforeAllConstructors
                // Filter non-bangumi responses
                // If it isn't bangumi, the type variable will not exist in this map
                if (instance.bangumiApiResponseClass?.isInstance(body) == true ||
                        // for new blue 6.3.7
                        instance.rxGeneralResponseClass?.isInstance(body) == true) {
                    fixBangumi(body)
                } else if (url != null && (url.startsWith("https://app.bilibili.com/x/v2/view") ||
                                url.startsWith("https://app.bilibili.com/x/intl/view") ||
                                url.startsWith("https://appintl.biliapi.net/intl/gateway/app/view")) &&
                        body.getIntField("code") == FAIL_CODE) {
                    fixView(body, url)
                } else if (url != null && url.startsWith("https://appintl.biliapi.net/intl/gateway/app/search/type")) {
                    fixPlaySearchType(body, url)
                }
            }
        }

        "com.bapis.bilibili.app.view.v1.ViewMoss".findClassOrNull(mClassLoader)
                ?.hookAfterMethod("view", "com.bapis.bilibili.app.view.v1.ViewReq") { param ->
                    param.result?.let { return@hookAfterMethod }
                    val serializedRequest = param.args[0].callMethodAs<ByteArray>("toByteArray")
                    val req = Protos.ViewReq.parseFrom(serializedRequest)
                    val reply = fixViewProto(req)
                    val serializedReply = reply?.toByteArray() ?: return@hookAfterMethod
                    param.result = (param.method as Method).returnType.callStaticMethod("parseFrom", serializedReply)
                }

        val urlHook: (MethodHookParam) -> Unit = fun(param) {
            val redirectUrl = param.thisObject.getObjectFieldAs<String?>("redirectUrl")
            if (redirectUrl.isNullOrEmpty()) return
            param.result = param.thisObject.callMethod("getUrl", redirectUrl)
        }
        "com.bilibili.bplus.followingcard.api.entity.cardBean.VideoCard".findClass(mClassLoader)?.run {
            hookAfterMethod("getJumpUrl", hooker = urlHook)
            hookAfterMethod("getCommentJumpUrl", hooker = urlHook)
        }

        if (sPrefs.getBoolean("allow_download", false))
            instance.checkBlueClass?.replaceMethod(instance.checkBlue(), Context::class.java) {
                return@replaceMethod false
            }
    }

    private fun fixPlaySearchType(body: Any, url: String) {
        val resultClass = body.getObjectField("data")?.javaClass ?: return
        if (!url.contains("type=7") && !url.contains("type=8")) return
        val newUrl = url.replace("appintl.biliapi.net/intl/gateway/app/", "app.bilibili.com/x/v2/")
        val content = getContent(newUrl)?.toJSONObject()?.optJSONObject("data") ?: return
        val newResult = resultClass.fromJson(content) ?: return
        body.setObjectField("data", newResult)
    }

    private fun fixBangumi(body: Any) {
        val fieldName = if (isSerializable) "data" else "result"
        val result = body.getObjectField(fieldName)
        val code = body.getIntField("code")
        if (instance.bangumiUniformSeasonClass?.isInstance(result) != true && code != FAIL_CODE) return
        val jsonResult = result?.toJson()
        // Filter normal bangumi and other responses
        if (isBangumiWithWatchPermission(jsonResult, code)) {
            jsonResult?.also { allowDownload(it) }
        } else {
            Log.toast("发现版权番剧，尝试解锁……")
            Log.d("Info: $lastSeasonInfo")
            val (newCode, newJsonResult) = getSeason(lastSeasonInfo, result == null)?.toJSONObject()?.let {
                it.optInt("code", FAIL_CODE) to it.optJSONObject("result")
            } ?: FAIL_CODE to null
            if (isBangumiWithWatchPermission(newJsonResult, newCode)) {
                Log.d("Got new season information from proxy server: $newJsonResult")
                lastSeasonInfo["title"] = newJsonResult?.optString("title")
                allowDownload(newJsonResult, false)
            }
            jsonResult?.apply {
                // Replace only episodes and rights
                // Remain user information, such as follow status, watch progress, etc.
                put("rights", newJsonResult?.optJSONObject("rights"))
                put("episodes", newJsonResult?.optJSONArray("episodes"))
                remove("limit")
                put("modules", newJsonResult?.optJSONArray("modules"))
                remove("dialog")
            } ?: newJsonResult
        }?.let {
            body.setIntField("code", 0).setObjectField(fieldName, instance.bangumiUniformSeasonClass?.fromJson(it))
        } ?: run {
            Log.d("Failed to get new season information from proxy server")
            Log.toast("解锁失败，请重试")
            lastSeasonInfo.clear()
        }
    }

    private fun fixViewProto(req: Protos.ViewReq): Protos.ViewReply? {
        val query = Uri.Builder().run {
            appendQueryParameter("id", req.aid.toString())
            appendQueryParameter("bvid", req.bvid.toString())
            appendQueryParameter("from", req.from.toString())
            appendQueryParameter("trackid", req.trackid.toString())
            appendQueryParameter("ad_extra", req.adExtra.toString())
            appendQueryParameter("qn", req.qn.toString())
            appendQueryParameter("fnver", req.fnver.toString())
            appendQueryParameter("fnval", req.fnval.toString())
            appendQueryParameter("force_host", req.forceHost.toString())
            appendQueryParameter("fourk", req.fourk.toString())
            appendQueryParameter("spmid", req.spmid.toString())
            appendQueryParameter("autoplay", req.autoplay.toString())
            instance.accessKey?.let {
                appendQueryParameter("access_key", it)
            }
            build()
        }.query

        val content = BiliRoamingApi.getView(query)?.toJSONObject() ?: return null
        val result = content.optJSONObject("v2_app_api")
        if (result?.has("season") != true) return null

        return Protos.ViewReply.newBuilder().run {
            arc = Protos.Arc.newBuilder().run {
                author = Protos.Author.newBuilder().run {
                    result.optJSONObject("owner")?.run {
                        mid = optLong("mid")
                        face = optString("face")
                        name = optString("name")
                    }
                    build()
                }

                dimension = Protos.Dimension.newBuilder().run {
                    result.optJSONObject("dimension")?.run {
                        width = optLong("width")
                        height = optLong("height")
                        rotate = optLong("rotate")
                    }
                    build()
                }

                stat = Protos.Stat.newBuilder().run {
                    result.optJSONObject("stat")?.run {
                        aid = optLong("aid")
                        view = optInt("view")
                        danmaku = optInt("danmaku")
                        reply = optInt("reply")
                        fav = optInt("favorite")
                        coin = optInt("coin")
                        share = optInt("share")
                        nowRank = optInt("now_rank")
                        hisRank = optInt("his_rank")
                        like = optInt("like")
                        dislike = optInt("dislike")
                    }
                    build()
                }
                result.run {
                    aid = optLong("aid")
                    attribute = optInt("attribute")
                    copyright = optInt("copyright")
                    ctime = optLong("ctime")
                    desc = optString("desc")
                    duration = optLong("duration")
                    firstCid = optLong("cid")
                    pic = optString("pic")
                    pubdate = optLong("pubdate")
                    redirectUrl = optString("redirect_url")
                    state = optInt("state")
                    title = optString("title")
                    typeId = optInt("tid")
                    typeName = optString("tname")
                    videos = optLong("videos")
                }
                build()
            }
            bvid = result.optString("bvid")
            season = Protos.Season.newBuilder().run {
                result.optJSONObject("season")?.run {
                    allowDownload = "1"
                    seasonId = optString("season_id").toLong()
                    title = optString("title")
                    cover = optString("cover")
                    isFinish = optString("is_finish").toInt()
                    newestEpIndex = optString("newest_ep_index")
                    newestEpid = optString("newest_ep_id").toInt()
                    totalCount = optString("total_count").toLong()
                    weekday = optString("weekday").toInt()
                    ovgPlayurl = optString("ogv_play_url")
                    isJump = optInt("is_jump")
                }
                build()
            }
            val pages = result.optJSONArray("pages")
            for (page in pages.orEmpty()) {
                addPages(Protos.ViewPage.newBuilder().run {
                    downloadSubtitle = page.optString("download_subtitle")
                    downloadTitle = page.optString("download_title")
                    this.page = Protos.Page.newBuilder().run {
                        this.page = page.optInt("page")
                        page.run {
                            cid = optLong("cid")
                            from = optString("from")
                            part = optString("part")
                            duration = optLong("duration")
                            vid = optString("vid")
                            webLink = optString("weblink")
                        }
                        dimension = Protos.Dimension.newBuilder().run {
                            page.optJSONObject("dimension")?.run {
                                width = optLong("width")
                                height = optLong("height")
                                rotate = optLong("rotate")
                            }
                            build()
                        }
                        build()
                    }
                    build()
                })
            }
            shortLink = result.optString("short_link")
            result.optJSONObject("t_icon")?.let {
                for (key in it.keys()) {
                    val icon = it.optJSONObject(key)?.optString("icon")
                    putTIcon(key, Protos.TIcon.newBuilder().setIcon(icon).build())
                }
            }
            val tags = result.optJSONArray("tag")
            for (tag in tags.orEmpty()) {
                addTag(Protos.Tag.newBuilder().run {
                    tag.run {
                        id = optLong("tag_id")
                        name = optString("tag_name")
                        likes = optLong("likes")
                        liked = optInt("liked")
                        hates = optLong("hates")
                        hated = optInt("hated")
                        tagType = optString("tag_type")
                        uri = optString("uri")
                    }
                    build()
                })
            }
            build()
        }
    }

    private fun fixView(body: Any, urlString: String) {
        var queryString = urlString.substring(urlString.indexOf("?") + 1)
        queryString = queryString.replace("aid=", "id=")
        val content = BiliRoamingApi.getView(queryString) ?: return
        Log.d("Got view information from proxy server: $content")
        val detailClass = "tv.danmaku.bili.ui.video.api.BiliVideoDetail".findClass(mClassLoader)
                ?: return
        val newJsonResult = content.toJSONObject().optJSONObject("v2_app_api") ?: return
        newJsonResult.optJSONObject("season")?.optString("newest_ep_id")?.let {
            lastSeasonInfo["ep_id"] = it
            val url = URL(urlString)
            url.query.split("&").filter { it2 ->
                it2.startsWith("access_key=")
            }.forEach { it2 ->
                lastSeasonInfo["access_key"] = it2.substring(it2.indexOf("=") + 1)
            }
        }
        body.setIntField("code", 0).setObjectField("data", detailClass.fromJson(newJsonResult))
    }

    private fun isBangumiWithWatchPermission(result: JSONObject?, code: Int) = result?.let {
        result.optJSONObject("rights")?.run { !optBoolean("area_limit", true) || optInt("area_limit", 1) == 0 }
    } ?: run { code != FAIL_CODE }


    private fun allowDownload(result: JSONObject?, toast: Boolean = true) {
        if (sPrefs.getBoolean("allow_download", false)) {
            val rights = result?.optJSONObject("rights")
            rights?.put("allow_download", 1)
            for (module in result?.optJSONArray("modules").orEmpty()) {
                val data = module.optJSONObject("data")
                val moduleEpisodes = data?.optJSONArray("episodes")
                for (episode in moduleEpisodes.orEmpty()) {
                    episode.optJSONObject("rights")?.put("allow_download", 1)
                }
            }
            for (episode in result?.optJSONArray("episodes").orEmpty()) {
                episode.optJSONObject("rights")?.put("allow_download", 1)
            }
            Log.d("Download allowed")
            if (toast) Log.toast("已允许下载")
        }
    }

    private fun getUrl(response: Any): String? {
        val requestField = instance.requestField()
        val urlMethod = instance.urlMethod()
        if (requestField == null || urlMethod == null) {
            return null
        }
        val request = response.getObjectField(requestField)
        return try {
            request?.callMethod(urlMethod)?.toString()
        } catch (e: Throwable) {
            null
        }
    }

}