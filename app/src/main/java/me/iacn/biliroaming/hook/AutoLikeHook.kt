package me.iacn.biliroaming.hook

import android.view.View
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class AutoLikeHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val likedVideos = HashSet<Long>()

    companion object {
        var detail: Pair<Long, Int>? = null
    }

    override fun startHook() {
        if (!sPrefs.getBoolean("auto_like", false)) return

        Log.d("startHook: AutoLike")

        val likeId = getId("frame_recommend")
        val like1 = getId("frame1")

        instance.sectionClass?.hookAfterAllMethods(instance.likeMethod()) { param ->
            val sec = param.thisObject ?: return@hookAfterAllMethods
            val (aid, like) = detail ?: return@hookAfterAllMethods
            if (likedVideos.contains(aid)) return@hookAfterAllMethods
            likedVideos.add(aid)
            val likeView = sec.javaClass.declaredFields.filter {
                View::class.java.isAssignableFrom(it.type)
            }.firstNotNullOfOrNull {
                sec.getObjectFieldOrNullAs<View>(it.name)?.takeIf { v ->
                    v.id == likeId || v.id == like1
                }
            }
            if (like == 0)
                likeView?.callOnClick()
        }
    }
}
