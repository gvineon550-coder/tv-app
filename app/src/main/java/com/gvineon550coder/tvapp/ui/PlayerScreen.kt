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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

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
            val player = remember(streamUrl) {
                ExoPlayer.Builder(ctx).build().apply {
                    setMediaItem(MediaItem.fromUri(streamUrl))
                    prepare()
                    playWhenReady = true
                }
            }
            DisposableEffect(streamUrl, player) {
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
                    player.removeListener(listener)
                    onPlayingChanged(false)
                    player.release()
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
