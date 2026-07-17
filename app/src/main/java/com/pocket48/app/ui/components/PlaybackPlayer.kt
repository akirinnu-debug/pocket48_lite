package com.pocket48.app.ui.components

import android.net.Uri
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.pocket48.app.data.VideoCacheManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 视频播放器 (HLS + MP4, 带离线缓存 + 续播 + 长按倍速)
 *
 * - .m3u8 → HlsMediaSource (新回放)
 * - .mp4 / 其他 → ProgressiveMediaSource (旧回放)
 * - initialPositionMs > 0 时, prepare 完成后自动 seek 到续播位置
 * - 长按屏幕 400ms 进入 2x 倍速, 松手恢复 1x (角标提示)
 * 播放时自动缓存分片到本地, 同一直播再次播放直接走缓存
 * 缓存上限 2GB, LRU 自动淘汰最旧分片
 */
@Composable
fun PlaybackPlayer(
    m3u8Url: String,
    modifier: Modifier = Modifier,
    isFullScreen: Boolean = false,
    initialPositionMs: Long = 0L,
    onFullScreenToggle: () -> Unit = {},
    onPositionChanged: (Long) -> Unit = {},
    onPlayingChanged: (Boolean) -> Unit = {},
    onDurationChanged: (Long) -> Unit = {},
    onError: (PlaybackException) -> Unit = {},
) {
    val context = LocalContext.current

    val player = remember {
        val dataSourceFactory = VideoCacheManager.cacheDataSourceFactory(context)
        val mediaItem = MediaItem.fromUri(Uri.parse(m3u8Url))
        val mediaSource: MediaSource = if (m3u8Url.endsWith(".m3u8", ignoreCase = true)) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }

        ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            prepare()
            // 续播: 在 prepare 后 seek (ExoPlayer 会在 STATE_READY 后定位)
            if (initialPositionMs > 0) {
                seekTo(initialPositionMs)
            }
            playWhenReady = true
        }
    }

    // 倍速状态 (长按触发)
    var isLongPressSpeed by remember { mutableStateOf(false) }

    // 播放位置 + 时长轮询 (用于弹幕同步 + 历史记录续播)
    LaunchedEffect(player) {
        while (true) {
            delay(200)
            onPositionChanged(player.currentPosition)
            onPlayingChanged(player.isPlaying)
            val dur = player.duration
            if (dur > 0) onDurationChanged(dur)
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

    // 长按屏幕倍速: 400ms 触发 2x, 松手恢复 1x
    // 单纯拦截长按事件, 不影响 PlayerView 自带的单击显示/隐藏 controller
    //
    // 关键: PlayerView 会消费 UP 事件 (用于切换 controller), 默认的 Main pass 看不到 UP
    // → 单击会被误判为 400ms 超时 → 误触发长按倍速
    // 解决: 用 PointerEventPass.Initial 监听事件, Initial 在消费前触发, 能感知所有 UP
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
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        // 400ms 内若抬手 → 视为普通点击 (交给 PlayerView 显示/隐藏 controller)
                        // 用 PointerEventPass.Initial 监听, 不受 PlayerView 消费影响
                        val up = withTimeoutOrNull(400L) {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.any { it.changedToUp() }) {
                                    return@withTimeoutOrNull true
                                }
                            }
                        }
                        if (up == null) {
                            // 长按触发: 进入 2x 倍速
                            isLongPressSpeed = true
                            player.playbackParameters = PlaybackParameters(2.0f)
                            // 阻塞等待真正抬手 (此时手指仍按住)
                            withTimeoutOrNull(60_000L) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.changes.any { it.changedToUp() }) break
                                }
                            }
                            isLongPressSpeed = false
                            player.playbackParameters = PlaybackParameters(1.0f)
                        }
                        // 不消费事件, 让 PlayerView 仍能收到 ACTION_DOWN/MOVE 显示 controller
                    }
                },
        )

        // 倍速角标 (左上角, 仅长按激活时显示)
        if (isLongPressSpeed) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.FastForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "2.0x 加速中",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

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
