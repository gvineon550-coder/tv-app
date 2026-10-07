package com.tv.player.data

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Immutable
data class Channel(
    val id: String,
    val title: String,
    val description: String = "",
    val category: String? = null,
    val avatar: String = ""
)

data class ChannelInfo(
    val ok: Boolean = false,
    val blocked: Boolean = false,
    val reason: String = "",
    val streamUrl: String? = null,
    val description: String = "",
    val category: String = "",
    val author: String = "",
    val avatar: String = ""
)

@Immutable
data class Program(
    val title: String,
    val start: LocalDateTime?,
    val end: LocalDateTime?,
    val desc: String = ""
)

@Immutable
data class StreamVariant(
    val pixels: Int,
    val height: Int,
    val bandwidth: Int,
    val url: String
)

data class AutoWidgetResponse(
    val results: List<AutoWidgetEntry>? = null,
    val feed: AutoWidgetFeed? = null
)

data class AutoWidgetEntry(
    val id: String? = null,
    val title: String? = null,
    val description: Any? = null,
    val category: Any? = null,
    val duration: Any? = null,
    val origin_type: String? = null,
    val name: String? = null,
    val childs: List<AutoWidgetEntry>? = null
)

data class AutoWidgetFeed(
    val resources: List<AutoWidgetResource>? = null
)

data class AutoWidgetResource(
    val items: List<AutoWidgetEntry>? = null
)

data class PlayOptionsResponse(
    val video: Any? = null,
    val description: Any? = null,
    val category: Any? = null,
    val author: Any? = null,
    val live_streams: LiveStreams? = null,
    val type: String? = null,
    val detail: Any? = null
)

data class LiveStreams(
    val hls: List<HlsEntry>? = null
)

data class HlsEntry(
    val url: String? = null
)

fun Any?.toNormalizedText(): String = when (this) {
    null -> ""
    is String -> this.trim()
    is Map<*, *> -> (get("name") as? String)?.trim() ?: ""
    else -> toString().trim()
}

fun parseProgramTime(value: Any?, baseDay: LocalDate? = null): LocalDateTime? {
    if (value == null) return null
    val zone = ZoneId.systemDefault()

    if (value is Number) return epochToLocal(value.toLong(), zone)

    val s = value.toString().trim()
    if (s.isEmpty()) return null

    if (s.matches(Regex("""\d{9,14}"""))) {
        return epochToLocal(s.toLong(), zone)
    }

    Regex("""^(\d{1,2}):(\d{2})(?::(\d{2}))?$""").matchEntire(s)?.let { m ->
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val sec = m.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toInt() ?: 0
        val day = baseDay ?: LocalDate.of(1900, 1, 1)
        return LocalDateTime.of(day.year, day.monthValue, day.dayOfMonth, h, min, sec)
    }

    runCatching {
        val iso = s.replace("Z", "+00:00")
        val odt = java.time.OffsetDateTime.parse(iso)
        return odt.atZoneSameInstant(zone).toLocalDateTime()
    }

    runCatching {
        val ldt = LocalDateTime.parse(s)
        return ldt.atZone(java.time.ZoneOffset.UTC)
            .withZoneSameInstant(zone).toLocalDateTime()
    }

    runCatching {
        val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        val ldt = LocalDateTime.parse(s, fmt)
        return ldt.atZone(java.time.ZoneOffset.UTC)
            .withZoneSameInstant(zone).toLocalDateTime()
    }

    runCatching {
        val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        return LocalDate.parse(s, fmt).atStartOfDay()
    }

    return null
}

private fun epochToLocal(epoch: Long, zone: ZoneId): LocalDateTime {
    val sec = if (epoch > 1_000_000_000_000L) epoch / 1000L else epoch
    return LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(sec), zone)
}
