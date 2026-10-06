package com.gvineon550coder.tvapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3u8ParserTest {

    @Test
    fun `parseVariants extracts basic stream`() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=1280x720
            https://cdn.example.com/720p.m3u8
        """.trimIndent()

        val result = M3u8Parser.parseVariants(playlist, "https://cdn.example.com/")

        assertEquals(1, result.size)
        val v = result[0]
        assertEquals(1280 * 720, v.pixels)
        assertEquals(720, v.height)
        assertEquals(1_000_000, v.bandwidth)
        assertEquals("https://cdn.example.com/720p.m3u8", v.url)
    }

    @Test
    fun `parseVariants extracts multiple streams`() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=854x480
            https://cdn.example.com/480p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1920x1080
            https://cdn.example.com/1080p.m3u8
        """.trimIndent()

        val result = M3u8Parser.parseVariants(playlist, "https://cdn.example.com/")

        assertEquals(2, result.size)
        assertEquals(480, result[0].height)
        assertEquals(1080, result[1].height)
    }

    @Test
    fun `parseVariants resolves relative urls`() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=1280x720
            /streams/720p.m3u8
        """.trimIndent()

        val result = M3u8Parser.parseVariants(playlist, "https://cdn.example.com/root/")

        assertEquals(1, result.size)
        assertEquals("https://cdn.example.com/streams/720p.m3u8", result[0].url)
    }

    @Test
    fun `parseVariants returns empty for media playlist`() {
        val playlist = """
            #EXTM3U
            #EXTINF:10.0,
            segment1.ts
            #EXTINF:10.0,
            segment2.ts
        """.trimIndent()

        val result = M3u8Parser.parseVariants(playlist, "https://cdn.example.com/")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseVariants handles bandwidth only without resolution`() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1500000
            https://cdn.example.com/unknown.m3u8
        """.trimIndent()

        val result = M3u8Parser.parseVariants(playlist, "https://cdn.example.com/")

        assertEquals(1, result.size)
        assertEquals(0, result[0].height)
        assertEquals(1_500_000, result[0].bandwidth)
    }

    @Test
    fun `limitHeight filters streams above limit`() {
        val variants = listOf(
            variant(480, 500_000),
            variant(720, 1_000_000),
            variant(1080, 2_000_000),
            variant(1440, 4_000_000)
        )

        val result = M3u8Parser.limitHeight(variants, 1080)

        assertEquals(3, result.size)
        assertTrue(result.all { it.height in 1..1080 })
    }

    @Test
    fun `limitHeight returns all when limit is zero`() {
        val variants = listOf(variant(480, 500_000), variant(1080, 2_000_000))

        val result = M3u8Parser.limitHeight(variants, 0)

        assertEquals(2, result.size)
    }

    @Test
    fun `limitHeight returns smallest when none fit`() {
        val variants = listOf(
            variant(720, 1_000_000),
            variant(1080, 2_000_000),
            variant(1440, 4_000_000)
        )

        val result = M3u8Parser.limitHeight(variants, 480)

        assertEquals(1, result.size)
        assertEquals(720, result[0].height)
    }

    @Test
    fun `pickBest returns highest quality within limit`() {
        val variants = listOf(
            variant(480, 500_000, "https://cdn/480.m3u8"),
            variant(720, 1_000_000, "https://cdn/720.m3u8"),
            variant(1080, 2_000_000, "https://cdn/1080.m3u8")
        )

        val result = M3u8Parser.pickBest(variants, 720)

        assertEquals("https://cdn/720.m3u8", result)
    }

    @Test
    fun `pickBest prefers rtbcdn`() {
        val variants = listOf(
            variant(1080, 2_000_000, "https://other-cdn.com/1080.m3u8"),
            variant(1080, 2_000_000, "https://rtbcdn.ru/1080.m3u8")
        )

        val result = M3u8Parser.pickBest(variants, 1080)

        assertEquals("https://rtbcdn.ru/1080.m3u8", result)
    }

    @Test
    fun `pickBest returns null for empty list`() {
        val result = M3u8Parser.pickBest(emptyList(), 1080)
        assertNull(result)
    }

    @Test
    fun `pickBest falls back to bandwidth when no resolution`() {
        val variants = listOf(
            variant(0, 500_000, "https://cdn/low.m3u8"),
            variant(0, 2_000_000, "https://cdn/high.m3u8"),
            variant(0, 1_000_000, "https://cdn/mid.m3u8")
        )

        val result = M3u8Parser.pickBest(variants, 0)

        assertEquals("https://cdn/high.m3u8", result)
    }

    private fun variant(
        height: Int,
        bandwidth: Int,
        url: String = "https://cdn.example.com/${height}p.m3u8"
    ): StreamVariant = StreamVariant(
        pixels = if (height > 0) height * 16 / 9 * height else 0,
        height = height,
        bandwidth = bandwidth,
        url = url
    )
}
