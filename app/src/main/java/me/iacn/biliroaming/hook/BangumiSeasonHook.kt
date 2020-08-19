package me.iacn.biliroaming.hook

import android.net.Uri
import de.robv.android.xposed.XC_MethodHook
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.Constant.TYPE_EPISODE_ID
import me.iacn.biliroaming.Constant.TYPE_SEASON_ID
import me.iacn.biliroaming.Protos
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.XposedInit.Companion.toastMessage
import me.iacn.biliroaming.network.BiliRoamingApi
import me.iacn.biliroaming.network.BiliRoamingApi.getSeason
import me.iacn.biliroaming.utils.*
import org.json.JSONObject
import java.lang.reflect.Method
import java.net.URL

/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */

data class Result(val code: Int?, val result: Any?)

class BangumiSeasonHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        val lastSeasonInfo: MutableMap<String, String?> = HashMap()
    }

    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("main_func", false)) return
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

        instance.retrofitResponseClass?.hookBeforeAllConstructors { param ->
            val url = getUrl(param.args[0])
            val body = param.args[1] ?: return@hookBeforeAllConstructors
            // Filter non-bangumi responses
            // If it isn't bangumi, the type variable will not exist in this map
            if (instance.bangumiApiResponseClass?.isInstance(body) == true ||
                    // for new blue 6.3.7
                    instance.rxGeneralResponseClass?.isInstance(body) == true) {
                fixBangumi(body)
            } else if (url != null && url.startsWith("https://app.bilibili.com/x/v2/view") &&
                    body.getIntField("code") == -404) {
                fixView(body, url)
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

        val urlHook = object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                val redirectUrl = param.thisObject.getObjectFieldAs<String?>("redirectUrl")
                if (redirectUrl.isNullOrEmpty()) return
                param.result = param.thisObject.callMethod("getUrl", redirectUrl)
            }
        }
        "com.bilibili.bplus.followingcard.api.entity.cardBean.VideoCard".findClass(mClassLoader)?.hookMethod("getJumpUrl", urlHook)
        "com.bilibili.bplus.followingcard.api.entity.cardBean.VideoCard".findClass(mClassLoader)?.hookMethod("getCommentJumpUrl", urlHook)
    }

    private fun fixBangumi(body: Any) {
        val result = body.getObjectField("result")
        // Filter normal bangumi and other responses
        if (isBangumiWithWatchPermission(body.getIntField("code"), result)) {
            result?.let {
                if (instance.bangumiUniformSeasonClass?.isInstance(it) == true) {
                    allowDownload(it)
                    lastSeasonInfo.clear()
                }
            }
            return
        }
        toastMessage("发现版权番剧，尝试解锁……")
        Log.d("Info: $lastSeasonInfo")
        val content = getSeason(lastSeasonInfo, result == null)
        val (code, newResult) = instance.bangumiUniformSeasonClass?.let { getNewResult(content, "result", it) }
                ?: return

        if (newResult != null && code != null && isBangumiWithWatchPermission(code, newResult)) {
            Log.d("Got new season information from proxy server: $content")
            lastSeasonInfo["title"] = newResult.getObjectFieldAs<String>("title").toString()
            toastMessage("已从代理服务器获取番剧信息")
        } else {
            Log.d("Failed to get new season information from proxy server")
            toastMessage("解锁失败，请重试")
            lastSeasonInfo.clear()
            return
        }
        val newRights = newResult.getObjectField("rights") ?: return
        if (XposedInit.sPrefs.getBoolean("allow_download", false))
            newRights.setBooleanField("allowDownload", true)
        if (result != null) {
            // Replace only episodes and rights
            // Remain user information, such as follow status, watch progress, etc.
            if (!newRights.getBooleanField("areaLimit")) {
                val newEpisodes = newResult.getObjectField("episodes")
                var newModules: Any? = null
                newResult.javaClass.findFieldOrNull("modules")?.let {
                    newModules = newResult.getObjectField("modules")
                }
                result.setObjectField("rights", newRights)
                        .setObjectField("episodes", newEpisodes)
                        .setObjectField("seasonLimit", null)
                result.javaClass.findFieldOrNull("modules")?.let {
                    newModules?.let { it2 ->
                        result.setObjectField(it.name, it2)
                    }
                }
            }
        } else {
            body.setIntField("code", 0)
                    .setObjectField("result", newResult)
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

        val content = BiliRoamingApi.getView(query) ?: return null
        val result = JSONObject(content).getJSONObject("v2_app_api")
        if (!result.has("season")) return null

        return try {
            Protos.ViewReply.newBuilder().run {
                arc = Protos.Arc.newBuilder().run {
                    aid = result.getLong("aid")
                    attribute = result.getInt("attribute")
                    author = Protos.Author.newBuilder().run {
                        val owner = result.getJSONObject("owner")
                        mid = owner.getLong("mid")
                        face = owner.getString("face")
                        name = owner.getString("name")
                        build()
                    }
                    copyright = result.getInt("copyright")
                    ctime = result.getLong("ctime")
                    desc = result.getString("desc")

                    dimension = Protos.Dimension.newBuilder().run {
                        val dimension = result.getJSONObject("dimension")
                        width = dimension.getLong("width")
                        height = dimension.getLong("height")
                        rotate = dimension.getLong("rotate")
                        build()
                    }

                    stat = Protos.Stat.newBuilder().run {
                        val stat = result.getJSONObject("stat")
                        aid = stat.getLong("aid")
                        view = stat.getInt("view")
                        danmaku = stat.getInt("danmaku")
                        reply = stat.getInt("reply")
                        fav = stat.getInt("favorite")
                        coin = stat.getInt("coin")
                        share = stat.getInt("share")
                        nowRank = stat.getInt("now_rank")
                        hisRank = stat.getInt("his_rank")
                        like = stat.getInt("like")
                        dislike = stat.getInt("dislike")
                        build()
                    }
                    duration = result.getLong("duration")
                    firstCid = result.getLong("cid")
                    pic = result.getString("pic")
                    pubdate = result.getLong("pubdate")
                    redirectUrl = result.getString("redirect_url")
                    state = result.getInt("state")
                    title = result.getString("title")
                    typeId = result.getInt("tid")
                    typeName = result.getString("tname")
                    videos = result.getLong("videos")
                    build()
                }
                bvid = result.getString("bvid")
                season = Protos.Season.newBuilder().run {
                    val season = result.getJSONObject("season")
                    allowDownload = "1"
                    seasonId = season.getString("season_id").toLong()
                    title = season.getString("title")
                    cover = season.getString("cover")
                    isFinish = season.getString("is_finish").toInt()
                    newestEpIndex = season.getString("newest_ep_index")
                    newestEpid = season.getString("newest_ep_id").toInt()
                    totalCount = season.getString("total_count").toLong()
                    weekday = season.getString("weekday").toInt()
                    ovgPlayurl = season.getString("ogv_play_url")
                    isJump = season.getInt("is_jump")
                    build()
                }
                val pages = result.getJSONArray("pages")
                for (i in 0 until pages.length()) {
                    addPages(Protos.ViewPage.newBuilder().run {
                        val page = pages.getJSONObject(i)
                        downloadSubtitle = page.getString("download_subtitle")
                        downloadTitle = page.getString("download_title")
                        this.page = Protos.Page.newBuilder().run {
                            cid = page.getLong("cid")
                            this.page = page.getInt("page")
                            from = page.getString("from")
                            part = page.getString("part")
                            duration = page.getLong("duration")
                            vid = page.getString("vid")
                            webLink = page.getString("weblink")
                            dimension = Protos.Dimension.newBuilder().run {
                                val dimension = page.getJSONObject("dimension")
                                width = dimension.getLong("width")
                                height = dimension.getLong("height")
                                rotate = dimension.getLong("rotate")
                                build()
                            }
                            build()
                        }
                        build()
                    })
                }
                shortLink = result.getString("short_link")
                val tags = result.getJSONArray("tag")
                for (i in 0 until tags.length()) {
                    val tag = tags.getJSONObject(i)
                    addTag(Protos.Tag.newBuilder().run {
                        id = tag.getLong("tag_id")
                        name = tag.getString("tag_name")
                        likes = tag.getLong("likes")
                        liked = tag.getInt("liked")
                        hates = tag.getLong("hates")
                        hated = tag.getInt("hated")
                        tagType = tag.getString("tag_type")
                        uri = tag.getString("uri")
                        build()
                    })
                }
                build()
            }
        } catch (e: Throwable) {
            Log.e(e)
            null
        }
    }

    private fun fixView(body: Any, urlString: String) {
        var queryString = urlString.substring(urlString.indexOf("?") + 1)
        queryString = queryString.replace("aid=", "id=")
        val content = BiliRoamingApi.getView(queryString) ?: return
        Log.d("Got view information from proxy server: $content")
        val detailClass = "tv.danmaku.bili.ui.video.api.BiliVideoDetail".findClass(mClassLoader)
                ?: return
        val (_, newResult) = getNewResult(content, "v2_app_api", detailClass)
        body.setIntField("code", 0).setObjectField("data", newResult)
        val bangumiInfo = newResult?.getObjectField("mBangumiInfo")
        bangumiInfo?.let {
            val episodeId = bangumiInfo.getObjectField("mEpId") as String?
            episodeId?.let {
                lastSeasonInfo["ep_id"] = it
                val url = URL(urlString)
                url.query.split("&").filter { it2 ->
                    it2.startsWith("access_key=")
                }.forEach { it2 ->
                    lastSeasonInfo["access_key"] = it2.substring(it2.indexOf("=") + 1)
                }
            }
        }
    }

    private fun isBangumiWithWatchPermission(code: Int, result: Any?): Boolean {
        if (result != null) {
            if (instance.bangumiUniformSeasonClass?.isInstance(result) == true) {
                val rights = result.getObjectField("rights")
                val areaLimit = rights?.getBooleanField("areaLimit")
                return areaLimit == null || !areaLimit
            }
        }
        return code != -404
    }

    private fun getNewResult(content: String?, fieldName: String, beanClass: Class<*>): Result {
        if (content == null) {
            return Result(null, null)
        }
        val contentJson = JSONObject(content)
        val resultJson = contentJson.optJSONObject(fieldName) ?: return Result(null, null)
        val code = contentJson.optInt("code")
        val newResult = instance.fastJsonClass?.callStaticMethod(
                instance.fastJsonParse(), resultJson.toString(), beanClass)
        return Result(code, newResult)
    }

    private fun allowDownload(result: Any?) {
        if (XposedInit.sPrefs.getBoolean("allow_download", false)) {
            try {
                val rights = result?.getObjectField("rights")
                rights?.setBooleanField("allowDownload", true)
                val modules = result?.getObjectField("modules") as MutableList<*>? ?: return
                for (it in modules) {
                    val data = it?.getObjectField("data")
                    val moduleEpisodes = data?.callMethod("getJSONArray", "episodes") ?: continue
                    val size = moduleEpisodes.callMethodAs<Int>("size")
                    for (i in 0 until size) {
                        val episode = moduleEpisodes.callMethod("getJSONObject", i)
                        val episodeRights = episode?.callMethod("getJSONObject", "rights")
                        episodeRights?.callMethod("put", "allow_download", 1)
                    }
                }
            } catch (e: Throwable) {
                Log.e(e)
            }
            Log.d("Download allowed")
            toastMessage("已允许下载")
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