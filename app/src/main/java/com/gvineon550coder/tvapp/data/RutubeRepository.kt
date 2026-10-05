package com.gvineon550coder.tvapp.data

import com.gvineon550coder.tvapp.util.ProxyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RutubeRepository @Inject constructor(
    private val prefs: Prefs
) {
    private val baseUrl = "https://rutube.ru/"

    private var cachedApiProxy: String? = null
    private var api: ApiService = buildApi(null)

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

    // ---------- Каналы ----------
    suspend fun fetchChannels(): List<Channel> = withContext(Dispatchers.IO) {
        val raw = runCatching { api().getChannels() }.getOrNull()
            ?: return@withContext emptyList()
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
    suspend fun fetchChannelInfo(id: String): ChannelInfo = withContext(Dispatchers.IO) {
        val resp = runCatching { api().getPlayOptions(id) }.getOrNull()
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
        val resp = runCatching { api().getPlayOptions(id) }.getOrNull()
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

    private fun resolveStream(hlsUrl: String, maxHeight: Int): String? {
        return runCatching {
            val client = ProxyUtil.buildClient(cachedApiProxy)
            val req = Request.Builder().url(hlsUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android TV)")
                .build()
            val body = client.newCall(req).execute().body?.string().orEmpty()
            if (body.isEmpty()) return@runCatching null
            if (!body.contains("#EXT-X-STREAM-INF") && body.contains("#EXTINF")) {
                return@runCatching hlsUrl
            }
            val variants = M3u8Parser.parseVariants(body, hlsUrl)
            M3u8Parser.pickBest(variants, maxHeight) ?: hlsUrl
        }.getOrNull()
    }

    // ---------- Программа передач ----------
    suspend fun fetchProgram(id: String, date: LocalDate): List<Program> = withContext(Dispatchers.IO) {
        val raw = runCatching { api().getProgram(id, date.toString()) }.getOrNull()
            ?: return@withContext emptyList()
        parseProgram(raw, date)
    }

    private fun parseProgram(data: Map<String, Any>, baseDate: LocalDate): List<Program> {
        val list = findProgramList(data) ?: return emptyList()
        val out = mutableListOf<Program>()
        for (entry in list) {
            if (entry !is Map<*, *>) continue
            val title = firstString(entry, LIST_OF_TITLE_KEYS) ?: continue
            val start = pickTime(entry, START_KEYS, baseDate)
            val end = pickTime(entry, END_KEYS, start?.toLocalDate() ?: baseDate)
            val desc = firstString(entry, DESC_KEYS) ?: ""
            out += Program(title, start, end, desc)
        }
        return out.sortedWith(compareBy(nullsLast()) { it.start })
    }

    private fun findProgramList(data: Any?, depth: Int = 0): List<*>? {
        if (depth > 6) return null
        when (data) {
            is List<*> -> {
                if (data.isEmpty()) return null
                val looks = data.count {
                    it is Map<*, *> && hasAny(it, LIST_OF_TITLE_KEYS)
                }
                if (looks * 2 >= data.size && looks > 0) return data
                data.forEach { findProgramList(it, depth + 1)?.let { r -> return r } }
            }
            is Map<*, *> -> {
                val pref = mutableListOf<Any?>()
                val rest = mutableListOf<Any?>()
                for ((k, v) in data) {
                    if (v !is Map<*, *> && v !is List<*>) continue
                    if (k in LIST_KEYS) pref += v else rest += v
                }
                (pref + rest).forEach {
                    findProgramList(it, depth + 1)?.let { r -> return r }
                }
            }
        }
        return null
    }

    private fun hasAny(map: Map<*, *>, keys: Set<String>): Boolean =
        keys.any { map[it] != null && map[it].toString().isNotBlank() }

    private fun firstString(map: Map<*, *>, keys: Set<String>): String? {
        for (k in keys) {
            val v = map[k] ?: continue
            if (v is Map<*, *>) {
                val inner = v["rus"] ?: v["ru"] ?: v["name"] ?: v.values.firstOrNull()
                if (inner != null) return inner.toString().trim()
            }
            val s = v.toString().trim()
            if (s.isNotEmpty()) return s
        }
        return null
    }

    private fun pickTime(
        map: Map<*, *>,
        keys: Set<String>,
        baseDate: LocalDate?
    ): java.time.LocalDateTime? {
        for (k in keys) {
            val v = map[k] ?: continue
            parseProgramTime(v, baseDate)?.let { return it }
        }
        return null
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

        private val LIST_OF_TITLE_KEYS = setOf(
            "title", "name", "program_title", "event_name",
            "show_title", "caption", "topic"
        )
        private val START_KEYS = setOf(
            "start", "start_time", "begin_time", "begin",
            "start_date", "begin_date", "air_time", "time", "date"
        )
        private val END_KEYS = setOf(
            "stop", "end_time", "end", "finish_time", "finish",
            "stop_time", "end_date", "finish_date"
        )
        private val DESC_KEYS = setOf(
            "description", "desc", "details", "annotation",
            "about", "text", "subtitle"
        )
        private val LIST_KEYS = setOf(
            "results", "items", "programs", "program", "schedule",
            "tv_program", "broadcasts", "events", "list", "data", "result"
        )
    }
}
