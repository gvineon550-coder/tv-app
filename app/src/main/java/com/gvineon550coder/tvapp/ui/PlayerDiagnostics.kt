package com.gvineon550coder.tvapp.ui

import androidx.media3.common.PlaybackException

/**
 * Снимок состояния плеера для диагностического оверлея.
 * Обновляется при каждом изменении через Player.Listener.
 */
data class PlayerDiagnostics(
    val streamUrl: String = "—",
    val playbackState: String = "—",
    val isPlaying: Boolean = false,
    val videoSize: String = "—",
    val bitrate: Int = 0,
    val bufferedMs: Long = 0,
    val errorCode: Int = 0,
    val errorName: String = "—",
    val errorMessage: String = "",
    val retryCount: Int = 0,
    val lastEvent: String = "—"
)

/**
 * Читаемое имя кода ошибки ExoPlayer.
 * Используется для оверлея — чтобы не смотреть в сырые числа.
 */
fun errorCodeName(code: Int): String = when (code) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
        "IO_NETWORK_CONNECTION_FAILED"
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
        "IO_NETWORK_CONNECTION_TIMEOUT"
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
        "IO_BAD_HTTP_STATUS (403/404?)"
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
        "IO_FILE_NOT_FOUND"
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
        "IO_NO_PERMISSION"
    PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
        "IO_CLEARTEXT_NOT_PERMITTED"
    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ->
        "IO_READ_POSITION_OUT_OF_RANGE"
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
        "PARSING_CONTAINER_MALFORMED"
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
        "PARSING_MANIFEST_MALFORMED"
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
        "PARSING_MANIFEST_UNSUPPORTED"
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
        "PARSING_CONTAINER_UNSUPPORTED"
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
        "DECODER_INIT_FAILED"
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
        "DECODER_QUERY_FAILED"
    PlaybackException.ERROR_CODE_DECODING_FAILED ->
        "DECODING_FAILED"
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
        "DECODING_FORMAT_UNSUPPORTED (кодек?)"
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
        "FORMAT_EXCEEDS_CAPABILITIES"
    PlaybackException.ERROR_CODE_RENDERER_FAILED ->
        "RENDERER_FAILED"
    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ->
        "AUDIO_TRACK_INIT_FAILED"
    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ->
        "AUDIO_TRACK_WRITE_FAILED"
    PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED ->
        "VIDEO_FRAME_PROCESSING_FAILED"
    PlaybackException.ERROR_CODE_UNSPECIFIED ->
        "UNSPECIFIED"
    else -> "CODE_$code"
}

/** Читаемое имя состояния плеера. */
fun playbackStateName(state: Int): String = when (state) {
    1 -> "IDLE"
    2 -> "BUFFERING"
    3 -> "READY"
    4 -> "ENDED"
    else -> "STATE_$state"
}

/** Форматирует битрейт в читаемый вид. */
fun formatBitrate(bps: Int): String = when {
    bps <= 0 -> "—"
    bps >= 1_000_000 -> "%.1f Mbps".format(bps / 1_000_000.0)
    bps >= 1_000 -> "${bps / 1000} kbps"
    else -> "$bps bps"
}

/** Форматирует буфер в секунды. */
fun formatBuffer(ms: Long): String = when {
    ms <= 0 -> "0s"
    ms < 1000 -> "${ms}ms"
    else -> "%.1fs".format(ms / 1000.0)
}
