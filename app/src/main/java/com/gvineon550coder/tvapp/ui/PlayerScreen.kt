package com.gvineon550coder.tvapp.ui

import android.content.Context
import android.os.PowerManager
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.gvineon550coder.tvapp.PlayerService

private const val VIDEO_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; Android TV) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/120.0.0.0 Safari/537.36"

@Composable
fun PlayerScreen(
    streamUrl: String?,
    onResolutionChanged: (String) -> Unit = {},
    onPlayingChanged: (Boolean) -> Unit = {},
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
                // Запускаем Foreground Service — система обязана держать
                // процесс живым, пока играет прямой эфир
                runCatching { PlayerService.start(ctx) }

                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> runCatching { player.pause() }
                        Lifecycle.Event.ON_RESUME -> runCatching { player.play() }
                        Lifecycle.Event.ON_STOP -> runCatching { player.pause() }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)

                val listener = object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            onResolutionChanged("${videoSize.width}x${videoSize.height}")
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        onPlayingChanged(isPlaying)
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
                        if (!playing && wakeLock.isHeld) {
                            runCatching { wakeLock.release() }
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
                    // Останавливаем Foreground Service
                    runCatching { PlayerService.stop(ctx) }
                }
            }

            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player
                        // Полностью отключаем контроллер: никакой шкалы времени,
                        // никаких кнопок play/pause — для живого эфира они бесполезны
                        this.useController = false
                        // Убираем спиннер буферизации (крутящийся кружок)
                        this.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        // Не перехватывать фокус
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
