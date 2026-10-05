package com.gvineon550coder.tvapp.util

import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object ProxyUtil {

    // User-Agent как у обычного браузера. Нужен, чтобы Rutube не видел
    // "okhttp/4.12.0" и не ограничивал запросы. Это стандартный UA Chrome,
    // с точки зрения сервера — просто зритель с браузера.
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
            // Interceptor добавляет User-Agent и базовые заголовки
            // ко ВСЕМ запросам через этот клиент (API, play/options, m3u8).
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
                    .build()
                chain.proceed(req)
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
