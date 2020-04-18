package me.iacn.biliroaming.utils

import android.os.AsyncTask
import org.json.JSONObject
import java.net.URL

interface OnTaskReturn<T> {
    fun onReturn(result: T?)
}

class CheckVersionTask(private val onResult: OnTaskReturn<JSONObject>) : AsyncTask<URL, Void, JSONObject?>() {
    override fun doInBackground(vararg url: URL?): JSONObject? {
        return try {
            JSONObject(url[0]?.readText()!!)
        } catch (e: Throwable) {
            null
        }
    }

    override fun onPostExecute(result: JSONObject?) {
        onResult.onReturn(result)
    }

}
