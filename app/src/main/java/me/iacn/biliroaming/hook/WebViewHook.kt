package me.iacn.biliroaming.hook

import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import me.iacn.biliroaming.BuildConfig
import me.iacn.biliroaming.XposedInit.Companion.moduleRes
import me.iacn.biliroaming.utils.*


class WebViewHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val biliWebviewClass by Weak {
        "com.bilibili.app.comm.bh.BiliWebView".findClassOrNull(
            mClassLoader
        )
    }
    private val hookedClient = HashSet<Class<*>>()
    private val hooker: Hooker = { param ->
        try {
            param.args[0].callMethod(
                "evaluateJavascript", """(function(){$js})()""".trimMargin(), null
            )
        } catch (e: Throwable) {
            Log.e(e)
        }
    }

    private val jsHooker = object : Any() {
        @Suppress("UNUSED")
        @JavascriptInterface
        fun hook(url: String, text: String): String {
            return this@WebViewHook.hook(url, text)
        }

        @Suppress("UNUSED")
        @JavascriptInterface
        fun saveImage(url: String) {
            MainScope().launch(Dispatchers.IO) {
                CommentImageHook.saveImage(url)
            }
        }
    }

    private val js by lazy {
        runCatchingOrNull {
            moduleRes.assets.open("xhook.js")
                .use { it.bufferedReader().readText() }
        } ?: ""
    }

    override fun startHook() {
        Log.d("startHook: WebView")
        biliWebviewClass?.hookBeforeMethod(
            "setWebViewClient", "com.bilibili.app.comm.bh.BiliWebViewClient"
        ) { param ->
            val clazz = param.args[0].javaClass
            param.thisObject.callMethod("addJavascriptInterface", jsHooker, "hooker")
            if (hookedClient.contains(clazz)) return@hookBeforeMethod
            try {
                clazz.getDeclaredMethod(
                    "onPageStarted", biliWebviewClass, String::class.java, Bitmap::class.java
                ).hookBeforeMethod(hooker)
                if (sPrefs.getBoolean("save_comment_image", false)) {
                    clazz.getDeclaredMethod("onPageFinished", biliWebviewClass, String::class.java)
                        .hookBeforeMethod { hookParam ->
                            val url = hookParam.args[1] as String
                            if (url.startsWith("https://www.bilibili.com/h5/note-app/view")) {
                                hookParam.args[0].callMethod(
                                    "evaluateJavascript",
                                    """(function(){for(var i=0;i<document.images.length;++i){if(document.images[i].className==='img-preview'){document.images[i].addEventListener("contextmenu",(e)=>{hooker.saveImage(e.target.currentSrc);})}}})()""",
                                    null
                                )
                            }
                        }
                }
                hookedClient.add(clazz)
                Log.d("hook webview $clazz")
            } catch (_: NoSuchMethodException) {
            }
        }
    }

    fun hook(url: String, text: String): String {
        return text
    }

    override fun lateInitHook() {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
            biliWebviewClass?.callStaticMethod("setWebContentsDebuggingEnabled", true)
        }
    }
}
