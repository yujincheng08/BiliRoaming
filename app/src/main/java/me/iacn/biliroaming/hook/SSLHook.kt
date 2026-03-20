@file:Suppress("DEPRECATION")

package me.iacn.biliroaming.hook

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebView
import me.iacn.biliroaming.utils.*
import org.apache.http.conn.scheme.HostNameResolver
import org.apache.http.conn.ssl.SSLSocketFactory
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

class SSLHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        Log.d("startHook: Ssl")

        val emptyTrustManagers = arrayOf(object : X509TrustManager {
            @SuppressLint("TrustAllX509TrustManager")
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
            }

            @SuppressLint("TrustAllX509TrustManager")
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

            @Suppress("unused", "UNUSED_PARAMETER")
            fun checkServerTrusted(
                chain: Array<X509Certificate>,
                authType: String,
                host: String
            ): List<X509Certificate> = emptyList()
        })

        "javax.net.ssl.TrustManagerFactory".hookMethod(
            mClassLoader,
            "getTrustManagers"
        ) { chain ->
            chain.proceed()
            emptyTrustManagers
        }

        "javax.net.ssl.SSLContext".hookMethod(
            mClassLoader,
            "init",
            "javax.net.ssl.KeyManager[]",
            "javax.net.ssl.TrustManager[]",
            SecureRandom::class.java
        ) { chain ->
            val args = chain.args.toTypedArray()
            args[0] = null
            args[1] = emptyTrustManagers
            args[2] = null
            chain.proceed(args)
        }

        "javax.net.ssl.HttpsURLConnection".hookMethod(
            mClassLoader,
            "setSSLSocketFactory",
            javax.net.ssl.SSLSocketFactory::class.java
        ) { chain ->
            val args = chain.args.toTypedArray()
            args[0] = "javax.net.ssl.SSLSocketFactory".findClass(mClassLoader).new()
            chain.proceed(args)
        }

        "org.apache.http.conn.scheme.SchemeRegistry".findClassOrNull(mClassLoader)
            ?.hookMethod("register", "org.apache.http.conn.scheme.Scheme") { chain ->
                if (chain.args[0]!!.callMethodAs<String>("getName") == "https") {
                    val args = chain.args.toTypedArray()
                    args[0] = chain.args[0]!!.javaClass.new(
                        "https",
                        SSLSocketFactory.getSocketFactory(),
                        443
                    )
                    return@hookMethod chain.proceed(args)
                }
                chain.proceed()
            }

        "org.apache.http.conn.ssl.HttpsURLConnection".findClassOrNull(mClassLoader)?.run {
            hookMethod("setDefaultHostnameVerifier", HostnameVerifier::class.java) { chain ->
                val args = chain.args.toTypedArray()
                args[0] = SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER
                chain.proceed(args)
            }

            hookMethod("setHostnameVerifier", HostnameVerifier::class.java) { chain ->
                val args = chain.args.toTypedArray()
                args[0] = SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER
                chain.proceed(args)
            }

        }
        "org.apache.http.conn.ssl.SSLSocketFactory".hookMethod(
            mClassLoader,
            "getSocketFactory"
        ) { chain ->
            chain.proceed()
            SSLSocketFactory::class.java.new()
        }

        "org.apache.http.conn.ssl.SSLSocketFactory".findClassOrNull(mClassLoader)
            ?.hookConstructor(
                String::class.java,
                KeyStore::class.java,
                String::class.java,
                KeyStore::class.java,
                SecureRandom::class.java,
                HostNameResolver::class.java
            ) { chain ->
                chain.proceed()
                val algorithm = chain.args[0] as? String
                val keystore = chain.args[1] as? KeyStore
                val keystorePassword = chain.args[2] as? String
                val random = chain.args[4] as? SecureRandom

                @Suppress("UNCHECKED_CAST") val trustManagers =
                    emptyTrustManagers as Array<TrustManager>

                val keyManagers = keystore?.let {
                    SSLSocketFactory::class.java.callStaticMethodAs<Array<KeyManager>>(
                        "createKeyManagers",
                        keystore,
                        keystorePassword
                    )
                }


                chain.thisObject!!.setObjectField("sslcontext", SSLContext.getInstance(algorithm))
                chain.thisObject!!.getObjectField("sslcontext")
                    ?.callMethod("init", keyManagers, trustManagers, random)
                chain.thisObject!!.setObjectField(
                    "socketfactory",
                    chain.thisObject!!.getObjectField("sslcontext")?.callMethod("getSocketFactory")
                )
                null
            }

        "org.apache.http.conn.ssl.SSLSocketFactory".hookMethod(
            mClassLoader,
            "isSecure",
            Socket::class.java
        ) { chain ->
            chain.proceed()
            true
        }

        "okhttp3.CertificatePinner".findClassOrNull(mClassLoader)?.run {
            (runCatchingOrNull { getDeclaredMethod("findMatchingPins", String::class.java) }
                ?: declaredMethods.firstOrNull { it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java && it.returnType == List::class.java })?.hookMethod { chain ->
                val args = chain.args.toTypedArray()
                args[0] = ""
                chain.proceed(args)
            }
        }

        "android.webkit.WebViewClient".findClassOrNull(mClassLoader)?.run {
            hookMethod(
                "onReceivedSslError",
                WebView::class.java,
                SslErrorHandler::class.java,
                SslError::class.java
            ) { chain ->
                (chain.args[1] as SslErrorHandler).proceed()
                null
            }
            hookMethod(
                "onReceivedError",
                WebView::class.java,
                Int::class.javaPrimitiveType,
                String::class.java,
                String::class.java
            ) {
                null
            }
        }
    }

}
