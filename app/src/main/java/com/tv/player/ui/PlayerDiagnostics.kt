package com.tv.player.ui

import androidx.media3.common.PlaybackException

data class PlayerDiagnostics(
    val streamUrl: String = "—",
    val playbackState: String = "—",
    val isPlaying: Boolean = false,
    val videoSize: String = "—",
    val videoCodec: String = "—",
    val frameRate: Float = 0f,
    val bitrate: Int = 0,
    val audioCodec: String = "—",
    val bufferedMs: Long = 0,
    val currentPositionMs: Long = 0,
    val playbackSpeed: Float = 1f,
    val uptimeMs: Long = 0,
    val errorCode: Int = 0,
    val errorName: String = "—",
    val errorMessage: String = "",
    val retryCount: Int = 0,
    val lastEvent: String = "—"
)

fun errorCodeName(code: Int): String = when (code) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "IO_NETWORK_CONNECTION_FAILED"
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "IO_NETWORK_CONNECTION_TIMEOUT"
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "IO_BAD_HTTP_STATUS (403/404?)"
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "IO_FILE_NOT_FOUND"
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "IO_NO_PERMISSION"
    PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "IO_CLEARTEXT_NOT_PERMITTED"
    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> "IO_READ_POSITION_OUT_OF_RANGE"
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "PARSING_CONTAINER_MALFORMED"
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "PARSING_MANIFEST_MALFORMED"
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "PARSING_MANIFEST_UNSUPPORTED"
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "PARSING_CONTAINER_UNSUPPORTED"
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "DECODER_INIT_FAILED"
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> "DECODER_QUERY_FAILED"
    PlaybackException.ERROR_CODE_DECODING_FAILED -> "DECODING_FAILED"
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "DECODING_FORMAT_UNSUPPORTED"
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "FORMAT_EXCEEDS_CAPABILITIES"
    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> "AUDIO_TRACK_INIT_FAILED"
    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> "AUDIO_TRACK_WRITE_FAILED"
    PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED -> "VIDEO_FRAME_PROCESSING_FAILED"
    PlaybackException.ERROR_CODE_UNSPECIFIED -> "UNSPECIFIED"
    else -> "CODE_$code"
}

fun playbackStateName(state: Int): String = when (state) {
    1 -> "IDLE"
    2 -> "BUFFERING"
    3 -> "READY"
    4 -> "ENDED"
    else -> "STATE_$state"
}

fun formatBitrate(bps: Int): String = when {
    bps <= 0 -> "—"
    bps >= 1_000_000 -> "%.2f Mbps".format(bps / 1_000_000.0)
    bps >= 1_000 -> "${bps / 1000} kbps"
    else -> "$bps bps"
}

fun formatBuffer(ms: Long): String = when {
    ms <= 0 -> "0s"
    ms < 1000 -> "${ms}ms"
    else -> "%.1fs".format(ms / 1000.0)
}

fun formatUptime(ms: Long): String {
    val sec = ms / 1000
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
           else "%d:%02d".format(m, s)
}

fun codecShortName(mime: String?): String {
    if (mime.isNullOrBlank()) return "—"
    return when {
        mime.contains("avc") || mime.contains("h264") -> "H.264"
        mime.contains("hevc") || mime.contains("h265") -> "H.265"
        mime.contains("av01") || mime.contains("av1") -> "AV1"
        mime.contains("vp9") -> "VP9"
        mime.contains("vp8") -> "VP8"
        mime.contains("mp4a") -> "AAC"
        mime.contains("opus") -> "Opus"
        mime.contains("ac3") -> "AC-3"
        mime.contains("eac3") -> "E-AC-3"
        mime.contains("flac") -> "FLAC"
        mime.contains("mp3") -> "MP3"
        else -> mime.substringAfterLast("/").take(12)
    }
}
