package me.iacn.biliroaming.hook

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageView
import de.robv.android.xposed.XC_MethodHook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class CommentImageHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    @Suppress("UNCHECKED_CAST")
    override fun startHook() {
        if (!sPrefs.getBoolean("save_comment_image", false)) return
        val hooker = fun(param: XC_MethodHook.MethodHookParam) {
            val images = param.args[1] as? List<ImageView> ?: return
            val imageUrls = if (param.args.size > 3) {
                (param.args[3] as? List<Any>)?.map { it.getFirstFieldByExactTypeOrNull() }
            } else param.args[2].getObjectFieldOrNullAs<List<String?>?>("images")
            imageUrls ?: return
            for ((image, imageUrl) in images.zip(imageUrls)) {
                imageUrl ?: continue
                image.setOnLongClickListener {
                    MainScope().launch(Dispatchers.IO) {
                        runCatching {
                            URL(imageUrl).openStream().use { stream ->
                                val relativePath =
                                    "${Environment.DIRECTORY_PICTURES}${File.separator}bilibili"
                                val fullFilename = imageUrl.substringAfterLast('/')
                                val filename = fullFilename.substringBeforeLast('.')

                                val now = System.currentTimeMillis()
                                val contentValues = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                                    put(
                                        MediaStore.MediaColumns.MIME_TYPE,
                                        HttpURLConnection.guessContentTypeFromName(fullFilename)
                                            ?: "image/png"
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
                                        put(
                                            MediaStore.MediaColumns.DATA,
                                            File(path, fullFilename).absolutePath
                                        )
                                    }
                                }
                                val resolver = currentContext.contentResolver
                                val uri = resolver.insert(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
                                )
                                runCatching {
                                    resolver.openOutputStream(uri!!)?.use { stream.copyTo(it) }
                                }.onSuccess {
                                    Log.toast(
                                        "图片已保存至\n$relativePath${File.separator}$fullFilename",
                                        true
                                    )
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
                    true
                }
            }
        }
        instance.commentImageLoaderClass?.declaredMethods?.filter {
            it.name == instance.load() && it.parameterTypes.size == 6
                    || it.name == instance.richLoad() && it.parameterTypes.size == 3
        }?.forEach { it.hookAfterMethod(hooker) }
    }
}
