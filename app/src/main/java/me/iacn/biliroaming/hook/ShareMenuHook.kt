package me.iacn.biliroaming.hook

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import me.iacn.biliroaming.R
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.*
import java.io.File
import java.io.IOException
import java.lang.reflect.*
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance

class ShareMenuHook(val classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        private const val ITEM_ID_SHARE_TO_APPS = "share_pic_to_apps"

        private fun downloadPicture(
            file: File,
            url: String,
            onSuccess: () -> Unit,
            onFailed: () -> Unit
        ) {
            thread {
                val u = URL(url)
                try {
                    val connection = (u.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10000
                        readTimeout = 20000
                        requestMethod = "GET"
                        instanceFollowRedirects = true
                    }
                    connection.connect()
                    if (connection.responseCode == 200) {
                        val outputStream = file.outputStream().buffered()
                        val inputStream = connection.inputStream
                        try {
                            val buffer = ByteArray(4096)
                            while (true) {
                                val read = inputStream.read(buffer)
                                if (read == -1) break
                                outputStream.write(buffer, 0, read)
                            }
                            outputStream.flush()
                            outputStream.close()
                            onSuccess()
                        } catch (e: IOException) {
                            inputStream.close()
                            outputStream.close()
                            if (file.exists()) file.delete()
                            connection.disconnect()
                            throw e
                        }
                    } else {
                        connection.disconnect()
                        onFailed()
                    }
                } catch (t: Throwable) {
                    Log.e("failed to download $url")
                    Log.e(t)
                    onFailed()
                }
            }
        }

        private fun saveBitmap(
            bitmap: Bitmap,
            file: File,
            onSuccess: () -> Unit,
            onFailed: () -> Unit
        ) {
            thread {
                try {
                    val os = file.outputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                    os.flush()
                    os.close()
                    onSuccess()
                } catch (e: Throwable) {
                    Log.e("failed to save bitmap to $file")
                    Log.e(e)
                    onFailed()
                }
            }
        }

        private fun startShareActivity(context: Context, type: String, file: File) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                this.type = type
                putExtra(
                    Intent.EXTRA_STREAM,
                    instance.getUriForFile(context, file)
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(intent, "分享")
                    .also { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }

        fun sharePictureToOtherApps(imageUrl: String?, imageBmp: Bitmap?, imageFile: File?) {
            val context = AndroidAppHelper.currentApplication()
            val file = File(
                context.getExternalFilesDir(null),
                (Math.random() * Int.MAX_VALUE).toInt().toString()
            )
            val onSuccess = {
                startShareActivity(context, "image/*", file)
            }
            val onFailed = {
                Log.toast("分享失败")
            }
            if (imageUrl != null) {
                downloadPicture(file, imageUrl, onSuccess, onFailed)
            } else if (imageBmp != null) {
                saveBitmap(imageBmp, file, onSuccess, onFailed)
            } else if (imageFile != null) {
                thread {
                    try {
                        imageFile.copyTo(file, true)
                        onSuccess()
                    } catch (t: Throwable) {
                        Log.e("failed to copy $imageFile")
                        Log.e(t)
                        onFailed()
                    }
                }
            } else {
                Log.toast("未找到分享内容")
                return
            }
            Log.toast("正在准备分享……")
        }

        @SuppressLint("ResourceType")
        fun View.findViewByResourceText(text: String): View? {
            if (id > 0 && this.resources.getResourceEntryName(this.id).contains(text)) return this
            if (this is ViewGroup) {
                for (i in 0 until childCount) {
                    getChildAt(i).findViewByResourceText(text)?.also { return it }
                }
            }
            return null
        }

        class MyMenuItemClickListener(val orig: Any?, val shareContent: Bundle) :
            InvocationHandler {
            override fun invoke(thiz: Any, method: Method, args: Array<out Any>): Any? {
                if (method.name == "onItemClick") {
                    val menuItem = args[0]
                    val itemId = menuItem.callMethod("getItemId")
                    if (itemId == ITEM_ID_SHARE_TO_APPS) {
                        sharePictureToOtherApps(shareContent.getString("image_url"), shareContent.getParcelable("image_bmp"), null)
                        return true
                    }
                }
                if (orig != null) {
                    return method.invoke(orig, *args)
                }
                return false
            }

        }
    }

    private fun startHookSuperMenu() {
        val classSuperMenu = instance.superMenuClass ?: return
        val classMenuItemImpl = instance.menuItemImplClass ?: return
        instance.iMenuItemClass ?: return
        val fieldShareHelperV2 = instance.shareHelperCallbackClass?.let {
            classSuperMenu.findFieldByExactType(it)
        }?: return
        val classMenuItemClickListener = instance.menuItemClickListenerV2Class ?: return
        val methodGetMenus = classSuperMenu.getDeclaredMethod("getMenus")
        val classMenu =
            ((methodGetMenus.genericReturnType as ParameterizedType).actualTypeArguments[0] as Class<*>)
        val fieldClickListener =
            classSuperMenu.findFieldByExactType(classMenuItemClickListener) ?: return
        val ctorMenuItemImpl = classMenuItemImpl.getDeclaredConstructor(
            Context::class.java, String::class.java, Integer.TYPE, CharSequence::class.java
        )
        val methodMenuGetMenuItems = classMenu.declaredMethods.find {
            val returnType = it.genericReturnType as? ParameterizedType
            returnType?.rawType as? Class<*> == java.util.List::class.java &&
                    returnType.actualTypeArguments[0] as? Class<*> == instance.iMenuItemClass
        }!!
        classSuperMenu.hookBeforeMethod("show") {
            with(it.thisObject) {
                val helper = fieldShareHelperV2.get(this)
                val shareContent = (helper?.callMethod("getShareContent", "") as? Bundle)
                val shareType = shareContent?.get("params_type")
                if (!(shareContent != null && shareType == "type_pure_image" || shareType == "type_image")) {
                    return@hookBeforeMethod
                }
                val menus = methodGetMenus.invoke(this) as java.util.List<*>
                if (menus.isEmpty()) return@hookBeforeMethod
                menus.last()?.also {
                    val items = methodMenuGetMenuItems.invoke(it) as java.util.List<Any>
                    val item = ctorMenuItemImpl.newInstance(
                        AndroidAppHelper.currentApplication(),
                        ITEM_ID_SHARE_TO_APPS,
                        0,
                        "分享图片到其他应用"
                    )
                    item.callMethod(
                        "setIcon",
                        XposedInit.moduleRes.getDrawable(R.drawable.share_icon)
                    )
                    items.add(item)
                    val origListener = fieldClickListener.get(this)
                    fieldClickListener.set(
                        this,
                        Proxy.newProxyInstance(
                            classLoader,
                            arrayOf(classMenuItemClickListener),
                            MyMenuItemClickListener(origListener, shareContent)
                        )
                    )
                }
            }
        }
    }

    private fun startHookPosterShare() {
        val classPosterShareDialog = instance.posterShareDialogClass ?: return
        val classPosterShareCoreView = instance.posterShareCoreViewClass ?: return
        val classPosterData = instance.posterDataClass ?: return
        val fieldPosterShareCoreView = classPosterShareDialog.findFieldByExactType(classPosterShareCoreView) ?: return
        val fieldPosterData = classPosterShareCoreView.findFieldByExactType(classPosterData) ?: return
        var fieldMagic1: Field? = null
        // String
        var fieldMagic2: Field? = null

        fun findMagicField(coreView: Any) {
            classPosterShareCoreView.declaredFields.find { field1 ->
                field1.isAccessible = true
                if (field1.type.name.startsWith("com.bilibili.app.comm.supermenu.share.pic.ui.PosterShareCoreView\$")) {
                    val field1Val = field1.get(coreView) ?: return@find false
                    (field1.type as Class).declaredFields.find { field2 ->
                        field2.isAccessible = true
                        field2.type.name == "java.lang.String" && (field2.get(field1Val) as? String)?.startsWith("/storage/emulated") == true
                    }?.also {
                        fieldMagic2 = it
                        return@find true
                    }
                }
                false
            }?.also {
                fieldMagic1 = it
            }
        }

        classPosterShareDialog.hookAfterMethod("onViewCreated", View::class.java, Bundle::class.java) {
            with (it.thisObject) {
                val coreView = fieldPosterShareCoreView.get(this) as View
                coreView.findViewByResourceText("poster_img_container")?.also {
                    (it as ViewGroup).getChildAt(0).apply {
                        isLongClickable = true
                        setOnLongClickListener {
                            val data = fieldPosterData.get(coreView)
                            if (data != null) {
                                val url = data.getObjectField("mPicture") as? String
                                if (url != null) {
                                    sharePictureToOtherApps(url, null, null)
                                } else {
                                    // comment share
                                    if (fieldMagic1 == null || fieldMagic2 == null) {
                                        findMagicField(coreView)
                                    }

                                    if (fieldMagic1 != null && fieldMagic2 != null) {
                                        val path = fieldMagic1!!.get(coreView)?.let { fieldMagic2!!.get(it) } as? String
                                        if (path != null) {
                                            sharePictureToOtherApps(null, null, File(path))
                                            return@setOnLongClickListener true
                                        }
                                    }
                                    Log.toast("未找到分享内容")
                                }
                            }
                            true
                        }
                        Log.toast("长按图片分享到其他应用")
                    }
                }
            }
        }
    }

    override fun startHook() {
        if (!instance.hasFileProvider) return
        if (sPrefs.getBoolean("share_pictures", false)) {
            startHookSuperMenu()
        }
        if (sPrefs.getBoolean("share_posters", false)) {
            startHookPosterShare()
        }
    }
}
