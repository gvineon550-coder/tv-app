package com.tv.player.util

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Результат проверки лицензии.
 */
sealed class LicenseResult {
    /** Устройство разрешено — работаем. */
    object Allowed : LicenseResult()

    /** Устройство не в списке разрешённых — блокируем. */
    object Denied : LicenseResult()

    /** Не удалось проверить (нет сети и нет кэша). */
    data class Unknown(val deviceId: String, val reason: String) : LicenseResult()
}

/**
 * Менеджер лицензий.
 *
 * Логика:
 * 1. Свои устройства (белый список) — работают всегда, без интернета.
 * 2. Кэш на 7 дней — если свежий, используем без запросов.
 * 3. Если кэш старый — качаем licenses.json с GitHub Pages.
 * 4. Если сеть недоступна — используем старый кэш (если есть).
 * 5. Если ничего нет — Unknown (показать экран активации).
 */
object LicenseManager {

    private const val LICENSE_URL =
        "https://gvineon550-coder.github.io/tv-app/licenses.json"

    // !!! Свои устройства — работают всегда, без проверок !!!
    // Узнать свой ID: первый запуск новой версии покажет его на экране активации.
    // Впиши ID телефона и приставки сюда, потом пересобери APK.
    // Пример:
    //   private val MY_DEVICES: Set<String> = setOf("1234", "5678")
    // Пока пусто — все устройства проверяются через licenses.json.
    private val MY_DEVICES: Set<String> = emptySet()

    private const val PREFS_NAME = "license_cache"
    private const val KEY_ALLOWED = "allowed"
    private const val KEY_CACHED_AT = "cached_at"
    private const val KEY_CACHED_DEVICE_ID = "cached_device_id"

    // Кэш на 7 дней
    private const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * Короткий 4-значный ID устройства.
     * Стабилен: одно и то же устройство всегда даёт один ID.
     */
    fun getDeviceId(): String {
        val buildInfo = "${Build.BOARD}|${Build.BRAND}|${Build.DEVICE}|" +
                        "${Build.HARDWARE}|${Build.MODEL}|${Build.PRODUCT}"
        // hashCode() может быть отрицательным — берём беззнаковое значение
        val hash = buildInfo.hashCode().toLong() and 0xFFFFFFFFL
        return (1000 + (hash % 9000)).toString()
    }

    /**
     * Проверить лицензию устройства.
     * Блокирующая — вызывать из корутины.
     */
    suspend fun check(context: Context): LicenseResult = withContext(Dispatchers.IO) {
        val deviceId = getDeviceId()

        // 1. Своё устройство — сразу ОК
        if (MY_DEVICES.contains(deviceId)) {
            return@withContext LicenseResult.Allowed
        }

        // 2. Смотрим кэш
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedAt = prefs.getLong(KEY_CACHED_AT, 0L)
        val cachedAllowed = prefs.getBoolean(KEY_ALLOWED, false)
        val cachedDeviceId = prefs.getString(KEY_CACHED_DEVICE_ID, null)
        val now = System.currentTimeMillis()

        // Кэш валиден только если он от этого же устройства
        val cacheValid = cachedAt > 0L &&
                         cachedDeviceId == deviceId &&
                         (now - cachedAt) < CACHE_TTL_MS

        if (cacheValid) {
            return@withContext if (cachedAllowed) LicenseResult.Allowed
                                else LicenseResult.Denied
        }

        // 3. Кэш устарел или пустой — качаем свежий список
        val allowed = runCatching { fetchLicense(deviceId) }.getOrNull()

        if (allowed != null) {
            prefs.edit()
                .putBoolean(KEY_ALLOWED, allowed)
                .putString(KEY_CACHED_DEVICE_ID, deviceId)
                .putLong(KEY_CACHED_AT, now)
                .apply()
            return@withContext if (allowed) LicenseResult.Allowed
                                else LicenseResult.Denied
        }

        // 4. Не удалось скачать — используем старый кэш (даже просроченный)
        if (cachedAt > 0L && cachedDeviceId == deviceId) {
            return@withContext if (cachedAllowed) LicenseResult.Allowed
                                else LicenseResult.Denied
        }

        // 5. Совсем ничего нет — Unknown
        LicenseResult.Unknown(
            deviceId = deviceId,
            reason = "Нет соединения с сервером лицензий"
        )
    }

    /**
     * Скачать licenses.json и проверить, есть ли deviceId в списке.
     * Возвращает true/false. Бросает исключение при ошибке сети.
     */
    private fun fetchLicense(deviceId: String): Boolean {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(LICENSE_URL)
            .header("Accept", "application/json")
            .header("User-Agent", "tv-app/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false

            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return false

            val json = JSONObject(body)
            val devices = json.optJSONArray("devices") ?: return false

            for (i in 0 until devices.length()) {
                if (devices.optString(i) == deviceId) return true
            }
        }
        return false
    }

    /**
     * Сбросить кэш. Следующая проверка пойдёт в сеть.
     */
    fun clearCache(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
