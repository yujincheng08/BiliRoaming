package me.iacn.biliroaming.hook

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.View
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class CommentImageHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        fun saveImage(url: String) = runCatching {
            URL(url).openStream().use { stream ->
                val relativePath = "${Environment.DIRECTORY_PICTURES}${File.separator}bilibili"
                val fullFilename = url.substringAfterLast('/')
                val filename = fullFilename.substringBeforeLast('.')

                val now = System.currentTimeMillis()
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(
                        MediaStore.MediaColumns.MIME_TYPE,
                        HttpURLConnection.guessContentTypeFromName(fullFilename) ?: "image/png"
                    )
                    put(MediaStore.MediaColumns.DATE_ADDED, now / 1000)
                    put(MediaStore.MediaColumns.DATE_MODIFIED, now / 1000)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.DATE_TAKEN, now)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    } else {
                        val path = File(
                            Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_PICTURES
                            ), "bilibili"
                        ).also { it.mkdirs() }
                        put(MediaStore.MediaColumns.DATA, File(path, fullFilename).absolutePath)
                    }
                }
                val resolver = currentContext.contentResolver
                val uri = resolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
                )
                runCatching {
                    resolver.openOutputStream(uri!!)?.use { stream.copyTo(it) }
                }.onSuccess {
                    Log.toast("图片已保存至\n$relativePath${File.separator}$fullFilename", true)
                }.onFailure {
                    Log.e(it)
                    Log.toast("图片保存失败，可能已经保存或未授予权限", true)
                }
            }
        }.onFailure {
            Log.e(it)
            Log.toast("图片获取失败", force = true)
        }
    }

    private val imageViewId = getId("image_view")
    private var cacheUrlFieldName = ""

    @Suppress("DEPRECATION")
    override fun startHook() {
        if (!sPrefs.getBoolean("save_comment_image", false)) return
        instance.imageFragmentClass?.hookAfterMethod(
            "onViewCreated", View::class.java, Bundle::class.java
        ) { param ->
            val self = param.thisObject
            val view = param.args[0] as? View
            val imageItem = self.callMethodOrNullAs<Bundle?>("getArguments")
                ?.getParcelable<Parcelable>("image_item") ?: return@hookAfterMethod
            val urlFieldName = cacheUrlFieldName.ifEmpty {
                imageItem.javaClass.superclass.findFirstFieldByExactTypeOrNull(String::class.java)
                    ?.name.orEmpty().also { cacheUrlFieldName = it }
            }.ifEmpty { return@hookAfterMethod }
            val imageUrl = imageItem.getObjectFieldAs<String?>(urlFieldName).takeIf {
                !it.isNullOrEmpty() && it.startsWith("http")
            }?.substringBefore('@') ?: return@hookAfterMethod
            view?.findViewById<View>(imageViewId)?.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                MainScope().launch(Dispatchers.IO) {
                    saveImage(imageUrl)
                }
                true
            }
        }
    }
}
