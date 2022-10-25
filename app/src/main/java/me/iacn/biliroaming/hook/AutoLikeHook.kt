package me.iacn.biliroaming.hook

import android.view.View
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class AutoLikeHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val likedVideos = HashSet<Long>()

    override fun startHook() {
        if (!sPrefs.getBoolean("auto_like", false)) return

        Log.d("startHook: AutoLike")

        val likeId = getId("frame_recommend")
        val like1 = getId("frame1")
        val detailClass = instance.biliVideoDetailClass ?: return
        var detail: Any? = null

        instance.videoDetailCallbackClass?.hookAfterMethod(
            "onDataSuccess",
            Object::class.java
        ) { param ->
            if (detailClass.isInstance(param.args[0]))
                detail = param.args[0]
        }

        val hooker: Hooker = fun(param) {
            val sec = param.thisObject ?: return
            detail?.let { detail ->
                val avid = detail.getLongField("mAvid")
                if (likedVideos.contains(avid)) return
                likedVideos.add(avid)
                val requestUser = detail.getObjectField("mRequestUser")
                val like = requestUser?.getIntField("mLike")
                val likeView = sec.javaClass.declaredFields.filter {
                    View::class.java.isAssignableFrom(it.type)
                }.firstNotNullOfOrNull {
                    sec.getObjectFieldOrNullAs<View>(it.name)?.takeIf { v ->
                        v.id == likeId || v.id == like1
                    }
                }
                if (like == 0) {
                    likeView?.callOnClick()
                }
            }
        }
        instance.partyLikeMethod()?.let {
            instance.partySectionClass?.hookAfterAllMethods(
                it,
                hooker = hooker
            )
        }

        instance.likeMethod()?.let {
            instance.sectionClass?.hookAfterMethod(
                it,
                Object::class.java,
                hooker = hooker
            )
        }
    }
}
