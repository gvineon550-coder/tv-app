package com.gvineon550coder.tvapp.util

import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object ProxyUtil {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Android TV) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Safari/537.36"

    fun normalize(text: String?, defaultScheme: String = "http"): String? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        return if ("://" in t) t else "$defaultScheme://$t"
    }

    fun buildClient(proxyUrl: String?): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor { chain ->
                val original = chain.request()
                val host = original.url.host.lowercase()

                // Referer и Origin — только для запросов к rutube.ru.
                // Для CDN (rtbcdn, m3u8, логотипы) их не ставим —
                // некоторые CDN на чужой Referer отвечают 403.
                val reqBuilder = original.newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")

                if (host.endsWith("rutube.ru")) {
                    reqBuilder
                        .header("Referer", "https://rutube.ru/")
                        .header("Origin", "https://rutube.ru")
                }

                chain.proceed(reqBuilder.build())
            }

        if (proxyUrl != null) {
            runCatching {
                val uri = java.net.URI(proxyUrl)
                val host = uri.host ?: return@runCatching
                val port = if (uri.port > 0) uri.port
                    else if (uri.scheme == "https") 443 else 80
                b.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
                val userInfo = uri.userInfo
                if (!userInfo.isNullOrEmpty()) {
                    val parts = userInfo.split(":", limit = 2)
                    val creds = Credentials.basic(parts[0], parts.getOrElse(1) { "" })
                    b.proxyAuthenticator { _, response ->
                        response.request.newBuilder()
                            .header("Proxy-Authorization", creds).build()
                    }
                    b.authenticator { _, response ->
                        response.request.newBuilder()
                            .header("Proxy-Authorization", creds).build()
                    }
                }
            }
        }
        return b.build()
    }
}
