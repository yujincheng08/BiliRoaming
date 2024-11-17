package me.iacn.biliroaming.hook

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.hookBeforeAllMethods
import me.iacn.biliroaming.utils.hookBeforeMethod
import me.iacn.biliroaming.utils.packageName
import me.iacn.biliroaming.utils.sPrefs
import me.iacn.biliroaming.utils.toJSONObject
import kotlin.math.floor

class StartActivityHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private fun fixIntentUri(original: Uri): Uri {
        val fixedUri = Uri.parse(original.toString().replace("bilibili://story/", "bilibili://united_video/")).buildUpon()
            .clearQuery()
            .appendQueryParameter("from_spmid", original.getQueryParameter("from_spmid"))
            .appendQueryParameter("aid", original.path?.split("/")?.last() ?: "")
            .appendQueryParameter("bvid", "")
            .build()
        return fixedUri
    }

    override fun startHook() {
        "tv.danmaku.bili.ui.intent.IntentHandlerActivity".hookBeforeMethod(mClassLoader, "onCreate", Bundle::class.java) { param ->
            val a = param.thisObject as Activity
            val data = a.intent.data ?: return@hookBeforeMethod
            a.intent.data = data.buildUpon().encodedQuery(data.encodedQuery?.replace("&-Arouter=story", "")?.replace("&-Atype=story", "")).build()
        }
        Instrumentation::class.java.hookBeforeAllMethods("execStartActivity") { param ->
            val intent = param.args[4] as? Intent ?: return@hookBeforeAllMethods
            val uri = intent.dataString ?: return@hookBeforeAllMethods
            if (sPrefs.getBoolean(
                    "replace_story_video",
                    false
                ) && uri.startsWith("bilibili://story/")
            ) {
                intent.data?.let {
                    try {
                        val cid = intent.data?.getQueryParameter("player_preload").toJSONObject().getLong("cid")
                        intent.data = fixIntentUri(Uri.parse(intent.dataString))
                        // fix extra
                        val pre = Uri.parse(intent.dataString).buildUpon().clearQuery().build().toString()
                        val aid = pre.split("/").last().toLong()
                        intent.removeExtra("player_preload")
                        intent.putExtra("player_preload", floor(Math.random()*1000000000).toInt().toString())
                        intent.putExtra("blrouter.targeturl", pre)
                        intent.putExtra("blrouter.pagename", "bilibili://united_video/")
                        intent.putExtra("jumpFrom", 7)
                        intent.putExtra("", aid)
                        intent.putExtra("aid", aid)
                        intent.putExtra("cid", cid)
                        intent.putExtra("bvid", "")
                        intent.putExtra("from", 7)
                        intent.putExtra("blrouter.targeturl", pre)
                        intent.putExtra("blrouter.matchrule", "bilibili://united_video/")
                        // fix component
                        intent.component = ComponentName(
                            intent.component?.packageName ?: packageName,
                            "com.bilibili.ship.theseus.detail.UnitedBizDetailsActivity"
                        )
                    } catch (e: Exception) {
                        Log.e("replaceStoryVideo fix intent failed!!!")
                        Log.e(e)
                    }
                }
            }
            if (sPrefs.getBoolean("force_browser", false)) {
                if (intent.component?.className?.endsWith("MWebActivity") == true &&
                        intent.data?.authority?.matches(whileListDomain) == false) {
                    Log.d("force_browser ${intent.data?.authority}")
                    param.args[4] = Intent(Intent.ACTION_VIEW).apply {
                        data = intent.data
                    }
                }
            }
        }
    }
    companion object {
        val whileListDomain = Regex(""".*bilibili\.com|.*b23\.tv""")
    }
}
