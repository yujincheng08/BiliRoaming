package me.iacn.biliroaming.hook

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import de.robv.android.xposed.XC_MethodHook
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookBeforeMethod
import java.io.BufferedReader
import java.io.InputStreamReader


class WebViewHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val hookedClient = HashSet<Class<*>>()
    private val hooker = { param: XC_MethodHook.MethodHookParam ->
        (param.args[0] as WebView).run {
            loadUrl("""javascript:(function(){$js})()""".trimMargin())
        }
    }

    private val jsHooker = object : Any() {
        @JavascriptInterface
        fun hook(url: String, text: String): String {
            return this@WebViewHook.hook(url, text)
        }
    }

    private val js by lazy {
        val sb = StringBuilder()
        try {
            val `is` = WebViewHook::class.java.classLoader?.getResourceAsStream("assets/xhook.js")
            val isr = InputStreamReader(`is`)
            val br = BufferedReader(isr)
            var line: String
            while (br.readLine().also { line = it } != null) {
                sb.appendLine(line)
            }
            `is`?.close()
        } catch (e: Exception) {
        }
        sb.appendLine()
        sb.toString()
    }

    override fun startHook() {
        Log.d("startHook: WebView")
        WebView::class.java.hookBeforeMethod("setWebViewClient", WebViewClient::class.java) { param ->
            val clazz = param.args[0].javaClass
            (param.thisObject as WebView).run {
                addJavascriptInterface(jsHooker, "hooker")
            }
            if (hookedClient.contains(clazz)) return@hookBeforeMethod
            try {
                clazz.getDeclaredMethod("onPageFinished", WebView::class.java, String::class.java).hookBeforeMethod(hooker)
                hookedClient.add(clazz)
                Log.d("hook webview $clazz")
            } catch (e: NoSuchMethodException) {
            }
        }
    }

    fun hook(url: String, text: String): String {
        if (XposedInit.sPrefs.getBoolean("comment_floor", false)) {
            if (url.contains("api.bilibili.com/x/v2/reply/main"))
                return text.replace("\"showfloor\":0", "\"showfloor\":1")
        }
        return text
    }
}