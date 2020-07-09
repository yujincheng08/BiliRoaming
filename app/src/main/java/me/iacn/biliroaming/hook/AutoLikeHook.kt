package me.iacn.biliroaming.hook

import android.app.AndroidAppHelper
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.*
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.Log

class AutoLikeHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    val likedVideos = HashSet<Long>()

    override fun startHook() {
        if(!XposedInit.sPrefs!!.getBoolean("auto_like", false)) return

        Log.d("startHook: auto like")

        val context = AndroidAppHelper.currentApplication()
        val likeId = context.resources.getIdentifier("frame1", "id", context.packageName)
        val instance = instance!!

        findAndHookMethod(instance.section(), "a", Object::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val sec = param.thisObject ?: return
                val detail = getObjectField(sec, instance.videoDetailName())
                val avid = getLongField(detail, "mAvid")
                if (likedVideos.contains(avid)) return
                likedVideos.add(avid)
                val requestUser = getObjectField(detail, "mRequestUser")
                val like = getIntField(requestUser, "mLike")
                val likeView = sec.javaClass.declaredFields.map {
                    getObjectField(sec, it.name)
                }.filter {
                    View::class.java.isInstance(it)
                }.map {
                    it as View
                }.first {
                    it.id == likeId
                }
                if (like == 0) {
                    callMethod(sec, "onClick", likeView)
                }
            }
        })
    }
}
