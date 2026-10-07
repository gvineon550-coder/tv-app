package com.tv.player.ui

import android.content.Context
import android.os.PowerManager
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tv.player.PlayerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val VIDEO_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; Android TV) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/120.0.0.0 Safari/537.36"

private const val MAX_RETRIES = 3
private const val RETRY_DELAY_MS = 3_000L

@Composable
fun PlayerScreen(
    streamUrl: String?,
    onResolutionChanged: (String) -> Unit = {},
    onPlayingChanged: (Boolean) -> Unit = {},
    onFatalError: (() -> Unit)? = null,
    showDiagnostics: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (streamUrl == null) {
            Text(
                "Выберите канал",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            val ctx = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val scope = rememberCoroutineScope()

            var retryCount by remember(streamUrl) { mutableIntStateOf(0) }
            val diagnostics = remember {
                mutableStateOf(PlayerDiagnostics(streamUrl = streamUrl))
            }

            val wakeLock = remember {
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "tvapp:playback"
                ).apply {
                    setReferenceCounted(false)
                }
            }

            val player = remember(streamUrl) {
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(VIDEO_USER_AGENT)
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15_000)
                    .setReadTimeoutMs(20_000)

                val hlsFactory = HlsMediaSource.Factory(httpFactory)

                ExoPlayer.Builder(ctx).build().apply {
                    setMediaSource(
                        hlsFactory.createMediaSource(MediaItem.fromUri(streamUrl))
                    )
                    prepare()
                    playWhenReady = true
                }
            }

            DisposableEffect(streamUrl, player, lifecycleOwner) {
                runCatching { PlayerService.start(ctx) }

                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> runCatching { player.pause() }
                        Lifecycle.Event.ON_RESUME -> {
                            runCatching { player.play() }
                            runCatching { PlayerService.start(ctx) }
                        }
                        Lifecycle.Event.ON_STOP -> {
                            runCatching { player.pause() }
                            runCatching { PlayerService.stop(ctx) }
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)

                val listener = object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            val res = "${videoSize.width}x${videoSize.height}"
                            onResolutionChanged(res)
                            diagnostics.value = diagnostics.value.copy(
                                videoSize = res,
                                lastEvent = "videoSize=$res"
                            )
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        onPlayingChanged(isPlaying)
                        diagnostics.value = diagnostics.value.copy(
                            isPlaying = isPlaying,
                            lastEvent = if (isPlaying) "playing" else "paused"
                        )
                        if (isPlaying) {
                            if (!wakeLock.isHeld) {
                                runCatching { wakeLock.acquire(6 * 60 * 60 * 1000L) }
                            }
                        } else {
                            if (wakeLock.isHeld) {
                                runCatching { wakeLock.release() }
                            }
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val playing = player.isPlaying &&
                                playbackState == Player.STATE_READY
                        onPlayingChanged(playing)

                        if (playbackState == Player.STATE_READY) {
                            retryCount = 0
                        }

                        diagnostics.value = diagnostics.value.copy(
                            playbackState = playbackStateName(playbackState),
                            bitrate = player.videoFormat?.bitrate ?: 0,
                            bufferedMs = player.bufferedPosition,
                            retryCount = retryCount,
                            lastEvent = "state=${playbackStateName(playbackState)}"
                        )

                        if (!playing && wakeLock.isHeld) {
                            runCatching { wakeLock.release() }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val name = errorCodeName(error.errorCode)
                        diagnostics.value = diagnostics.value.copy(
                            errorCode = error.errorCode,
                            errorName = name,
                            errorMessage = error.message ?: error.cause?.message ?: "",
                            retryCount = retryCount,
                            lastEvent = "error=$name"
                        )

                        if (retryCount < MAX_RETRIES) {
                            retryCount++
                            diagnostics.value = diagnostics.value.copy(
                                retryCount = retryCount,
                                lastEvent = "retry #$retryCount"
                            )
                            scope.launch {
                                delay(RETRY_DELAY_MS)
                                runCatching {
                                    player.prepare()
                                    player.playWhenReady = true
                                }
                            }
                        } else {
                            onFatalError?.invoke()
                        }
                    }
                }
                player.addListener(listener)

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    player.removeListener(listener)
                    onPlayingChanged(false)
                    runCatching {
                        player.stop()
                        player.release()
                    }
                    runCatching {
                        if (wakeLock.isHeld) wakeLock.release()
                    }
                    runCatching { PlayerService.stop(ctx) }
                }
            }

            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player
                        this.useController = false
                        this.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (showDiagnostics) {
                DiagnosticsOverlay(
                    d = diagnostics.value,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                )
            }
        }
    }
}

/**
 * Диагностический оверлей — плашка в левом верхнем углу.
 * Видна только при showDiagnostics = true.
 */
@Composable
private fun DiagnosticsOverlay(
    d: PlayerDiagnostics,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xCC000000), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text(
            text = buildString {
                appendLine("URL: …${d.streamUrl.takeLast(50)}")
                appendLine("state: ${d.playbackState}  play=${d.isPlaying}")
                appendLine("video: ${d.videoSize}  br=${formatBitrate(d.bitrate)}")
                appendLine("buffer: ${formatBuffer(d.bufferedMs)}")
                appendLine("error: ${d.errorName}")
                if (d.errorMessage.isNotBlank())
                    appendLine("msg: ${d.errorMessage.take(80)}")
                appendLine("retry: ${d.retryCount}/$MAX_RETRIES")
                append("last: ${d.lastEvent}")
            },
            color = Color(0xFF7CFF7C),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
