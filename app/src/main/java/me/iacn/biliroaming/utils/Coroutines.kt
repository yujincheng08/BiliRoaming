package me.iacn.biliroaming.utils

import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL

suspend fun fetchJson(url: URL): JSONObject? {
    return withContext(Dispatchers.IO) {
        try {
            JSONObject(url.readText())
        } catch (e: Throwable) {
            null
        }
    }
}

fun launchMain(block: suspend CoroutineScope.() -> Unit): Job {
    return GlobalScope.launch(Dispatchers.Main, block = block)
}
