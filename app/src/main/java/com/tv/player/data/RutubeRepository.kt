package com.tv.player.data

import com.google.gson.annotations.SerializedName
import com.tv.player.util.ProxyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

// ---------- DTO для JSON с GitHub Pages ----------

data class JsonChannelsResponse(
    @SerializedName("updated") val updated: String? = null,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("channels") val channels: List<JsonChannel>? = null
)

data class JsonChannel(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("stream_url") val streamUrl: String? = null,
    @SerializedName("blocked") val blocked: Boolean = false,
    @SerializedName("blocked_reason") val blockedReason: String? = null
)

@Singleton
class RutubeRepository @Inject constructor(
    private val prefs: Prefs
) {
    private val baseUrl = "https://rutube.ru/"

    private var cachedApiProxy: String? = null
    private var api: ApiService = buildApi(null)

    // ---------- Кеш JSON в памяти ----------
    @Volatile private var jsonSourceUrl: String? = null
    @Volatile private var jsonChannels: List<Channel>? = null
    @Volatile private var jsonStreamUrls: Map<String, String> = emptyMap()
    @Volatile private var jsonBlocked: Map<String, String> = emptyMap()
    @Volatile private var jsonDescriptions: Map<String, String> = emptyMap()
    @Volatile private var jsonCategories: Map<String, String> = emptyMap()
    @Volatile private var jsonAvatars: Map<String, String> = emptyMap()
    @Volatile private var jsonLoadedFlag: Boolean = false

    private fun buildApi(proxy: String?): ApiService {
        val client = ProxyUtil.buildClient(proxy)
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private suspend fun api(): ApiService {
        val snap = prefs.snapshot()
        val wanted = if (snap.apiProxyEnabled)
            ProxyUtil.normalize(snap.apiProxy) else null
        if (wanted != cachedApiProxy) {
            cachedApiProxy = wanted
            api = buildApi(wanted)
        }
        return api
    }

    private suspend fun <T> retry(
        attempts: Int = 3,
        initialDelayMs: Long = 400L,
        block: suspend () -> T
    ): T? {
        var lastEx: Exception? = null
        var delayMs = initialDelayMs
        repeat(attempts) { i ->
            try {
                return block()
            } catch (e: Exception) {
                lastEx = e
                if (i < attempts - 1) {
                    delay(delayMs)
                    delayMs *= 2
                }
            }
        }
        if (lastEx != null) {
            // не пробрасываем
        }
        return null
    }

    // ---------- JSON-источник ----------

    fun isJsonLoaded(): Boolean = jsonLoadedFlag

    suspend fun loadFromJson(url: String?): Boolean = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) {
            jsonLoadedFlag = false
            return@withContext false
        }
        if (jsonLoadedFlag && jsonSourceUrl == url && jsonChannels != null) {
            return@withContext true
        }

        val body = retry {
            val client = ProxyUtil.buildClient(null)
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().body?.string().orEmpty()
        } ?: return@withContext false

        if (body.isEmpty()) return@withContext false

        val parsed = runCatching {
            com.google.gson.Gson().fromJson(body, JsonChannelsResponse::class.java)
        }.getOrNull() ?: return@withContext false

        val rawChannels = parsed.channels ?: return@withContext false

        val channels = mutableListOf<Channel>()
        val streams = mutableMapOf<String, String>()
        val blocked = mutableMapOf<String, String>()
        val descriptions = mutableMapOf<String, String>()
        val categories = mutableMapOf<String, String>()
        val avatars = mutableMapOf<String, String>()

        for (jc in rawChannels) {
            val id = jc.id ?: continue
            val title = jc.title?.trim().orEmpty()
            if (title.isEmpty()) continue

            if (jc.blocked) {
                blocked[id] = jc.blockedReason ?: "видео недоступно"
                continue
            }

            channels += Channel(
                id = id,
                title = title,
                description = jc.description ?: "",
                category = jc.category?.takeIf { it.isNotBlank() },
                avatar = jc.avatar ?: ""
            )
            jc.streamUrl?.let { streams[id] = it }
            descriptions[id] = jc.description ?: ""
            categories[id] = jc.category ?: ""
            avatars[id] = jc.avatar ?: ""
        }

        jsonSourceUrl = url
        jsonChannels = channels
        jsonStreamUrls = streams
        jsonBlocked = blocked
        jsonDescriptions = descriptions
        jsonCategories = categories
        jsonAvatars = avatars
        jsonLoadedFlag = true
        true
    }

    fun clearJsonCache() {
        jsonSourceUrl = null
        jsonChannels = null
        jsonStreamUrls = emptyMap()
        jsonBlocked = emptyMap()
        jsonDescriptions = emptyMap()
        jsonCategories = emptyMap()
        jsonAvatars = emptyMap()
        jsonLoadedFlag = false
    }

    // ---------- Каналы ----------
    suspend fun fetchChannels(): List<Channel> = withContext(Dispatchers.IO) {
        jsonChannels?.let { return@withContext it }
        val raw = retry { api().getChannels() } ?: return@withContext emptyList()
        val synonyms = parseSynonyms(prefs.snapshot().synonyms)
        parseChannels(raw, synonyms)
    }

    private fun parseChannels(
        data: AutoWidgetResponse?,
        synonyms: Map<String, Pair<String?, String?>>
    ): List<Channel> {
        val out = mutableListOf<Channel>()
        val seen = mutableSetOf<String>()

        fun add(raw: AutoWidgetEntry?, group: String?) {
            if (raw == null) return
            val id = raw.id ?: return
            val title = raw.title?.trim().orEmpty()
            if (title.isEmpty()) return
            if (raw.duration != null && raw.duration != 0) return
            if (raw.origin_type == "ifrm") return
            if (id in seen) return
            seen += id

            val syn = synonyms[id]
            val finalTitle = syn?.first?.takeIf { it.isNotBlank() } ?: cleanTitle(title)
            val ownCat = raw.category.toNormalizedText().ifEmpty { null }
            val baseGroup = ownCat ?: group?.trim()?.ifEmpty { null }
            val finalGroup = syn?.second ?: baseGroup

            out += Channel(
                id = id,
                title = finalTitle,
                description = raw.description.toNormalizedText(),
                category = finalGroup?.let { cleanCategory(it) }
            )
        }

        if (data?.results != null) {
            for (e in data.results) {
                if (e.childs != null) {
                    e.childs.forEach { add(it, e.name) }
                } else {
                    add(e, null)
                }
            }
        } else if (data?.feed?.resources != null) {
            val res = data.feed.resources
            if (res.size > 1) res[1].items?.forEach { add(it, null) }
        }
        return out
    }

    // ---------- Инфо о канале ----------
    // ВАЖНО: если в JSON аватар пустой — идём в API за реальным аватаром.
    // Это нужно для каналов, где GitHub-парсер получил 404 (гео-блок).
    suspend fun fetchChannelInfo(id: String): ChannelInfo = withContext(Dispatchers.IO) {
        if (jsonLoadedFlag) {
            val blockedReason = jsonBlocked[id]
            if (blockedReason != null) {
                return@withContext ChannelInfo(
                    ok = true, blocked = true, reason = blockedReason
                )
            }
            val jsonAvatar = jsonAvatars[id] ?: ""
            val jsonDesc = jsonDescriptions[id] ?: ""
            val jsonCat = jsonCategories[id] ?: ""
            // Если в JSON всё есть — отдаём быстро, без API
            if (jsonAvatar.isNotBlank() && jsonDesc.isNotBlank()) {
                return@withContext ChannelInfo(
                    ok = true,
                    blocked = false,
                    description = jsonDesc,
                    category = jsonCat,
                    avatar = jsonAvatar
                )
            }
            // Иначе — fallthrough в API (за аватаром или описанием)
        }

        // Fallback — API
        val resp = retry { api().getPlayOptions(id) }
            ?: return@withContext ChannelInfo(ok = false)
        val blocked = isBlocked(resp)
        ChannelInfo(
            ok = true,
            blocked = blocked,
            reason = if (blocked) blockedReason(resp) else "",
            description = resp.description.toNormalizedText(),
            category = resp.category.toNormalizedText(),
            author = (resp.author as? Map<*, *>)?.get("name").toNormalizedText(),
            avatar = (resp.author as? Map<*, *>)?.get("avatar_url")?.toString().orEmpty()
        )
    }

    // ---------- Поток ----------
    suspend fun fetchStream(id: String, maxHeight: Int): ChannelInfo = withContext(Dispatchers.IO) {
        if (jsonLoadedFlag) {
            val blockedReason = jsonBlocked[id]
            if (blockedReason != null) {
                return@withContext ChannelInfo(
                    ok = true, blocked = true, reason = blockedReason
                )
            }
            val url = jsonStreamUrls[id]
            if (url != null) {
                val streamUrl = resolveStream(url, maxHeight)
                return@withContext ChannelInfo(
                    ok = true,
                    streamUrl = streamUrl,
                    description = jsonDescriptions[id] ?: "",
                    category = jsonCategories[id] ?: "",
                    avatar = jsonAvatars[id] ?: ""
                )
            }
            // URL нет — пробуем API fallback
        }

        // Fallback — API
        val resp = retry { api().getPlayOptions(id) }
            ?: return@withContext ChannelInfo(ok = false)
        val blocked = isBlocked(resp)
        if (blocked) {
            return@withContext ChannelInfo(
                ok = true, blocked = true, reason = blockedReason(resp)
            )
        }

        val hlsUrl = resp.live_streams?.hls?.firstOrNull()?.url
            ?: return@withContext ChannelInfo(ok = true, streamUrl = null)

        val streamUrl = resolveStream(hlsUrl, maxHeight)
        ChannelInfo(
            ok = true,
            streamUrl = streamUrl,
            description = resp.description.toNormalizedText(),
            category = resp.category.toNormalizedText(),
            author = (resp.author as? Map<*, *>)?.get("name").toNormalizedText(),
            avatar = (resp.author as? Map<*, *>)?.get("avatar_url")?.toString().orEmpty()
        )
    }

    private suspend fun resolveStream(hlsUrl: String, maxHeight: Int): String? {
        val body = retry {
            val client = ProxyUtil.buildClient(cachedApiProxy)
            val req = Request.Builder().url(hlsUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android TV)")
                .build()
            client.newCall(req).execute().body?.string().orEmpty()
        } ?: return null

        if (body.isEmpty()) return null

        if (!body.contains("#EXT-X-STREAM-INF") && body.contains("#EXTINF")) {
            return hlsUrl
        }

        val variants = M3u8Parser.parseVariants(body, hlsUrl)
        return M3u8Parser.pickBest(variants, maxHeight) ?: hlsUrl
    }

    // ---------- Скрытые / заблокированные ----------
    private fun isBlocked(resp: PlayOptionsResponse): Boolean {
        val t = resp.type
        if (t == "blocking_rule" || t == "player_stub") return true
        val detail = resp.detail
        if (detail is Map<*, *>) {
            val dt = detail["type"]?.toString()
            if (dt == "blocking_rule" || dt == "player_stub") return true
            val name = detail["name"]?.toString().orEmpty()
            if (name.startsWith("blocking_rule") || name == "login_required") return true
        }
        return false
    }

    private fun blockedReason(resp: PlayOptionsResponse): String {
        val detail = resp.detail as? Map<*, *>
        val name = detail?.get("name")?.toString().orEmpty()
        val type = resp.type ?: detail?.get("type")?.toString()
        return when {
            type == "blocking_rule" || name.startsWith("blocking_rule") ->
                "правообладатель или VPN"
            type == "player_stub" || name == "login_required" ->
                "скрыто автором (нужен вход)"
            else -> "видео недоступно"
        }
    }

    // ---------- Синонимы ----------
    private fun parseSynonyms(json: String): Map<String, Pair<String?, String?>> {
        return runCatching {
            val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
            val result = mutableMapOf<String, Pair<String?, String?>>()
            for ((k, v) in obj.entrySet()) {
                val arr = v.asJsonArray
                val titleEl = if (arr.size() > 0) arr.get(0) else null
                val groupEl = if (arr.size() > 1) arr.get(1) else null
                val title = if (titleEl != null && !titleEl.isJsonNull)
                    titleEl.asString else null
                val group = if (groupEl != null && !groupEl.isJsonNull)
                    groupEl.asString else null
                result[k] = title to group
            }
            result
        }.getOrDefault(emptyMap())
    }

    companion object {
        private const val TITLE_PREFIX = "Прямой эфир"
        private val CATEGORY_RENAMES = mapOf(
            "Все прямые эфиры" to "Прямые эфиры",
            "Новости и СМИ" to "Новости"
        )

        fun cleanTitle(s: String): String {
            val t = s.trim()
            val regex = Regex(
                "^${Regex.escape(TITLE_PREFIX)}[\\s:;,\\-–—]*",
                RegexOption.IGNORE_CASE
            )
            val m = regex.find(t) ?: return t
            val rest = t.substring(m.range.last + 1).trim()
            return if (rest.isNotEmpty()) rest else t
        }

        fun cleanCategory(s: String): String {
            val t = s.trim()
            return CATEGORY_RENAMES[t] ?: t
        }
    }
}
