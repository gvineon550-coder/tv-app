package com.gvineon550coder.tvapp.ui

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
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

// Тот же UA, что и в ProxyUtil — держим в одном стиле
private const val VIDEO_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; Android TV) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/120.0.0.0 Safari/537.36"

@Composable
fun PlayerScreen(
    streamUrl: String?,
    onResolutionChanged: (String) -> Unit = {},
    onPlayingChanged: (Boolean) -> Unit = {},
    useController: Boolean = true,
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

            val player = remember(streamUrl) {
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(VIDEO_USER_AGENT)
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15_000)
                    .setReadTimeoutMs(20_000)

                val hlsFactory = HlsMediaSource.Factory(httpFactory)
                val mediaSource: MediaSource = hlsFactory.createMediaSource(
                    MediaItem.fromUri(streamUrl)
                )

                ExoPlayer.Builder(ctx)
                    .setMediaSourceFactory { hlsFactory }
                    .build()
                    .apply {
                        setMediaSource(mediaSource)
                        prepare()
                        playWhenReady = true
                    }
            }

            DisposableEffect(streamUrl, player, lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> runCatching { player.pause() }
                        Lifecycle.Event.ON_RESUME -> runCatching { player.play() }
                        Lifecycle.Event.ON_STOP -> runCatching {
                            player.pause(); player.stop()
                        }
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
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val playing = player.isPlaying &&
                                playbackState == Player.STATE_READY
                        onPlayingChanged(playing)
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
                }
            }

            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player
                        this.useController = useController
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
