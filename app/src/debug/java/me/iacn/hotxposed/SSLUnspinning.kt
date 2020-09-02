@file:Suppress("DEPRECATION")

package me.iacn.hotxposed

import android.annotation.SuppressLint
import me.iacn.biliroaming.utils.*
import org.apache.http.conn.scheme.HostNameResolver
import org.apache.http.conn.ssl.SSLSocketFactory
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

// From https://github.com/ac-pm/SSLUnpinning_Xposed/blob/master/app/src/main/java/mobi/acpm/sslunpinning/Module.java
fun hookSSL(classLoader: ClassLoader) {
    val emptyTrustManagers = arrayOf(object : X509TrustManager {
        @SuppressLint("TrustAllX509TrustManager")
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        }

        @SuppressLint("TrustAllX509TrustManager")
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return emptyArray()
        }
    })

    "javax.net.ssl.TrustManagerFactory".hookBeforeMethod(classLoader, "getTrustManagers") { param ->
        param.result = emptyTrustManagers
    }

    "javax.net.ssl.SSLContext".hookBeforeMethod(classLoader, "init", "javax.net.ssl.KeyManager[]", "javax.net.ssl.TrustManager[]", SecureRandom::class.java) { param ->
        param.args[0] = null
        param.args[1] = emptyTrustManagers
        param.args[2] = null
    }

    "javax.net.ssl.HttpsURLConnection".hookBeforeMethod(classLoader, "setSSLSocketFactory", javax.net.ssl.SSLSocketFactory::class.java) { param ->
        param.args[0] = "javax.net.ssl.SSLSocketFactory".findClass(classLoader)!!.new()
    }

    "org.apache.http.conn.scheme.SchemeRegistry".hookBeforeMethod(classLoader, "register", "org.apache.http.conn.scheme.Scheme") { param ->
        if (param.args[0].callMethodAs<String>("getName") == "https") {
            param.args[0] = param.args[0].javaClass.new("https", SSLSocketFactory.getSocketFactory(), 443)
        }
    }

    "org.apache.http.conn.ssl.HttpsURLConnection".findClassOrNull(classLoader)?.run {
        hookBeforeMethod("setDefaultHostnameVerifier", HostnameVerifier::class.java) { param ->
            param.args[0] = SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER
        }

        hookBeforeMethod("setHostnameVerifier", HostnameVerifier::class.java) { param ->
            param.args[0] = SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER
        }

    }
    "org.apache.http.conn.ssl.SSLSocketFactory".hookBeforeMethod(classLoader, "getSocketFactory") { param ->
        param.result = SSLSocketFactory::class.java.new()
    }

    "org.apache.http.conn.ssl.SSLSocketFactory".findClass(classLoader)?.hookAfterConstructor(String::class.java, KeyStore::class.java, String::class.java, KeyStore::class.java, SecureRandom::class.java, HostNameResolver::class.java) { param ->
        val algorithm = param.args[0] as String?
        val keystore = param.args[1] as KeyStore?
        val keystorePassword = param.args[2] as String?
        val random = param.args[4] as SecureRandom?

        @Suppress("UNCHECKED_CAST") val trustManagers = emptyTrustManagers as Array<TrustManager>

        val keyManagers = keystore?.let { SSLSocketFactory::class.java.callStaticMethodAs<Array<KeyManager>>("createKeyManagers", keystore, keystorePassword) }


        param.thisObject.setObjectField("sslcontext", SSLContext.getInstance(algorithm))
        param.thisObject.getObjectField("sslcontext")?.callMethod("init", keyManagers, trustManagers, random)
        param.thisObject.setObjectField("socketfactory", param.thisObject.getObjectField("sslcontext")?.callMethod("getSocketFactory"))
    }

    "org.apache.http.conn.ssl.SSLSocketFactory".hookAfterMethod(classLoader, "isSecure", Socket::class.java) { param ->
        param.result = true
    }

    "okhttp3.CertificatePinner".findClassOrNull(classLoader)?.hookBeforeMethod("findMatchingPins", String::class.java) { param ->
        param.args[0] = ""
    }
}

