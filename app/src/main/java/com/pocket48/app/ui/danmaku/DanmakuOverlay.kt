package com.pocket48.app.ui.danmaku

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket48.app.data.model.LrcLine
import kotlin.math.abs

/** 最大同时活跃弹幕数 */
private const val MAX_ACTIVE = 60

/** 每帧最多新增弹幕数 */
private const val MAX_NEW_PER_FRAME = 5

/** 弹幕触发窗口 (ms) */
private const val WINDOW_MS = 6000L

/** 弹幕从右到左的基本耗时 (ms) */
private const val SPEED_BASE_MS = 8000f

/** 活跃弹幕项 */
private class ActiveDanmaku(
    val layout: TextLayoutResult,
    var x: Float,
    val y: Float,
    val speed: Float,
)

/** 二分查找第一个 >= timeMs 的弹幕索引 */
private fun binarySearch(lrcLines: List<LrcLine>, timeMs: Long): Int {
    var lo = 0
    var hi = lrcLines.size - 1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lrcLines[mid].timeMs < timeMs) {
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return lo
}

/**
 * 基于 Canvas 的飞行弹幕覆盖层
 *
 * 采用"弹幕池"模式 (参考 48tools/PlayerContent):
 *  - 全量弹幕 (lrcLines) 一次性加载进池
 *  - 二分查找定位起始索引, 按视频进度增量呈现
 *  - 限速增长: 每帧最多 MAX_NEW_PER_FRAME 条
 *  - 活跃弹幕数上限 MAX_ACTIVE
 *
 * 重要: currentPositionMs 用 ref 桥接, 避免 LaunchedEffect 内读取到初始快照
 */
@Composable
fun DanmakuOverlay(
    lrcLines: List<LrcLine>,
    currentPositionMs: Long,
    isPlaying: Boolean,
    showDanmaku: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!showDanmaku || lrcLines.isEmpty()) return

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val textStyle = TextStyle(
        color = Color.White,
        fontSize = 15.sp,
        shadow = Shadow(blurRadius = 3f, offset = Offset(1f, 1f), color = Color.Black.copy(alpha = 0.8f)),
    )

    val activeItems = remember { mutableStateListOf<ActiveDanmaku>() }
    var lastFrameNanos by remember { mutableLongStateOf(0L) }
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }
    var tick by remember { mutableIntStateOf(0) }
    // 记录"已触发到 lrcLines 哪个索引", 跨尺寸重置时清空活跃弹幕同时也需要重置
    var lastTriggeredIdxRef by remember { mutableIntStateOf(-1) }

    // ref 桥接: LaunchedEffect 内读 posRef 获得最新的 currentPositionMs
    var posRef by remember { mutableLongStateOf(currentPositionMs) }
    LaunchedEffect(currentPositionMs) { posRef = currentPositionMs }

    val trackHeightPx = with(density) { 28.dp.toPx() }
    // 顶部预留 2 行轨道（约 56dp）避开刘海/状态栏区域
    val topReservedPx = with(density) { 56.dp.toPx() }
    val usableHeight = (canvasHeight - topReservedPx).coerceAtLeast(0f)
    val maxTracks = if (usableHeight > 0) (usableHeight / trackHeightPx).toInt() else 0

    // 全屏/窗口尺寸大幅变化 (≥ 15%) 时清空活跃弹幕, 避免旧 x/speed/y 错位
    var lastKnownWidth by remember { mutableFloatStateOf(0f) }
    var lastKnownHeight by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(canvasWidth, canvasHeight) {
        val w = canvasWidth
        val h = canvasHeight
        if (w > 0f && h > 0f && lastKnownWidth > 0f && lastKnownHeight > 0f) {
            val widthDelta = abs(w - lastKnownWidth) / lastKnownWidth
            val heightDelta = abs(h - lastKnownHeight) / lastKnownHeight
            if (widthDelta > 0.15f || heightDelta > 0.15f) {
                activeItems.clear()
                // 同步重置已触发索引, 防止"重复/漏发"弹幕
                lastTriggeredIdxRef = -1
            }
        }
        if (w > 0f) lastKnownWidth = w
        if (h > 0f) lastKnownHeight = h
    }

    // 单循环：动画 + 弹幕触发
    LaunchedEffect(isPlaying, showDanmaku, lrcLines) {
        var lastPositionMs = 0L
        // 从 ref 初始化 (尺寸重置会把 ref 设回 -1)
        var lastTriggeredIdx = lastTriggeredIdxRef
        lastFrameNanos = 0L

        while (isPlaying && showDanmaku) {
            withFrameNanos { now ->
                val deltaMs = if (lastFrameNanos > 0) (now - lastFrameNanos) / 1_000_000f else 16f
                lastFrameNanos = now

                // ── 1. 移动已有弹幕 ──
                val toRemove = mutableListOf<ActiveDanmaku>()
                for (item in activeItems) {
                    item.x -= item.speed * deltaMs
                    if (item.x + item.layout.size.width < 0f) {
                        toRemove.add(item)
                    }
                }
                if (toRemove.isNotEmpty()) {
                    activeItems.removeAll(toRemove)
                }

                // ── 2. 检测跳转 (用 ref 读最新位置) 或外部尺寸重置 ──
                val pos = posRef
                val externalReset = lastTriggeredIdxRef == -1 && lastTriggeredIdx != -1
                if (abs(pos - lastPositionMs) > 3000 || pos == 0L || externalReset) {
                    activeItems.clear()
                    lastTriggeredIdx = -1
                }
                lastPositionMs = pos

                // ── 3. 触发新弹幕 (限流) ──
                if (canvasWidth > 0 && maxTracks > 0) {
                    val windowStart = pos - 500L
                    val windowEnd = pos + WINDOW_MS
                    val startIdx = (lastTriggeredIdx + 1).coerceAtLeast(0)
                    var newCount = 0
                    for (i in startIdx until lrcLines.size) {
                        val lrc = lrcLines[i]
                        if (lrc.timeMs > windowEnd) break
                        if (lrc.timeMs in windowStart..pos) {
                            val layout = textMeasurer.measure(lrc.text, textStyle)

                            var track = -1
                            for (t in 0 until maxTracks) {
                                val trackY = topReservedPx + t * trackHeightPx
                                val conflict = activeItems.any { a ->
                                    abs(a.y - trackY) < 1f && a.x + a.layout.size.width > canvasWidth * 0.8f
                                }
                                if (!conflict) {
                                    track = t
                                    break
                                }
                            }

                            if (track >= 0) {
                                val speed = (canvasWidth + layout.size.width) / SPEED_BASE_MS
                                activeItems.add(
                                    ActiveDanmaku(
                                        layout = layout,
                                        x = canvasWidth,
                                        y = topReservedPx + track * trackHeightPx,
                                        speed = speed,
                                    )
                                )
                            }
                            lastTriggeredIdx = i
                            lastTriggeredIdxRef = i
                            newCount++
                            if (newCount >= MAX_NEW_PER_FRAME) break
                        }
                    }
                }

                // ── 4. 上限保护 ──
                while (activeItems.size > MAX_ACTIVE) {
                    val discard = activeItems.maxByOrNull { it.x } ?: break
                    activeItems.remove(discard)
                }

                tick++ // 触发 Canvas 重绘
            }
        }
    }

    Canvas(
        modifier = modifier.onSizeChanged {
            canvasWidth = it.width.toFloat()
            canvasHeight = it.height.toFloat()
        }
    ) {
        tick // 依赖 tick，每次动画帧强制重绘
        for (item in activeItems) {
            drawText(
                textLayoutResult = item.layout,
                topLeft = Offset(item.x, item.y),
            )
        }
    }
}
