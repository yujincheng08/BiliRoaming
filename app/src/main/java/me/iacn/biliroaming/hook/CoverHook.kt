package me.iacn.biliroaming.hook

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import me.iacn.biliroaming.BiliBiliPackage.Weak
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.XposedInit.Companion.toastMessage
import me.iacn.biliroaming.utils.*
import java.io.File
import java.io.FileOutputStream

class CoverHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("get_cover", false)) return
        Log.d("startHook: GetCover")
        arrayOf(bgmClass, ugcClass).forEach {
            it?.hookAfterMethod("onViewCreated", View::class.java, Bundle::class.java, hooker = hooker)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    val hooker = fun(param: XC_MethodHook.MethodHookParam) {
        val group = param.args[0] as ViewGroup
        val activity = param.thisObject.callMethodAs<Activity>("getActivity")

        val gestureDetector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent?) {
                var url: String? = null
                var filename: String? = null
                when (param.thisObject.javaClass) {
                    bgmClass -> activity.run {
                        javaClass.declaredFields.firstOrNull { it.type.name == "com.bilibili.bangumi.data.page.detail.entity.BangumiUniformSeason" }?.let {
                            url = getObjectField(it.name)?.getObjectFieldAs("cover")
                            filename = "s${getObjectField(it.name)?.getObjectField("seasonId").toString()}"
                        }
                    }
                    ugcClass -> activity.run {
                        javaClass.declaredFields.firstOrNull { it.type.name == "tv.danmaku.bili.ui.video.api.BiliVideoDetail" }?.let {
                            url = getObjectField(it.name)?.getObjectFieldAs("mCover")
                            filename = "av${getObjectField(it.name)?.getObjectField("mAvid").toString()}"
                        }
                    }
                }

                toastMessage("开始获取封面")
                getBitmapFromURL(url) { bitmap ->
                    bitmap?.let {
                        val path = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                        val file = File(path, "$filename.png")
                        val out = FileOutputStream(file)
                        it.compress(Bitmap.CompressFormat.PNG, 100, out)
                        out.close()
                        toastMessage("封面已保存到${file.absolutePath}")
                    } ?: run {
                        toastMessage("获取封面失败")
                        return@getBitmapFromURL
                    }
                }
            }
        })

        for (i in 0 until group.childCount) {
            val view = group.getChildAt(i)
            if (view.javaClass.name.startsWith("tv.danmaku.biliplayerv2.widget.gesture")) {
                view.setOnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)
                    false
                }
            }
        }
    }

    val bgmClass by Weak { "com.bilibili.bangumi.ui.page.detail.playerV2.BangumiPlayerFragmentV2".findClass(mClassLoader) }
    val ugcClass by Weak { "tv.danmaku.bili.ui.video.playerv2.UgcPlayerFragment".findClass(mClassLoader) }

}