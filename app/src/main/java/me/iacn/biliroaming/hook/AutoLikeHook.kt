package me.iacn.biliroaming.hook

import android.app.AndroidAppHelper
import android.view.View
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.*

class AutoLikeHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val likedVideos = HashSet<Long>()

    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("auto_like", false)) return

        Log.d("startHook: auto like")

        val context = AndroidAppHelper.currentApplication()
        val likeId = context.resources.getIdentifier("frame1", "id", context.packageName)

        instance.sectionClass?.hookAfterMethod(instance.likeMethod(), Object::class.java) { param ->
            val sec = param.thisObject ?: return@hookAfterMethod
            val detail = sec.getObjectField(instance.videoDetailName())
            val avid = detail?.getLongField("mAvid") ?: return@hookAfterMethod
            if (likedVideos.contains(avid)) return@hookAfterMethod
            likedVideos.add(avid)
            val requestUser = detail.getObjectField("mRequestUser")
            val like = requestUser?.getIntField("mLike")
            val likeView = sec.javaClass.declaredFields.map {
                sec.getObjectField(it.name)
            }.filter {
                View::class.java.isInstance(it)
            }.map {
                it as View
            }.first {
                it.id == likeId
            }
            if (like == 0) {
                sec.callMethod("onClick", likeView)
            }
        }
    }
}
