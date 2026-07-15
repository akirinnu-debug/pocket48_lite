package com.pocket48.app.ui.members

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.pocket48.app.data.model.MemberInfo
import com.pocket48.app.data.model.MemberStatus
import com.pocket48.app.data.model.sourceUrl
import com.pocket48.app.viewmodel.Level1Mode
import com.pocket48.app.viewmodel.MemberViewModel

private fun Long.toColor(): Color = Color(this)

private val STATUS_COLORS = MemberStatus.entries.associateWith { it.color.toColor() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberListScreen() {
    val vm: MemberViewModel = viewModel()
    val members by vm.filteredMembers.collectAsState()
    val grouped by vm.groupedMembers.collectAsState()
    val favorites by vm.favoriteIds.collectAsState()
    val hasFavorites by vm.hasFavorites.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val level1 by vm.level1Mode.collectAsState()
    val selectedAbbr by vm.selectedAbbr.collectAsState()
    val selectedTeam by vm.selectedTeam.collectAsState()
    val expandedGroups by vm.groupsExpanded.collectAsState()
    val abbrs by vm.availableAbbrs.collectAsState()
    val teams by vm.availableTeams.collectAsState()
    val isLoading by vm.isLoading.collectAsState()

    val isFiltered = selectedAbbr.isNotEmpty() || selectedTeam.isNotEmpty() || level1 != Level1Mode.ACTIVE

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("成员 (${members.size})", style = MaterialTheme.typography.titleMedium)
                        if (isFiltered) {
                            Text(
                                buildString {
                                    if (level1 == Level1Mode.ALL) append("全部")
                                    if (level1 == Level1Mode.FAVORITES) append("关注")
                                    if (selectedAbbr.isNotEmpty()) append(" · $selectedAbbr")
                                    if (selectedTeam.isNotEmpty()) append(" · $selectedTeam")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                actions = {
                    if (isFiltered) {
                        TextButton(onClick = { vm.clearFilters() }) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("清空", fontSize = 13.sp)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 搜索栏
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { vm.setSearchQuery(it) },
                placeholder = { Text("搜索姓名 / 昵称 / 拼音", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { vm.setSearchQuery("") }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "清空", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                singleLine = true,
            )

            // === 一级菜单 ===
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Level1Chip("现役", level1 == Level1Mode.ACTIVE) { vm.selectLevel1(Level1Mode.ACTIVE) }
                Level1Chip("全部", level1 == Level1Mode.ALL) { vm.selectLevel1(Level1Mode.ALL) }
                if (hasFavorites) {
                    Level1Chip("关注 (${favorites.size})", level1 == Level1Mode.FAVORITES) {
                        vm.selectLevel1(Level1Mode.FAVORITES)
                    }
                }
            }

            // === 二级菜单（团体筛选） ===
            if (level1 != Level1Mode.FAVORITES && abbrs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.toggleGroups() }
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.FilterAlt,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        if (selectedAbbr.isNotEmpty()) "团体: $selectedAbbr" else "团体筛选 (${abbrs.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                AnimatedVisibility(
                    visible = expandedGroups,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        items(abbrs) { abbr ->
                            FilterChip(
                                selected = selectedAbbr == abbr,
                                onClick = { vm.selectAbbr(abbr) },
                                label = { Text(abbr, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }

                // === 三级菜单（队伍），选中团体后自动展开 ===
                if (selectedAbbr.isNotEmpty() && teams.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        items(teams) { team ->
                            FilterChip(
                                selected = selectedTeam == team,
                                onClick = { vm.selectTeam(team) },
                                label = { Text(team, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }
            }

            // === 状态颜色图例（仅未筛选时显示） ===
            if (members.isNotEmpty() && !isFiltered) {
                StatusLegend(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 1.dp),
                )
            }

            // === 成员列表（4 列网格） ===
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                members.isEmpty() -> {
                    EmptyState(
                        isFiltered = isFiltered,
                        hasQuery = searchQuery.isNotEmpty(),
                        onClear = { vm.clearFilters() },
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        for (group in grouped) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "h-${group.teamName}") {
                                TeamHeader(
                                    teamName = group.teamName,
                                    count = group.members.size,
                                )
                            }
                            gridItems(
                                items = group.members,
                                span = { GridItemSpan(1) },
                                key = { member -> "${group.teamName}-${member.userId}" },
                            ) { member ->
                                MemberGridItem(
                                    member = member,
                                    isFavorite = member.userId in favorites,
                                    onToggleFavorite = { vm.toggleFavorite(member.userId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 队伍 section header（左侧色点 + 队伍名 + 人数） */
@Composable
private fun TeamHeader(teamName: String, count: Int) {
    val color = com.pocket48.app.data.model.TeamColors.of(teamName)?.let { Color(it) }
        ?: MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = teamName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${count}人",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 10.sp,
        )
    }
}

/** 一级菜单按钮 */
@Composable
private fun Level1Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = fg,
        )
    }
}

/** 状态颜色图例（紧凑） */
@Composable
private fun StatusLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberStatus.entries.forEach { st ->
            val color = STATUS_COLORS[st] ?: Color.Gray
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    st.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

/** 空状态 */
@Composable
private fun EmptyState(isFiltered: Boolean, hasQuery: Boolean, onClear: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.PersonOff,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    hasQuery -> "未找到匹配成员"
                    isFiltered -> "当前筛选下无成员"
                    else -> "暂无成员数据"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            if (isFiltered) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("清空筛选", fontSize = 12.sp)
                }
            }
        }
    }
}

/** 网格项：参考项目样式 - 头像完整显示 + 队伍色色条 + 名字/团队 */
@Composable
private fun MemberGridItem(
    member: MemberInfo,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    val st = member.displayStatus
    val statusColor = STATUS_COLORS[st] ?: Color.Gray
    val teamColor = com.pocket48.app.data.model.TeamColors.of(member.teamName)?.let { Color(it) }
        ?: MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleFavorite() },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column {
            Box {
                // 头像区域 0.78 让头部更完整显示，不被裁切
                AsyncImage(
                    model = sourceUrl(member.avatar),
                    contentDescription = member.displayName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.78f),
                    contentScale = ContentScale.Crop,
                )
                // 状态点（左上）
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                        .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                )
                // 收藏星标（右上）
                if (isFavorite) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "已收藏",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(14.dp),
                    )
                }
            }

            // 队伍色色条（3dp）- 参考图样式
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(teamColor),
            )

            // 姓名 + 团队
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = member.realName.ifBlank { member.nickname },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (member.teamName.isNotEmpty()) {
                    Text(
                        text = member.teamName,
                        style = MaterialTheme.typography.labelSmall,
                        color = teamColor,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
