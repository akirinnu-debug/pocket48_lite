package com.pocket48.app.data.model

import com.pocket48.app.data.model.LiveListItem

/**
 * 日期定位相关状态模型
 *
 * 设计要点:
 * - LocateSession / LocateWindow 与主列表状态 (_liveListState) 完全解耦
 *   定位过程不污染主列表, 保护 "从播放页返回不回弹" 的既有修复
 * - LocateWindow 是定位命中后展示的 "以目标项为中心的窗口"
 *   用户点 "返回最新" 后清除窗口, 主列表状态原样保留
 */

/** 定位任务进度, null 表示当前无任务 */
data class LocateSession(
    /** 展示给用户的文案, 如 "正在定位..." / "正在加载第 N 页..." / "未找到该时间段的回放" */
    val status: String,
    /** 已翻页数 (含当前进行中的页) */
    val pageIndex: Int,
    /** 估算总页数; Phase 2 用 CursorIndex 估算, Phase 1 始终为 null */
    val estTotalPages: Int?,
    /** 进度 0..1; estTotalPages 为 null 时为 0 (UI 退化显示页码即可) */
    val progress: Float,
    /** 是否可取消 (任务运行中为 true, 终态如 not-found/error 为 false) */
    val cancelable: Boolean,
) {
    companion object {
        /** 初始态: 刚触发定位, 尚未翻页 */
        fun initial(): LocateSession = LocateSession(
            status = "正在定位...",
            pageIndex = 0,
            estTotalPages = null,
            progress = 0f,
            cancelable = true,
        )
    }
}

/**
 * 定位结果窗口, null 表示当前未处于定位结果视图
 *
 * - items: 以目标项为中心的一段连续回放 (目标 + 前若干 + 后若干)
 * - olderCursor: 继续翻更早内容时使用的 cursor (即窗口最旧页对应的 entryCursor)
 * - canLoadOlder: 是否还能加载更早 (olderCursor 非空且上一页加载未到尽头)
 *
 * 注意: DESC 顺序 (最新在前), "加载更早" 是往列表末尾追加更旧的内容
 */
data class LocateWindow(
    val items: List<LiveListItem>,
    /** 目标项在 items 中的索引 (滚动到此索引) */
    val targetIndex: Int,
    /** 继续翻更早内容用的 cursor; null 表示已到尽头 */
    val olderCursor: String?,
    val canLoadOlder: Boolean,
)
