package me.iacn.biliroaming.hook

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import java.io.File

class CoverHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private val gson by lazy {
        instance.gson()?.let { instance.gsonConverterClass?.getStaticObjectField(it) }
    }

    override fun startHook() {
//        if (!sPrefs.getBoolean("get_cover", false)) return
//        Log.d("startHook: GetCover")
//        arrayOf(bgmClass, ugcClass, liveClass).forEach {
//            it?.hookAfterMethod(
//                "onViewCreated",
//                View::class.java,
//                Bundle::class.java,
//                hooker = hooker
//            )
//        }
    }

    @SuppressLint("ClickableViewAccessibility")
    val hooker: Hooker = { param ->
        val group = param.args[0] as ViewGroup
        val activity = param.thisObject.callMethodAs<Activity>("getActivity")

        val gestureDetector =
            GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent?) {
                    var url: String? = null
                    var filename: String? = null
                    var title: String? = null
                    try {
                        when (param.thisObject.javaClass) {
                            bgmClass -> activity.run {
                                val viewModelField =
                                    activity.javaClass.declaredFields.firstOrNull { it.type.name == "com.bilibili.bangumi.logic.page.detail.BangumiDetailViewModelV2" }
                                val episodeMethod =
                                    viewModelField?.type?.declaredMethods?.lastOrNull { it.returnType.name == "com.bilibili.bangumi.data.page.detail.entity.BangumiUniformEpisode" }
                                val episode =
                                    getObjectField(viewModelField?.name)?.callMethod(episodeMethod?.name)
                                val hasGson = episode?.javaClass?.annotations?.fold(false) { last, it ->
                                    last || it.annotationClass.java.name.startsWith("gsonannotator")
                                } ?: false && instance.gsonFromJson() != null && instance.gsonToJson() != null
                                if (hasGson) {
                                    val json = gson?.callMethodAs<String>(instance.gsonToJson(), episode)?.toJSONObject()
                                    url = json?.optString("cover")
                                    filename = "ep${json?.optInt("id")}"
                                    title = json?.optString("share_copy")
                                } else {
                                    url = episode?.getObjectFieldAs("cover")
                                    filename = "ep${episode?.getLongField("epid").toString()}"
                                    title = episode?.getObjectFieldAs("longTitle") ?: ""
                                }
                            }
                            ugcClass -> activity.run {
                                javaClass.declaredFields.firstOrNull { it.type == instance.biliVideoDetailClass }
                                    ?.let {
                                        url = getObjectField(it.name)?.getObjectFieldAs("mCover")
                                        filename = "av${
                                            getObjectField(it.name)?.getObjectField("mAvid")
                                                .toString()
                                        }"
                                        title = getObjectField(it.name)?.getObjectFieldAs("mTitle")
                                    }
                            }
                            else -> if (liveClass?.isInstance(param.thisObject) == true) {
                                val viewModelField = activity.javaClass.declaredFields.firstOrNull {
                                    it.type.name == "com.bilibili.bililive.videoliveplayer.ui.roomv3.base.viewmodel.LiveRoomRootViewModel" ||
                                            it.type.name == "com.bilibili.bililive.room.ui.roomv3.base.viewmodel.LiveRoomRootViewModel"
                                }
                                val roomFeedField =
                                    viewModelField?.type?.declaredFields?.firstOrNull {
                                        it.type.name == "com.bilibili.bililive.videoliveplayer.ui.roomv3.vertical.roomfeed.LiveRoomFeedViewModel" ||
                                                it.type.name == "com.bilibili.bililive.room.ui.roomv3.vertical.roomfeed.LiveRoomFeedViewModel"
                                    }
                                val currentFeedField =
                                    roomFeedField?.type?.declaredFields?.firstOrNull {
                                        it.type.name.startsWith("com.bilibili.bililive.videoliveplayer.ui.roomv3.vertical.roomfeed") ||
                                                it.type.name.startsWith("com.bilibili.bililive.room.ui.roomv3.vertical.roomfeed")
                                    }
                                val roomIdField =
                                    currentFeedField?.type?.declaredFields?.firstOrNull { it.type == Long::class.java }
                                val coverField =
                                    currentFeedField?.type?.declaredFields?.firstOrNull { it.type == String::class.java }
                                val titleField =
                                    currentFeedField?.type?.declaredFields?.lastOrNull { it.type == String::class.java }
                                activity.getObjectField(viewModelField?.name)
                                    ?.getObjectField(roomFeedField?.name)
                                    ?.getObjectField(currentFeedField?.name)?.run {
                                    url = getObjectFieldAs(coverField?.name)
                                    filename = "live${getObjectField(roomIdField?.name).toString()}"
                                    title = getObjectFieldAs(titleField?.name)
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        Log.e(e)
                    }

                    Log.toast("开始获取封面", true)
                    getBitmapFromURL(url) { bitmap ->
                        bitmap?.let {
                            val relativePath =
                                "${Environment.DIRECTORY_PICTURES}${File.separator}bilibili"

                            @Suppress("DEPRECATION")
                            val contentValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                                put(MediaStore.MediaColumns.TITLE, title)
                                put(
                                    MediaStore.MediaColumns.DATE_ADDED,
                                    System.currentTimeMillis() / 1000
                                )
                                put(
                                    MediaStore.MediaColumns.DATE_MODIFIED,
                                    System.currentTimeMillis() / 1000
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    put(
                                        MediaStore.MediaColumns.DATE_TAKEN,
                                        System.currentTimeMillis()
                                    )
                                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                                } else {
                                    val path = File(
                                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                                        "bilibili"
                                    )
                                    path.mkdirs()
                                    put(
                                        MediaStore.MediaColumns.DATA,
                                        File(path, "$filename.png").absolutePath
                                    )
                                }
                            }
                            val resolver = activity.contentResolver
                            val uri = resolver.insert(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                contentValues
                            )
                            try {
                                resolver.openOutputStream(uri!!).use { stream ->
                                    it.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                }
                                Log.toast(
                                    "保存封面成功到\n$relativePath${File.separator}$filename.png",
                                    true
                                )
                            } catch (e: Throwable) {
                                Log.e(e)
                                Log.toast("保存封面失败，可能已经保存或未授予权限", true)
                            }
                        } ?: run {
                            Log.toast("获取封面失败", true)
                            return@getBitmapFromURL
                        }
                    }
                }
            })

        val onTouchListener = View.OnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        for (i in 0 until group.childCount) {
            val view = group.getChildAt(i)
            if (view.javaClass.name.startsWith("tv.danmaku.biliplayerv2.widget.gesture")
                || view.javaClass.name.startsWith("tv.danmaku.biliplayerimpl.gesture")
            ) {
                view.setOnTouchListener(onTouchListener)
            }
        }
        val liveId = getId("controller_underlay")
        activity.findViewById<View>(liveId)?.setOnTouchListener(onTouchListener)
    }

    val bgmClass by Weak {
        "com.bilibili.bangumi.ui.page.detail.playerV2.BangumiPlayerFragmentV2".findClass(
            mClassLoader
        )
    }
    val ugcClass by Weak {
        "tv.danmaku.bili.ui.video.playerv2.UgcPlayerFragment".findClassOrNull(
            mClassLoader
        ) ?: "tv.danmaku.bili.videopage.player.UgcPlayerFragment".findClassOrNull(mClassLoader)
    }
    val liveClass by Weak {
        "com.bilibili.bililive.blps.core.business.player.container.AbsLivePlayerFragment".findClass(
            mClassLoader
        )
    }

}
