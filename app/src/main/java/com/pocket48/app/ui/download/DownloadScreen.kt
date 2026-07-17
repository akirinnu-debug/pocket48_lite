package com.pocket48.app.ui.download

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pocket48.app.data.model.DownloadItem
import com.pocket48.app.data.model.DownloadStatus
import com.pocket48.app.data.model.formatDuration
import com.pocket48.app.data.model.formatLiveDate
import com.pocket48.app.data.model.formatRelativeTime
import com.pocket48.app.data.model.sourceUrl
import com.pocket48.app.viewmodel.DownloadViewModel

/**
 * 下载列表页
 *
 * - 顶部右上: 全部暂停 / 全部恢复
 * - 列表项: 封面 + 标题 + 成员 + 状态 + 进度条
 * - 操作: 播放(已下载) / 暂停/恢复(进行中) / 删除
 * - 长按删除单条
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadScreen(onLiveClick: (String) -> Unit) {
    val vm: DownloadViewModel = viewModel()
    val downloads by vm.downloads.collectAsState()
    val statusMap by vm.statusMap.collectAsState()

    var deleteTarget by remember { mutableStateOf<DownloadItem?>(null) }

    val hasActive = statusMap.values.any { it.isDownloading }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("下载", fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (downloads.isNotEmpty()) {
                        IconButton(onClick = {
                            if (hasActive) vm.pauseAll() else vm.resumeAll()
                        }) {
                            Icon(
                                if (hasActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (hasActive) "全部暂停" else "全部恢复",
                            )
                        }
                    }
                },
            )
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "暂无下载任务",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "在直播详情页右上角点 \"下载\" 加入",
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(downloads, key = { it.liveId }) { item ->
                val status = statusMap[item.liveId]
                DownloadCard(
                    item = item,
                    status = status,
                    onClick = {
                        // 已下载才允许点击播放
                        if (status?.isCompleted == true) {
                            onLiveClick(item.liveId)
                        }
                    },
                    onLongPress = { deleteTarget = item },
                    onPlay = { onLiveClick(item.liveId) },
                    onPause = { vm.pauseDownload(item.liveId) },
                    onResume = { vm.resumeDownload(item.liveId) },
                    onDelete = { deleteTarget = item },
                )
            }
            item {
                val totalBytes = statusMap.values.sumOf { it.downloadedBytes }
                Text(
                    "共 ${downloads.size} 个任务 · 已缓存 ${formatBytes(totalBytes)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除下载") },
            text = {
                Text(
                    "将删除:\n${target.title.ifBlank { "无标题" }}\n\n同时清掉本地分片, 此操作不可撤销。",
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteDownload(target.liveId)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadCard(
    item: DownloadItem,
    status: DownloadStatus?,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val isCompleted = status?.isCompleted == true
    val isDownloading = status?.isDownloading == true
    val isPaused = status?.isPaused == true
    val isFailed = status?.isFailed == true
    val percent = status?.percent ?: 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 封面 (叠加状态图标)
                Box(
                    modifier = Modifier.size(width = 96.dp, height = 54.dp)
                        .clip(RoundedCornerShape(6.dp)),
                ) {
                    AsyncImage(
                        model = sourceUrl(item.coverPath),
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop,
                    )
                    // 左下角状态图标
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(2.dp)
                            .size(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    isCompleted -> Color(0xFF4CAF50)
                                    isFailed -> Color(0xFFF44336)
                                    isDownloading -> MaterialTheme.colorScheme.primary
                                    else -> Color(0xFF9E9E9E)
                                }.copy(alpha = 0.9f)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            when {
                                isCompleted -> Icons.Default.DownloadDone
                                isFailed -> Icons.Default.Delete
                                isDownloading -> Icons.Default.Download
                                else -> Icons.Default.Pause
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title.ifBlank { "无标题" },
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    // 成员昵称 + 直播日期 (同标题同封面的场次靠日期区分)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.userNickname.isNotBlank()) {
                            Text(
                                item.userNickname,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        if (item.liveDate > 0) {
                            if (item.userNickname.isNotBlank()) {
                                Text(
                                    " · ",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                            Text(
                                formatLiveDate(item.liveDate),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                maxLines = 1,
                            )
                        }
                    }
                    // 状态文本 + 大小 (单视频大小, 区别于底部总缓存大小)
                    val sizeSuffix = when {
                        status == null -> ""
                        isCompleted && status.totalBytes > 0 -> " · ${formatBytes(status.totalBytes)}"
                        isDownloading && status.totalBytes > 0 ->
                            " · ${formatBytes(status.downloadedBytes)} / ${formatBytes(status.totalBytes)}"
                        isPaused && status.totalBytes > 0 ->
                            " · ${formatBytes(status.downloadedBytes)} / ${formatBytes(status.totalBytes)}"
                        status.downloadedBytes > 0 -> " · 已缓存 ${formatBytes(status.downloadedBytes)}"
                        else -> ""
                    }
                    val stateText = when {
                        isCompleted -> "已下载 · 可离线播放"
                        isDownloading -> "下载中 ${percent.toInt()}%"
                        isPaused -> "已暂停 ${percent.toInt()}%"
                        isFailed -> "下载失败"
                        else -> "排队中"
                    } + sizeSuffix
                    Text(
                        stateText,
                        fontSize = 11.sp,
                        color = when {
                            isCompleted -> MaterialTheme.colorScheme.primary
                            isFailed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        },
                    )
                }
                // 右侧操作
                when {
                    isCompleted -> IconButton(onClick = onPlay) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "播放",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    isDownloading -> IconButton(onClick = onPause) {
                        Icon(Icons.Default.Pause, contentDescription = "暂停")
                    }
                    isPaused || isFailed -> IconButton(onClick = onResume) {
                        Icon(Icons.Default.Refresh, contentDescription = "继续")
                    }
                    else -> IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            }
            // 进度条 (未完成时显示)
            if (!isCompleted && percent > 0f) {
                LinearProgressIndicator(
                    progress = { (percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .padding(horizontal = 12.dp, vertical = 0.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (isFailed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 字节数格式化 */
private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / 1024 / 1024} MB"
    else -> String.format(java.util.Locale.CHINA, "%.2f GB", bytes / 1024.0 / 1024 / 1024)
}
