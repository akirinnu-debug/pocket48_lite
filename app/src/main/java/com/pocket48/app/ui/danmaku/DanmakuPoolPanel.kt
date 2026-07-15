package com.pocket48.app.ui.danmaku

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket48.app.data.model.LrcLine
import kotlin.math.abs

/** 每轮最多新增弹幕数 */
private const val MAX_STEP = 50

/** 跳转检测阈值 (ms) */
private const val SEEK_THRESHOLD = 3000L

/** 二分查找 ≤ timeMs 的条数 */
private fun countUpTo(lrcLines: List<LrcLine>, timeMs: Long): Int {
    var lo = 0
    var hi = lrcLines.size - 1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lrcLines[mid].timeMs <= timeMs) {
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return lo // lo == count of items with timeMs <= target
}

/**
 * 弹幕池面板 - 逐条增量显示的弹幕列表（伪实时效果）
 *
 * 参考 48tools 弹幕池实现:
 *  - 全量弹幕一次性加载进池
 *  - 按视频进度增量呈现
 *  - 跳转时立即对齐，正常播放时限速增长
 */
@Composable
fun DanmakuPoolPanel(
    visible: Boolean,
    lrcLines: List<LrcLine>,
    currentPositionMs: Long,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
    ) {
        var consumedCount by remember { mutableIntStateOf(0) }
        val listState = rememberLazyListState()

        // ref 桥接: snapshotFlow 追踪的是 Snapshot 状态, 非普通 Long 参数
        var posRef by remember { mutableLongStateOf(currentPositionMs) }
        LaunchedEffect(currentPositionMs) { posRef = currentPositionMs }

        LaunchedEffect(lrcLines) {
            var lastPosMs = -1L

            snapshotFlow { posRef }.collect { pos ->
                val target = countUpTo(lrcLines, pos)
                val isSeek = lastPosMs >= 0 && abs(pos - lastPosMs) > SEEK_THRESHOLD
                lastPosMs = pos

                val newCount = when {
                    isSeek || pos == 0L -> target          // 跳转/起始: 直接对齐
                    target > consumedCount ->               // 正常播放: 限速增长
                        (consumedCount + MAX_STEP).coerceAtMost(target)
                    else -> target                          // 后退跳转: 直接对齐
                }

                if (newCount != consumedCount) {
                    consumedCount = newCount
                    if (newCount > 0) {
                        listState.animateScrollToItem(newCount - 1)
                    }
                }
            }
        }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 250.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
                .padding(4.dp),
        ) {
            Text(
                "弹幕池  ${consumedCount}/${lrcLines.size}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(
                    items = lrcLines.take(consumedCount),
                    key = { i, _ -> i },
                ) { _, item ->
                    DanmakuPoolItem(item)
                }
            }
        }
    }
}

@Composable
private fun DanmakuPoolItem(lrc: LrcLine) {
    val sec = lrc.timeMs / 1000
    val min = sec / 60
    val ss = sec % 60
    Text(
        text = lrc.text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    )
}
