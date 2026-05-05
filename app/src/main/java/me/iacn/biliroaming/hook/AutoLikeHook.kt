package me.iacn.biliroaming.hook

import android.view.View
import android.widget.LinearLayout
import io.github.libxposed.api.XposedInterface.HookHandle
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class AutoLikeHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val likedVideos = HashSet<Long>()

    companion object {
        var detail: Pair<Long, Int>? = null
    }

    private val likeIds by lazy {
        arrayOf(
            "frame_like",
            "like_layout"
        ).map { getId(it) }
    }

    override fun startHook() {
        if (!sPrefs.getBoolean("auto_like", false)) return

        Log.d("startHook: AutoLike")

        val unhookSet = arrayOf<Set<HookHandle>?>(null)
        unhookSet[0] = instance.kingPositionComponentClass?.hookAllConstructors { chain ->
            chain.proceed()
            val kingPositionComponent = chain.thisObject
            val componentMap =
                kingPositionComponent!!.getObjectFieldAs<Map<*, *>>(instance.componentMapField())

            val likeComponentClass = componentMap.keys.filterNotNull().firstNotNullOfOrNull {
                val componentClass = it.javaClass
                componentClass.declaredFields.firstOrNull {
                    it.type == Runnable::class.java
                } ?: return@firstNotNullOfOrNull null
                componentClass
            } ?: return@hookAllConstructors null

            val rebindView = likeComponentClass.declaredMethods.firstOrNull {
                it.returnType == Void.TYPE && it.parameterCount == 5
                        && it.parameterTypes[0] == likeComponentClass
                        && it.parameterTypes[4] == LinearLayout::class.java
            } ?: return@hookAllConstructors null

            rebindView.hookMethod { p ->
                val result = p.proceed()
                performLike(p.args[4] as View)
                result
            }

            unhookSet[0]?.forEach { it.unhook() }
            null
        }

        // 番剧分集切换
        instance.viewUniteMossClass?.hookMethod(
            "arcRefresh",
            "com.bapis.bilibili.app.viewunite.v1.ArcRefreshReq",
            instance.mossResponseHandlerClass
        ) { chain ->
            val args = chain.args.toTypedArray()
            val req = args[0]!!
            val aid = req.callMethodAs("getAid")
                ?: req.callMethodAs<String?>("getBvid")?.let { bv2av(it) }
            if (aid == null || aid <= 0L) {
                return@hookMethod chain.proceed()
            }
            args[1] = args[1]!!.mossResponseHandlerReplaceProxy { reply ->
                reply ?: return@mossResponseHandlerReplaceProxy null
                val like = reply.callMethod("getReqUser")?.callMethodAs<Int?>("getLike")
                    ?: return@mossResponseHandlerReplaceProxy null
                detail = aid to like
                null
            }
            chain.proceed(args)
        }

        // 竖屏模式
        instance.storyAbsControllerClass?.hookMethod(
            instance.setMDataMethod(),
            instance.storyDetailClass
        ) { chain ->
            val result = chain.proceed()
            if (chain.thisObject!!.callMethod(instance.getMPlayerMethod()) == null) {
                return@hookMethod result
            }
            val storyDetail = instance.fastJsonClass?.callStaticMethodAs<String>(
                "toJSONString",
                chain.args[0]
            ).toJSONObject()
            val playerArgs = storyDetail.optJSONObject("player_args")
            val aid = playerArgs?.optLong("aid")
            val reqUser = storyDetail.optJSONObject("req_user")
            val like = reqUser?.optBoolean("like")
            if (aid == null || like == null) {
                return@hookMethod result
            }
            detail = aid to if (like) 1 else 0

            performLike(chain.thisObject as View)
            result
        }
    }

    private fun performLike(root: View) {
        val likeView = likeIds.firstNotNullOfOrNull {
            root.findViewById(it)
        }
        likeView?.post {
            if (shouldClickLike()) {
                likeView.callOnClick()
            }
        }
    }

    private fun shouldClickLike(): Boolean {
        val (aid, like) = detail ?: return false
        if (likedVideos.contains(aid) || like != 0) {
            return false
        }
        likedVideos.add(aid)
        return true
    }
}
