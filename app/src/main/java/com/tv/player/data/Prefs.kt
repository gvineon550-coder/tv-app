package com.tv.player.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "tv_prefs")

class Prefs(private val context: Context) {

    companion object {
        val API_PROXY_ENABLED = booleanPreferencesKey("api_proxy_enabled")
        val API_PROXY = stringPreferencesKey("api_proxy")
        val STREAM_PROXY_ENABLED = booleanPreferencesKey("stream_proxy_enabled")
        val STREAM_PROXY = stringPreferencesKey("stream_proxy")
        val MAX_HEIGHT = intPreferencesKey("max_height")
        val SYNONYMS = stringPreferencesKey("synonyms")
        val CACHE = stringPreferencesKey("channels_cache")
        val FAVORITES = stringPreferencesKey("favorites")
        val LAST_PARSE = longPreferencesKey("last_parse")
        val JSON_SOURCE_URL = stringPreferencesKey("json_source_url")
        val PIN_HASH = stringPreferencesKey("pin_hash")

        const val DEFAULT_JSON_URL =
            "https://gvineon550-coder.github.io/tv-app/channels.json"
    }

    val maxHeight: Flow<Int> = context.dataStore.data.map { it[MAX_HEIGHT] ?: 1080 }
    val apiProxy: Flow<String> = context.dataStore.data.map { it[API_PROXY] ?: "" }
    val apiProxyEnabled: Flow<Boolean> = context.dataStore.data.map { it[API_PROXY_ENABLED] ?: false }
    val streamProxy: Flow<String> = context.dataStore.data.map { it[STREAM_PROXY] ?: "" }
    val streamProxyEnabled: Flow<Boolean> = context.dataStore.data.map { it[STREAM_PROXY_ENABLED] ?: false }
    val synonyms: Flow<String> = context.dataStore.data.map { it[SYNONYMS] ?: "{}" }
    val cache: Flow<String> = context.dataStore.data.map { it[CACHE] ?: "{}" }
    val favorites: Flow<String> = context.dataStore.data.map { it[FAVORITES] ?: "" }
    val lastParse: Flow<Long> = context.dataStore.data.map { it[LAST_PARSE] ?: 0L }
    val jsonSourceUrl: Flow<String> = context.dataStore.data.map {
        it[JSON_SOURCE_URL] ?: DEFAULT_JSON_URL
    }
    val pinHash: Flow<String> = context.dataStore.data.map { it[PIN_HASH] ?: "" }

    suspend fun setMaxHeight(v: Int) = context.dataStore.edit { it[MAX_HEIGHT] = v }
    suspend fun setApiProxy(v: String) = context.dataStore.edit { it[API_PROXY] = v }
    suspend fun setApiProxyEnabled(v: Boolean) = context.dataStore.edit { it[API_PROXY_ENABLED] = v }
    suspend fun setStreamProxy(v: String) = context.dataStore.edit { it[STREAM_PROXY] = v }
    suspend fun setStreamProxyEnabled(v: Boolean) = context.dataStore.edit { it[STREAM_PROXY_ENABLED] = v }
    suspend fun setSynonyms(v: String) = context.dataStore.edit { it[SYNONYMS] = v }
    suspend fun setCache(v: String) = context.dataStore.edit { it[CACHE] = v }
    suspend fun setFavorites(v: String) = context.dataStore.edit { it[FAVORITES] = v }
    suspend fun setLastParse(v: Long) = context.dataStore.edit { it[LAST_PARSE] = v }
    suspend fun setJsonSourceUrl(v: String) = context.dataStore.edit { it[JSON_SOURCE_URL] = v }
    suspend fun setPinHash(v: String) = context.dataStore.edit { it[PIN_HASH] = v }

    suspend fun snapshot(): Snapshot = Snapshot(
        apiProxyEnabled = apiProxyEnabled.first(),
        apiProxy = apiProxy.first(),
        streamProxyEnabled = streamProxyEnabled.first(),
        streamProxy = streamProxy.first(),
        maxHeight = maxHeight.first(),
        synonyms = synonyms.first(),
        cache = cache.first(),
        favorites = favorites.first(),
        lastParse = lastParse.first(),
        jsonSourceUrl = jsonSourceUrl.first(),
        pinHash = pinHash.first()
    )

    data class Snapshot(
        val apiProxyEnabled: Boolean,
        val apiProxy: String,
        val streamProxyEnabled: Boolean,
        val streamProxy: String,
        val maxHeight: Int,
        val synonyms: String,
        val cache: String,
        val favorites: String,
        val lastParse: Long,
        val jsonSourceUrl: String,
        val pinHash: String
    )
}
