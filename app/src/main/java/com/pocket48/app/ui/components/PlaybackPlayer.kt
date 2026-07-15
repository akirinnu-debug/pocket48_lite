package com.pocket48.app.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.pocket48.app.data.VideoCacheManager
import kotlinx.coroutines.delay

/**
 * HLS 播放器 (带离线缓存)
 *
 * 播放时自动缓存 .ts 分片到本地, 同一直播再次播放直接走缓存
 * 缓存上限 2GB, LRU 自动淘汰最旧分片
 */
@Composable
fun PlaybackPlayer(
    m3u8Url: String,
    modifier: Modifier = Modifier,
    isFullScreen: Boolean = false,
    onFullScreenToggle: () -> Unit = {},
    onPositionChanged: (Long) -> Unit = {},
    onPlayingChanged: (Boolean) -> Unit = {},
    onError: (PlaybackException) -> Unit = {},
) {
    val context = LocalContext.current

    val player = remember {
        val dataSourceFactory = VideoCacheManager.cacheDataSourceFactory(context)
        val mediaSource: MediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(Uri.parse(m3u8Url)))

        ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }

    // 播放位置轮询 (用于弹幕同步)
    LaunchedEffect(player) {
        while (true) {
            delay(200)
            onPositionChanged(player.currentPosition)
            onPlayingChanged(player.isPlaying)
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlayingChanged(isPlaying)
            }
            override fun onPlayerError(error: PlaybackException) {
                onError(error)
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.apply {
                playWhenReady = false
                stop()
                release()
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    setPlayer(player)
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    keepScreenOn = true
                    defaultArtwork = null
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        IconButton(
            onClick = onFullScreenToggle,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(36.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.4f),
                contentColor = Color.White,
            ),
        ) {
            Icon(
                if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = if (isFullScreen) "退出全屏" else "全屏",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
