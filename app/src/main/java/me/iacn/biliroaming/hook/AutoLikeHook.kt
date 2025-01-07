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

        val likeIds = arrayOf(
            "frame_recommend",
            "frame1",
            "frame_like"
        ).map { getId(it) }

        instance.likeMethod()?.let { likeMethod ->
            instance.sectionClass?.hookAfterAllMethods(likeMethod) { param ->
                val sec = param.thisObject ?: return@hookAfterAllMethods
                if (!shouldClickLike()) {
                    return@hookAfterAllMethods
                }
                val likeView = sec.javaClass.declaredFields.filter {
                    View::class.java.isAssignableFrom(it.type)
                }.firstNotNullOfOrNull {
                    sec.getObjectFieldOrNullAs<View>(it.name)?.takeIf { v ->
                        v.id in likeIds
                    }
                }
                likeView?.callOnClick()
            }
        }
        instance.bindViewMethod()?.let { bindViewMethod ->
            instance.sectionClass?.hookAfterMethod(
                bindViewMethod,
                instance.viewHolderClass,
                instance.continuationClass
            ) { param ->
                if (!shouldClickLike()) {
                    return@hookAfterMethod
                }
                val root = param.args[0].callMethodAs<View>(instance.getRootMethod())
                val likeView = likeIds.firstNotNullOfOrNull { id ->
                    root.findViewById(id)
                }
                likeView?.callOnClick()
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
