package com.gvineon550coder.tvapp.data

import java.net.URI

object M3u8Parser {

    fun parseVariants(text: String, baseUrl: String): List<StreamVariant> {
        val lines = text.split("\n")
        val out = mutableListOf<StreamVariant>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!line.startsWith("#EXT-X-STREAM-INF:")) { i++; continue }
            var url = ""
            var j = i + 1
            while (j < lines.size && lines[j].isBlank()) j++
            if (j < lines.size) url = lines[j].trim()
            if (url.isEmpty() || url.startsWith("#")) { i = j + 1; continue }
            if (!url.startsWith("http")) {
                url = runCatching { URI(baseUrl).resolve(url).toString() }
                    .getOrDefault(url)
            }
            val res = Regex("""RESOLUTION=(\d+)x(\d+)""").find(line)
            val bw = Regex("""BANDWIDTH=(\d+)""").find(line)
            out += StreamVariant(
                pixels = res?.let {
                    it.groupValues[1].toInt() * it.groupValues[2].toInt()
                } ?: 0,
                height = res?.groupValues?.get(2)?.toInt() ?: 0,
                bandwidth = bw?.groupValues?.get(1)?.toInt() ?: 0,
                url = url
            )
            i = j + 1
        }
        return out
    }

    fun limitHeight(streams: List<StreamVariant>, maxHeight: Int): List<StreamVariant> {
        if (maxHeight <= 0) return streams
        val fit = streams.filter { it.height in 1..maxHeight }
        if (fit.isNotEmpty()) return fit
        val known = streams.filter { it.height > 0 }
        if (known.isEmpty()) return streams
        val min = known.minOf { it.height }
        return known.filter { it.height == min }
    }

    /**
     * Максимум по pixels (или bandwidth), с приоритетом rtbcdn.ru — как в оригинале.
     */
    fun pickBest(variants: List<StreamVariant>, maxHeight: Int): String? {
        if (variants.isEmpty()) return null
        val limited = limitHeight(variants, maxHeight)
        if (limited.isEmpty()) return null
        val maxPixels = limited.maxOf { it.pixels }
        val cand = if (maxPixels > 0) {
            limited.filter { it.pixels == maxPixels }
        } else {
            val maxBw = limited.maxOf { it.bandwidth }
            limited.filter { it.bandwidth == maxBw }
        }.map { it.url }.distinct()
        return cand.firstOrNull { it.contains("rtbcdn.ru") } ?: cand.firstOrNull()
    }
}
