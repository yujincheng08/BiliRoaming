package me.iacn.biliroaming.hook

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import me.iacn.biliroaming.utils.*
import java.io.File

class CoverHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("get_cover", false)) return
        Log.d("startHook: GetCover")
        arrayOf(bgmClass, ugcClass, liveClass).forEach {
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
                    else -> if (liveClass?.isInstance(param.thisObject) == true) {
                        val viewModelField = activity.javaClass.declaredFields.firstOrNull { it.type.name == "com.bilibili.bililive.videoliveplayer.ui.roomv3.player.LiveRoomPlayerViewModel" }
                        val roomDataField = "com.bilibili.bililive.videoliveplayer.ui.roomv3.base.viewmodel.LiveRoomBaseViewModel".findClassOrNull(mClassLoader)?.declaredFields?.firstOrNull { it.type.name == "com.bilibili.bililive.videoliveplayer.ui.roomv3.base.viewmodel.LiveRoomData" }
                        val essentialInfoField = roomDataField?.type?.declaredFields?.firstOrNull { it.type.name == "com.bilibili.bililive.videoliveplayer.net.beans.gateway.roominfo.BiliLiveRoomEssentialInfo" }
                        activity.getObjectField(viewModelField?.name)?.getObjectField(roomDataField?.name)?.getObjectField(essentialInfoField?.name)?.run {
                            url = getObjectFieldAs("cover")
                            filename = "live${getObjectField("roomId").toString()}"
                        }
                    }
                }

                Log.toast("开始获取封面", true)
                getBitmapFromURL(url) { bitmap ->
                    bitmap?.let {
                        val relativePath = "${Environment.DIRECTORY_PICTURES}${File.separator}bilibili"
                        @Suppress("DEPRECATION")
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                            } else {
                                val path = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "bilibili")
                                path.mkdirs()
                                put(MediaStore.MediaColumns.DATA, File(path, "$filename.png").absolutePath)
                            }
                        }
                        try {
                            val resolver = activity.contentResolver
                            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                            val stream = resolver.openOutputStream(uri!!)
                            it.compress(Bitmap.CompressFormat.PNG, 100, stream)
                            Log.toast("保存封面成功到\n$relativePath${File.separator}$filename.png", true)
                            stream?.close()
                        } catch (e: Throwable) {
                            Log.e(e)
                            Log.toast("获取封面失败", true)
                        }
                    } ?: run {
                        Log.toast("获取封面失败", true)
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
        val liveId = activity.resources.getIdentifier("controller_underlay", "id", activity.packageName)
        activity.findViewById<View>(liveId)?.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    val bgmClass by Weak { "com.bilibili.bangumi.ui.page.detail.playerV2.BangumiPlayerFragmentV2".findClass(mClassLoader) }
    val ugcClass by Weak { "tv.danmaku.bili.ui.video.playerv2.UgcPlayerFragment".findClass(mClassLoader) }
    val liveClass by Weak { "com.bilibili.bililive.blps.core.business.player.container.AbsLivePlayerFragment".findClass(mClassLoader) }

}