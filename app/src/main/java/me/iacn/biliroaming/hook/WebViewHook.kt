package me.iacn.biliroaming.hook

import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import me.iacn.biliroaming.BuildConfig
import me.iacn.biliroaming.XposedInit.Companion.moduleRes
import me.iacn.biliroaming.utils.*


class WebViewHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val hookedClient = HashSet<Class<*>>()

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
        WebView::class.java.hookMethod(
            "setWebViewClient", WebViewClient::class.java
        ) { chain ->
            val clazz = chain.args[0]!!.javaClass
            (chain.thisObject as WebView).addJavascriptInterface(jsHooker, "hooker")
            if (!hookedClient.contains(clazz)) {
                try {
                    clazz.getDeclaredMethod(
                        "onPageStarted",
                        WebView::class.java, String::class.java, Bitmap::class.java
                    ).hookMethod { innerChain ->
                        val webView = innerChain.args[0] as WebView
                        webView.evaluateJavascript("""(function(){$js})()""".trimMargin(), null)
                        innerChain.proceed()
                    }
                    if (sPrefs.getBoolean("save_comment_image", false)) {
                        clazz.getDeclaredMethod(
                            "onPageFinished",
                            WebView::class.java, String::class.java
                        ).hookMethod { innerChain ->
                            val webView = innerChain.args[0] as WebView
                            val url = innerChain.args[1] as String
                            if (url.startsWith("https://www.bilibili.com/h5/note-app/view")) {
                                webView.evaluateJavascript(
                                    """(function(){for(var i=0;i<document.images.length;++i){if(document.images[i].className==='img-preview'){document.images[i].addEventListener("contextmenu",(e)=>{hooker.saveImage(e.target.currentSrc);})}}})()""",
                                    null
                                )
                            }
                            innerChain.proceed()
                        }
                    }
                    hookedClient.add(clazz)
                    Log.d("hook webview $clazz")
                } catch (_: NoSuchMethodException) {
                }
            }
            chain.proceed()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun hook(url: String, text: String): String {
        return text
    }

    override fun lateInitHook() {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
