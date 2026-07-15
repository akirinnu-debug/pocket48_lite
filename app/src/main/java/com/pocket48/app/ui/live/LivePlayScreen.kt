package com.pocket48.app.ui.live

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocket48.app.ui.components.PlaybackPlayer
import com.pocket48.app.ui.danmaku.DanmakuOverlay
import com.pocket48.app.ui.danmaku.DanmakuPoolPanel
import com.pocket48.app.viewmodel.LiveViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivePlayScreen(liveId: String, onBack: () -> Unit) {
    val vm: LiveViewModel = viewModel()
    val state by vm.liveDetailState.collectAsState()

    var showDanmaku by remember { mutableStateOf(true) }
    var showDanmakuPool by remember { mutableStateOf(true) }
    var isFullScreen by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    LaunchedEffect(liveId) { vm.loadLiveDetail(liveId) }
    DisposableEffect(Unit) { onDispose { vm.resetDetail() } }

    // 全屏状态下拦截返回键 -> 退出全屏；非全屏时透传到 onBack (退出播放)
    BackHandler(enabled = isFullScreen) {
        isFullScreen = false
    }

    // 全屏时隐藏系统栏
    DisposableEffect(isFullScreen) {
        val activity = context as? Activity
        if (activity != null) {
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isFullScreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }

    Scaffold(
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                    title = {
                        val title = (state as? LiveViewModel.LiveDetailState.Success)?.detail?.title ?: "直播"
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                )
            }
        }
    ) { padding ->
        when (val s = state) {
            is LiveViewModel.LiveDetailState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is LiveViewModel.LiveDetailState.Success -> {
                val m3u8 = s.detail.playUrl
                Column(Modifier.fillMaxSize().padding(padding)) {
                    // 播放器 + 弹幕区域
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isFullScreen) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f))
                            .background(Color.Black)
                    ) {
                        PlaybackPlayer(
                            m3u8Url = m3u8,
                            modifier = Modifier.fillMaxSize(),
                            isFullScreen = isFullScreen,
                            onFullScreenToggle = { isFullScreen = !isFullScreen },
                            onPositionChanged = { positionMs = it },
                            onPlayingChanged = { isPlaying = it },
                            onError = { err -> playbackError = friendlyErrorMessage(err) },
                        )

                        // 错误提示覆盖层 (478 / 网络异常等 CDN 限制)
                        if (playbackError != null) {
                            PlaybackErrorOverlay(
                                message = playbackError!!,
                                onRetry = {
                                    playbackError = null
                                    vm.loadLiveDetail(liveId)
                                },
                            )
                        }
                        DanmakuOverlay(
                            lrcLines = s.lrcLines,
                            currentPositionMs = positionMs,
                            isPlaying = isPlaying,
                            showDanmaku = showDanmaku,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    // 底部: 弹幕控制 + 弹幕池
                    if (!isFullScreen) {
                        DanmakuControlBar(
                            showDanmaku = showDanmaku,
                            lrcCount = s.lrcLines.size,
                            onToggleDanmaku = { showDanmaku = !showDanmaku },
                            onPoolToggle = { showDanmakuPool = !showDanmakuPool },
                        )
                        DanmakuPoolPanel(
                            visible = showDanmakuPool,
                            lrcLines = s.lrcLines,
                            currentPositionMs = positionMs,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }
            is LiveViewModel.LiveDetailState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(s.message)
                }
            }
            LiveViewModel.LiveDetailState.Idle -> {}
        }
    }
}

/** 将 ExoPlayer 异常映射为可读中文消息 (478/超时/CDN 限流等) */
private fun friendlyErrorMessage(error: PlaybackException): String {
    val cause = error.cause
    val causeMsg = cause?.message.orEmpty()
    val statusCode = Regex("Response code: (\\d+)").find(causeMsg)?.groupValues?.getOrNull(1)
    return when {
        statusCode == "478" || statusCode == "403" -> "该回放已被服务器限制访问 (HTTP $statusCode)，可能为版权或风控原因"
        statusCode == "404" -> "回放文件已失效或被删除 (HTTP 404)"
        statusCode == "451" -> "该回放因地区限制无法播放 (HTTP 451)"
        error.errorCodeName.contains("BAD_HTTP_STATUS", ignoreCase = true) ->
            "服务器返回异常 (HTTP ${statusCode ?: "?"})，请稍后重试"
        error.errorCodeName.contains("NETWORK", ignoreCase = true) -> "网络连接失败，请检查网络后重试"
        error.errorCodeName.contains("TIMEOUT", ignoreCase = true) -> "加载超时，请重试"
        else -> "播放失败：${error.errorCodeName}"
    }
}

@Composable
private fun PlaybackErrorOverlay(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.size(12.dp))
            androidx.compose.material3.TextButton(
                onClick = onRetry,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(4.dp))
                Text("重试")
            }
        }
    }
}

@Composable
private fun DanmakuControlBar(
    showDanmaku: Boolean,
    lrcCount: Int,
    onToggleDanmaku: () -> Unit,
    onPoolToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = showDanmaku,
            onClick = onToggleDanmaku,
            label = { Text(if (showDanmaku) "弹幕开" else "弹幕关") },
            leadingIcon = {
                Icon(
                    if (showDanmaku) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                    contentDescription = null,
                    modifier = Modifier.height(18.dp),
                )
            },
        )
        if (lrcCount > 0) {
            Text(
                "$lrcCount 条弹幕",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable(onClick = onPoolToggle),
            )
        } else {
            Text(
                "无弹幕数据",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

