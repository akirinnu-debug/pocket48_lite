package com.pocket48.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pocket48.app.data.model.LiveListItem
import com.pocket48.app.data.model.MemberInfo
import com.pocket48.app.data.model.formatRelativeTime
import com.pocket48.app.data.model.sourceUrl
import com.pocket48.app.viewmodel.LiveViewModel

/**
 * 最新直播列表页
 * 关注成员横向列表 + 直播列表无限滚动（复刻原版设计）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveListScreen(onLiveClick: (String) -> Unit) {
    val vm: LiveViewModel = viewModel()
    val state by vm.liveListState.collectAsState()
    val favoriteMembers by vm.favoriteMembers.collectAsState()
    val filterUserId by vm.filterUserId.collectAsState()

    // 日期选择弹窗状态 (单日选择, 简化自旧 DateRangePicker)
    var showDatePicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val locateSession by vm.locateSession.collectAsState()
    val locateResult by vm.locateResult.collectAsState()
    // 窗口视图独立的滚动状态 (避免污染主列表的 listState, 保护回弹修复)
    val windowListState = rememberLazyListState()

    // 仅在首次或数据为空时加载, 从播放页返回时不重新刷新 (保持 scroll 位置)
    LaunchedEffect(Unit) {
        val current = state
        if (current !is LiveViewModel.LiveListState.Success || current.items.isEmpty()) {
            vm.loadLiveList(refresh = true)
        }
    }

    // 定位结果窗口出现时, 滚动到目标项
    LaunchedEffect(locateResult) {
        val r = locateResult ?: return@LaunchedEffect
        if (r.targetIndex in 0..r.items.lastIndex) {
            windowListState.scrollToItem(r.targetIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("最新直播", fontWeight = FontWeight.SemiBold)
                        if (filterUserId != 0L && favoriteMembers.isNotEmpty()) {
                            val memberName = favoriteMembers.find { it.userId == filterUserId }
                            Text(
                                "筛选: ${memberName?.displayName ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                            )
                        }
                    }
                },
                actions = {
                    // 日期定位: 仅在选定成员后生效 (全部成员列表数据量太大, 翻页成本高)
                    IconButton(
                        onClick = { showDatePicker = true },
                        enabled = filterUserId != 0L,
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "按日期定位",
                            tint = if (filterUserId != 0L) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                    }
                    IconButton(onClick = { vm.loadLiveList(refresh = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
            )
        }
    ) { padding ->
        // 无限滚动
        val shouldLoadMore by remember {
            derivedStateOf {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = listState.layoutInfo.totalItemsCount
                totalItems > 0 && lastVisible >= totalItems - 3
            }
        }
        LaunchedEffect(shouldLoadMore) {
            if (shouldLoadMore && state is LiveViewModel.LiveListState.Success) {
                val s = state as LiveViewModel.LiveListState.Success
                if (s.hasMore && !s.loadingMore) vm.loadMore()
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 定位结果窗口视图 (locateResult 非空时替换主列表, 主列表状态原样保留)
            val window = locateResult
            if (window != null) {
                LocateResultWindow(
                    window = window,
                    windowListState = windowListState,
                    onLiveClick = onLiveClick,
                    onBackToLatest = { vm.dismissLocateResult() },
                    onLoadOlder = { vm.loadOlderInWindow() },
                )
            } else when (state) {
                is LiveViewModel.LiveListState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("加载中...", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
                        }
                    }
                }
                is LiveViewModel.LiveListState.Error -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text((state as LiveViewModel.LiveListState.Error).message, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { vm.loadLiveList(refresh = true) }) { Text("重试") }
                        }
                    }
                }
                is LiveViewModel.LiveListState.Success -> {
                    val successState = state as LiveViewModel.LiveListState.Success
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // 收藏成员横向列表（复刻原版设计）
                        if (favoriteMembers.isNotEmpty()) {
                            item {
                                FavoriteMembersHeader(
                                    members = favoriteMembers,
                                    selectedUserId = filterUserId,
                                    onMemberClick = { userId ->
                                        if (filterUserId == userId) {
                                            vm.clearFilter()
                                        } else {
                                            vm.filterByMember(userId)
                                        }
                                    },
                                    onClearFilter = { vm.clearFilter() },
                                )
                            }
                            item { Spacer(Modifier.height(4.dp)) }
                        }

                        // 标题栏
                        if (successState.items.isNotEmpty()) {
                            item {
                                Text(
                                    if (filterUserId != 0L) "直播" else "全部直播",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }

                        // 直播列表
                        items(successState.items) { item ->
                            LiveCard(item = item, onClick = { onLiveClick(item.liveId) })
                        }

                        // 底部加载更多
                        if (successState.hasMore) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (successState.loadingMore) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        TextButton(onClick = { vm.loadMore() }) {
                                            Text("加载更多")
                                        }
                                    }
                                }
                            }
                        } else if (successState.items.isNotEmpty()) {
                            item {
                                Text(
                                    "没有更多了",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        // 空状态
                        if (successState.items.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("暂无直播", color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 日期定位进度浮层 (底部居中) - 仅在定位任务运行中且尚未出窗口时显示
        val session = locateSession
        if (session != null && locateResult == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (session.cancelable) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                        Text(session.status, fontSize = 13.sp)
                        // Phase 2 起会有 estTotalPages, 此时显示百分比
                        if (session.estTotalPages != null && session.estTotalPages > 0) {
                            val pct = (session.progress * 100).toInt()
                            Text(
                                "$pct%",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (session.cancelable) {
                            TextButton(
                                onClick = { vm.cancelLocate() },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text("取消", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // 日期选择弹窗 (单日, 跳转到当天最近一期回放)
    if (showDatePicker) {
        val dateState = rememberDatePickerState()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMs = dateState.selectedDateMillis
                        if (selectedMs != null) {
                            // selectedMs 是当天 0 点 (UTC), 加 24h - 1ms 转为当天 23:59:59.999
                            vm.locateToDate(selectedMs + 24 * 60 * 60 * 1000L - 1)
                        }
                        showDatePicker = false
                    },
                    enabled = dateState.selectedDateMillis != null,
                ) { Text("跳转") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
            text = {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                ) {
                    DatePicker(state = dateState)
                }
            },
        )
    }
}

/** 收藏成员横向列表（复刻原版 FollowedMemberChip 设计） */
@Composable
private fun FavoriteMembersHeader(
    members: List<MemberInfo>,
    selectedUserId: Long,
    onMemberClick: (Long) -> Unit,
    onClearFilter: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "我的关注",
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (selectedUserId != 0L) {
                TextButton(
                    onClick = onClearFilter,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("显示全部", fontSize = 12.sp)
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(members) { member ->
                FavoriteChip(
                    member = member,
                    isSelected = member.userId == selectedUserId,
                    onClick = { onMemberClick(member.userId) },
                )
            }
        }
    }
}

/** 单个收藏成员芯片 */
@Composable
private fun FavoriteChip(
    member: MemberInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    Card(
        modifier = Modifier.width(72.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(6.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val avatarUrl = sourceUrl(member.avatar)
            if (avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = member.displayName,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        member.displayName.take(1),
                        fontSize = 16.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                member.displayName,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 直播卡片
 * @param isTarget 是否为定位目标项 (true 时加边框高亮) */
@Composable
private fun LiveCard(item: LiveListItem, onClick: () -> Unit, isTarget: Boolean = false) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTarget) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = if (isTarget) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val coverUrl = sourceUrl(item.coverPath)
            AsyncImage(
                model = coverUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .size(width = 80.dp, height = 60.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title.ifBlank { "无标题" },
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isTarget) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "目标",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                val memberName = item.userInfo?.let { it.starName.ifBlank { it.nickname } }?.ifBlank { item.createdName }.orEmpty()
                if (memberName.isNotBlank()) {
                    Text(
                        memberName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    formatRelativeTime(item.displayTime),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "播放",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/**
 * 日期定位结果窗口视图
 *
 * - 顶部: "← 返回最新" 按钮 (调 dismissLocateResult, 不触碰主列表)
 * - 中间: 窗口 items, 目标项高亮 (LiveCard isTarget=true)
 * - 底部: canLoadOlder 时显示 "加载更早" 按钮 (调 loadOlderInWindow)
 *
 * 使用独立的 windowListState, 避免污染主列表的 listState
 */
@Composable
private fun LocateResultWindow(
    window: com.pocket48.app.data.model.LocateWindow,
    windowListState: androidx.compose.foundation.lazy.LazyListState,
    onLiveClick: (String) -> Unit,
    onBackToLatest: () -> Unit,
    onLoadOlder: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = windowListState,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 顶部返回按钮
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onBackToLatest,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("返回最新")
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "定位结果 (${window.items.size} 条)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 窗口列表
        itemsIndexed(window.items) { index, item ->
            LiveCard(
                item = item,
                onClick = { onLiveClick(item.liveId) },
                isTarget = index == window.targetIndex,
            )
        }

        // 底部加载更早
        if (window.canLoadOlder) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TextButton(onClick = onLoadOlder) {
                        Text("加载更早")
                    }
                }
            }
        } else {
            item {
                Text(
                    "已到最早",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
