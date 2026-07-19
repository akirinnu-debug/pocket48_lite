package com.pocket48.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocket48.app.Pocket48App
import com.pocket48.app.data.api.Pocket48Api
import com.pocket48.app.data.danmaku.DanmakuParser
import com.pocket48.app.data.model.LiveDetail
import com.pocket48.app.data.model.LiveListItem
import com.pocket48.app.data.model.LocateSession
import com.pocket48.app.data.model.LocateWindow
import com.pocket48.app.data.model.LrcLine
import com.pocket48.app.data.model.MemberInfo
import com.pocket48.app.data.model.sourceUrl
import com.pocket48.app.data.store.CursorIndexEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log

class LiveViewModel : ViewModel() {

    private val api: Pocket48Api = Pocket48App.instance.pocket48Api
    private val store = Pocket48App.instance.memberStore
    private val historyStore = Pocket48App.instance.historyStore
    private val downloadStore = Pocket48App.instance.downloadStore
    private val cursorIndexStore = Pocket48App.instance.cursorIndexStore

    sealed class LiveListState {
        object Loading : LiveListState()
        data class Success(val items: List<LiveListItem>, val hasMore: Boolean, val loadingMore: Boolean = false) : LiveListState()
        data class Error(val message: String) : LiveListState()
    }

    sealed class LiveDetailState {
        object Idle : LiveDetailState()
        object Loading : LiveDetailState()
        data class Success(val detail: LiveDetail, val lrcLines: List<LrcLine>) : LiveDetailState()
        data class Error(val message: String) : LiveDetailState()
    }

    private val _liveListState = MutableStateFlow<LiveListState>(LiveListState.Loading)
    val liveListState = _liveListState.asStateFlow()

    private val _liveDetailState = MutableStateFlow<LiveDetailState>(LiveDetailState.Idle)
    val liveDetailState = _liveDetailState.asStateFlow()

    /** 当前播放的 liveId 对应的续播位置 (从历史记录读取, 进入播放页时使用) */
    private val _resumePositionMs = MutableStateFlow(0L)
    val resumePositionMs = _resumePositionMs.asStateFlow()

    private val _filterUserId = MutableStateFlow(0L)
    val filterUserId = _filterUserId.asStateFlow()

    /**
     * 日期定位相关状态 (与 _liveListState 完全解耦)
     *
     * - _locateSession: 定位任务进度, null 表示无任务
     * - _locateResult: 定位命中后的窗口, null 表示未在定位结果视图
     *
     * 关键不变量: 定位过程绝不修改 _liveListState / nextCursor
     * 这保护了 "从播放页返回不回弹" 的既有修复
     */
    private val _locateSession = MutableStateFlow<LocateSession?>(null)
    val locateSession: StateFlow<LocateSession?> = _locateSession.asStateFlow()

    private val _locateResult = MutableStateFlow<LocateWindow?>(null)
    val locateResult: StateFlow<LocateWindow?> = _locateResult.asStateFlow()

    /** 当前定位任务, 用于取消 */
    private var locateJob: Job? = null

    /** API 调用互斥锁: 序列化 loadMore / locate / loadOlderInWindow 的 fetchLiveList 调用
     *  防止并发触发导致服务器限流, 也避免游标状态竞争 */
    private val cursorMutex = Mutex()

    val favoriteMembers: StateFlow<List<MemberInfo>> = store.favoriteIds
        .combine(MutableStateFlow(Unit)) { favs, _ ->
            store.loadMembers().filter { it.userId in favs }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var nextCursor = "0"
    private var isLoadingMore = false

    companion object {
        private const val TAG = "LiveViewModel"
    }

    /** 加载直播列表 (record=true 获取回放，总是有数据) */
    fun loadLiveList(refresh: Boolean = true) {
        if (refresh) {
            _liveListState.value = LiveListState.Loading
            nextCursor = "0"
        }
        // 捕获本次请求用的 entryCursor (refresh 时是 "0", 否则是当前 nextCursor)
        val entryCursor = nextCursor
        viewModelScope.launch {
            val result = api.fetchLiveList(record = true, next = nextCursor, userId = _filterUserId.value)
            if (result != null) {
                nextCursor = result.next
                Log.d(TAG, "loadLiveList: got ${result.liveList.size} items, nextCursor=$nextCursor")
                _liveListState.value = LiveListState.Success(
                    items = result.liveList,
                    hasMore = result.next != "0" && result.next.isNotEmpty(),
                )
                // Phase 2: 播种 cursor 索引, 加速后续 locate
                seedCursorIndex(entryCursor, result.liveList, result.next)
            } else {
                if (refresh) _liveListState.value = LiveListState.Error("加载失败，请检查网络")
            }
        }
    }

    /** 加载更多 */
    fun loadMore() {
        if (isLoadingMore || nextCursor == "0" || nextCursor.isEmpty()) return
        val current = (_liveListState.value as? LiveListState.Success)?.items ?: return
        isLoadingMore = true
        val prevState = _liveListState.value as? LiveListState.Success
        if (prevState != null) {
            _liveListState.value = prevState.copy(loadingMore = true)
        }
        // 捕获本次请求用的 entryCursor (即翻页前的 nextCursor)
        val entryCursor = nextCursor
        viewModelScope.launch {
            // 加锁: 与 locate / loadOlderInWindow 互斥, 避免并发 API 调用与游标竞争
            val result = cursorMutex.withLock {
                api.fetchLiveList(record = true, next = nextCursor, userId = _filterUserId.value)
            }
            if (result != null) {
                nextCursor = result.next
                Log.d(TAG, "loadMore: got ${result.liveList.size} items, nextCursor=$nextCursor")
                // 去重
                val existingIds = current.map { it.liveId }.toSet()
                val newItems = result.liveList.filter { it.liveId !in existingIds }
                _liveListState.value = LiveListState.Success(
                    items = current + newItems,
                    hasMore = result.next != "0" && result.next.isNotEmpty(),
                )
                // Phase 2: 播种 cursor 索引
                seedCursorIndex(entryCursor, result.liveList, result.next)
            }
            isLoadingMore = false
        }
    }

    /**
     * 把一页的 cursor → 时间范围 元信息写入 CursorIndexStore
     *
     * fire-and-forget: 用 appScope (与 savePlayPosition 同模式), 即使 ViewModel 销毁也写完
     * 时间戳无效 (<=0) 或列表空时跳过, 避免污染索引
     */
    private fun seedCursorIndex(
        entryCursor: String,
        items: List<LiveListItem>,
        nextCursor: String,
    ) {
        if (items.isEmpty()) return
        val firstTime = items.first().displayTime
        val lastTime = items.last().displayTime
        if (firstTime <= 0 || lastTime <= 0) return
        val entry = CursorIndexEntry(
            userId = _filterUserId.value,
            entryCursor = entryCursor,
            nextCursor = nextCursor,
            firstItemTime = firstTime,
            lastItemTime = lastTime,
            itemCount = items.size,
            fetchedAt = System.currentTimeMillis(),
        )
        Pocket48App.instance.appScope.launch {
            cursorIndexStore.append(entry)
        }
    }

    /** 按成员筛选 */
    fun filterByMember(userId: Long) {
        _filterUserId.value = userId
        nextCursor = "0"
        loadLiveList(refresh = true)
    }

    /** 清除筛选 */
    fun clearFilter() {
        if (_filterUserId.value != 0L) {
            _filterUserId.value = 0L
            nextCursor = "0"
            loadLiveList(refresh = true)
        }
    }

    /**
     * 跳转到目标日期最近一期回放 (单日跳转, 简化自旧 locateByDateRange)
     *
     * 语义: 找 displayTime <= targetEndMs 的最新一条
     * - 当天有直播 → 命中当天最新
     * - 当天没有 → 命中比目标更旧的第一条 ("往后找")
     * - 选未来日期 → 命中当前列表第一项 ("最近一期")
     * - 翻到尽头仍未命中 → fallback 取最后一项 (最旧)
     *
     * 重构要点 (区别于旧 locateByDateRange):
     * - 移除 startMs 参数 (单日选择只需 endMs)
     * - 移除 L173 修复 (语义改变, 未来日期不再 refresh, 而是命中当前最新)
     * - 移除 L214 overshotOnce 容错 (不再 "找范围内", 翻到尽头取最后一项即可)
     * - findIndexInRange → findFirstLe (语义简化为 "找第一个 <= targetMs")
     * - 保留: 状态隔离 / cursorMutex / Job 取消 / CursorIndex 起跳 / buildWindowAround
     *
     * @param targetEndMs 目标日 23:59:59.999
     */
    fun locateToDate(targetEndMs: Long) {
        // 防重复触发
        if (locateJob?.isActive == true) {
            Log.w(TAG, "locateToDate: already running, ignore")
            return
        }

        val current = (_liveListState.value as? LiveListState.Success)?.items
        if (current.isNullOrEmpty()) {
            _locateSession.value = LocateSession(
                status = "列表为空，无法定位",
                pageIndex = 0,
                estTotalPages = null,
                progress = 0f,
                cancelable = false,
            )
            return
        }

        // 廉价路径: 当前列表已有 displayTime <= targetEndMs 的项
        //   列表 DESC, 第一个 <= targetEndMs 的就是最接近且不晚于目标的
        val quickHit = findFirstLe(current, targetEndMs)
        if (quickHit >= 0) {
            _locateResult.value = buildWindowAround(current, quickHit, olderCursor = nextCursor)
            _locateSession.value = null
            return
        }

        // 主路径: 当前列表全部 > targetEndMs (即全部比目标日期新), 翻页找更旧的
        _locateSession.value = LocateSession.initial()
        locateJob = viewModelScope.launch {
            try {
                // Phase 2: 查 CursorIndex 起跳 (跳过中间所有页)
                val candidate = cursorIndexStore.findCandidate(_filterUserId.value, targetEndMs)
                val indexHit = candidate != null &&
                    candidate.entryCursor != "0" &&
                    candidate.entryCursor.isNotEmpty() &&
                    candidate.entryCursor != nextCursor  // 与已加载位置不同才有跳的意义
                val startingCursor = if (indexHit) candidate!!.entryCursor else nextCursor
                // 估算总页数: 索引命中时, 候选页 + 容错页; 未命中时 null (未知)
                val estTotalPages: Int? = if (indexHit) {
                    // 若候选区间已包含目标 → 1 页; 否则需向后翻 2-3 页
                    if (targetEndMs in candidate!!.lastItemTime..candidate.firstItemTime) 1 else 3
                } else null

                if (indexHit) {
                    Log.d(TAG, "locateToDate: index hit, jump to cursor=${candidate!!.entryCursor.take(20)}..., estTotalPages=$estTotalPages")
                } else {
                    Log.d(TAG, "locateToDate: index miss, sequential from nextCursor")
                }

                var accumulatedItems: List<LiveListItem> = current
                var cursor = startingCursor
                var page = 0

                while (cursor != "0" && cursor.isNotEmpty()) {
                    page++
                    val progress = if (estTotalPages != null && estTotalPages > 0) {
                        (page.toFloat() / estTotalPages).coerceIn(0.01f, 0.99f)
                    } else 0f
                    _locateSession.value = (_locateSession.value ?: LocateSession.initial()).copy(
                        status = if (indexHit) "索引加速: 正在加载第 $page 页..." else "正在加载第 $page 页...",
                        pageIndex = page,
                        estTotalPages = estTotalPages,
                        progress = progress,
                    )

                    val entryCursorForSeed = cursor  // 播种用 (本次请求的 next)
                    val result = cursorMutex.withLock {
                        api.fetchLiveList(record = true, next = cursor, userId = _filterUserId.value)
                    }
                    if (result == null) {
                        _locateSession.value = _locateSession.value?.copy(
                            status = "加载失败，请重试",
                            cancelable = false,
                        )
                        return@launch
                    }

                    // Phase 2: 播种本页到索引 (持续完善索引)
                    seedCursorIndex(entryCursorForSeed, result.liveList, result.next)

                    // 去重追加到局部变量 (绝不写 _liveListState!)
                    val existingIds = accumulatedItems.map { it.liveId }.toSet()
                    val newItems = result.liveList.filter { it.liveId !in existingIds }
                    accumulatedItems = accumulatedItems + newItems

                    // 命中检查: 找第一个 displayTime <= targetEndMs 的项
                    val hitIndex = findFirstLe(accumulatedItems, targetEndMs)
                    if (hitIndex >= 0) {
                        _locateResult.value = buildWindowAround(accumulatedItems, hitIndex, olderCursor = result.next)
                        _locateSession.value = null
                        return@launch
                    }

                    cursor = result.next

                    // 翻到尽头仍未命中 → fallback 取最后一项 (最旧的)
                    // 语义保证: 用户总能看到一条结果 (除非列表完全为空)
                    if (result.next == "0" || result.next.isEmpty()) {
                        if (accumulatedItems.isNotEmpty()) {
                            _locateResult.value = buildWindowAround(
                                accumulatedItems, accumulatedItems.lastIndex, olderCursor = result.next,
                            )
                            _locateSession.value = null
                        } else {
                            _locateSession.value = _locateSession.value?.copy(
                                status = "未找到任何回放",
                                cancelable = false,
                            )
                        }
                        return@launch
                    }

                    // 防服务器限流
                    delay(200)
                }

                // 理论上不会走到这里 (while 退出条件已覆盖), 防御性兜底
                if (accumulatedItems.isNotEmpty()) {
                    _locateResult.value = buildWindowAround(
                        accumulatedItems, accumulatedItems.lastIndex, olderCursor = "0",
                    )
                    _locateSession.value = null
                } else {
                    _locateSession.value = _locateSession.value?.copy(
                        status = "未找到任何回放",
                        cancelable = false,
                    )
                }
            } catch (ce: CancellationException) {
                // 用户取消: 清进度 (保留主列表与已建索引状态)
                _locateSession.value = null
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "locateToDate failed", e)
                _locateSession.value = _locateSession.value?.copy(
                    status = "加载失败：${e.message}",
                    cancelable = false,
                )
            }
        }
    }

    /**
     * 在 items 中构造以 targetIndex 为中心的窗口 (前 10 + 目标 + 后 10)
     *
     * @param olderCursor 翻更早内容用的 cursor (= 窗口最旧页对应的 result.next)
     *        DESC 顺序下, "加载更早" 是继续往后翻页, 所以用 result.next 即可
     */
    private fun buildWindowAround(
        items: List<LiveListItem>,
        targetIndex: Int,
        olderCursor: String?,
    ): LocateWindow {
        val windowSize = 10
        val start = (targetIndex - windowSize).coerceAtLeast(0)
        val end = (targetIndex + windowSize + 1).coerceAtMost(items.size)
        val windowItems = items.subList(start, end).toList()  // toList 脱离原 List 引用
        val targetInWindow = targetIndex - start
        // canLoadOlder: 窗口末尾已达累计列表末尾, 且 olderCursor 仍非空时才可继续翻
        val canLoadOlder = end >= items.size &&
            !olderCursor.isNullOrEmpty() && olderCursor != "0"
        return LocateWindow(
            items = windowItems,
            targetIndex = targetInWindow,
            olderCursor = olderCursor?.takeIf { it != "0" && it.isNotEmpty() },
            canLoadOlder = canLoadOlder,
        )
    }

    /** 找第一个 displayTime <= targetMs 的索引 (列表 DESC, 即最接近 targetMs 且不晚于它的)
     *  全部 > targetMs 返回 -1
     *
     *  语义说明 (区别于旧 findIndexInRange):
     *  - 旧: 找 [startMs, endMs] 范围内的最新项 (区间筛选)
     *  - 新: 找 <= targetMs 的最新项 (单日跳转 + 往后找更旧)
     *  - 当天有直播 → 命中当天最新; 当天没有 → 命中比目标更旧的第一条 */
    private fun findFirstLe(items: List<LiveListItem>, targetMs: Long): Int {
        for ((i, item) in items.withIndex()) {
            if (item.displayTime <= targetMs) return i
        }
        return -1
    }

    /** 取消正在进行的定位任务 (UI "取消" 按钮调用) */
    fun cancelLocate() {
        locateJob?.cancel()
        _locateSession.value = null
    }

    /** 退出定位结果窗口 (UI "返回最新" 按钮调用)
     *  不触碰 _liveListState, 主列表状态与 scroll 位置原样保留 */
    fun dismissLocateResult() {
        _locateResult.value = null
    }

    /** 在定位窗口中加载更早的内容 (UI "加载更早" 按钮调用) */
    fun loadOlderInWindow() {
        val current = _locateResult.value ?: return
        if (!current.canLoadOlder || current.olderCursor.isNullOrEmpty()) return
        val entryCursor = current.olderCursor
        viewModelScope.launch {
            val result = cursorMutex.withLock {
                api.fetchLiveList(record = true, next = current.olderCursor, userId = _filterUserId.value)
            } ?: return@launch
            val existingIds = current.items.map { it.liveId }.toSet()
            val newItems = result.liveList.filter { it.liveId !in existingIds }
            _locateResult.value = current.copy(
                items = current.items + newItems,
                olderCursor = result.next.takeIf { it != "0" && it.isNotEmpty() },
                canLoadOlder = result.next != "0" && result.next.isNotEmpty(),
            )
            // Phase 2: 播种 (与 loadMore / locate 一致)
            seedCursorIndex(entryCursor, result.liveList, result.next)
        }
    }

    /** 加载直播详情 */
    fun loadLiveDetail(liveId: String) {
        viewModelScope.launch {
            _liveDetailState.value = LiveDetailState.Loading
            // 先读历史中的续播位置 (在写入新历史之前)
            val existing = historyStore.historyFlow.first().find { it.liveId == liveId }
            _resumePositionMs.value = if (existing?.hasResumePosition == true) {
                existing.lastPlayPosition
            } else {
                0L
            }
            val detail = api.fetchLiveOne(liveId)
            if (detail != null) {
                var lrcLines: List<LrcLine> = emptyList()
                val rawLrcUrl = detail.lrcUrl
                val rawMsgFile = detail.msgFilePath
                val danmakuUrl = if (!rawLrcUrl.isNullOrBlank() && rawLrcUrl != "null") {
                    rawLrcUrl
                } else if (!rawMsgFile.isNullOrBlank()) {
                    rawMsgFile
                } else null
                if (danmakuUrl != null) {
                    val fullUrl = sourceUrl(danmakuUrl)
                    val lrcText = fetchLrcWithCache(detail.liveId, fullUrl)
                    if (lrcText != null) {
                        lrcLines = DanmakuParser.parse(lrcText)
                    }
                }
                _liveDetailState.value = LiveDetailState.Success(detail, lrcLines)
                // 写入播放历史 (保留上次续播位置, 因为用户可能从历史页跳回继续看)
                historyStore.upsert(
                    liveId = detail.liveId,
                    title = detail.title,
                    coverPath = detail.coverPath,
                    userId = detail.user?.userId ?: detail.createdBy,
                    userNickname = detail.user?.userName?.ifBlank { detail.createdName }
                        ?: detail.createdName,
                    userAvatar = detail.user?.userAvatar.orEmpty(),
                    playDuration = detail.playDuration,
                    keepPosition = true,
                )
            } else {
                // 离线回退: API 请求失败 (无网络), 查下载索引
                // 如果该视频已下载过, 从 DownloadStore 取出 m3u8Url 直接播放
                val cached = runCatching {
                    downloadStore.downloadFlow.first().find { it.liveId == liveId }
                }.getOrNull()
                if (cached != null) {
                    var lrcLines: List<LrcLine> = emptyList()
                    // 尝试本地 LRC 缓存 (离线场景, 必须已有缓存)
                    if (!cached.lrcUrl.isNullOrBlank()) {
                        val lrcText = fetchLrcWithCache(liveId, sourceUrl(cached.lrcUrl))
                        if (lrcText != null) {
                            lrcLines = DanmakuParser.parse(lrcText)
                        }
                    }
                    _liveDetailState.value = LiveDetailState.Success(
                        detail = LiveDetail(
                            liveId = liveId,
                            title = cached.title,
                            coverPath = cached.coverPath,
                            m3u8Url = cached.m3u8Url,
                            playDuration = cached.playDuration,
                            createdBy = cached.userId,
                            createdName = cached.userNickname,
                        ),
                        lrcLines = lrcLines,
                    )
                } else {
                    _liveDetailState.value = LiveDetailState.Error("加载直播详情失败")
                }
            }
        }
    }

    /** 退出播放页时调用: 记录最后播放位置 (供 "继续观看")
     *
     * 用 appScope 而非 viewModelScope: NavBackStackEntry 销毁时 viewModel 会被清理,
     * viewModelScope 取消后 launch 的任务可能未完成, 导致进度丢失
     */
    fun savePlayPosition(liveId: String, positionMs: Long, durationMs: Long) {
        Pocket48App.instance.appScope.launch {
            historyStore.updatePosition(liveId, positionMs, durationMs)
        }
    }

    /**
     * 带本地缓存的 LRC 拉取
     *
     * - 优先读本地 filesDir/lrc/{liveId}.lrc (离线可读)
     * - 未命中则从网络拉取, 成功后写入本地
     * - 拉失败也回退到本地 (即使过期也比无好)
     *
     * 这样离线播放下载好的视频时, 弹幕也能正常显示
     */
    private suspend fun fetchLrcWithCache(liveId: String, url: String): String? {
        val app = Pocket48App.instance
        val lrcDir = java.io.File(app.filesDir, "lrc").apply { if (!exists()) mkdirs() }
        val localFile = java.io.File(lrcDir, "$liveId.lrc")
        // 1. 先试本地
        if (localFile.exists()) {
            runCatching {
                val text = localFile.readText()
                if (text.isNotBlank()) return text
            }
        }
        // 2. 网络
        val text = api.fetchLrc(url) ?: run {
            // 3. 网络失败, 退回本地 (即使为空也无所谓)
            return if (localFile.exists()) localFile.readText() else null
        }
        // 4. 写本地
        runCatching {
            localFile.writeText(text)
        }
        return text
    }

    fun resetDetail() {
        _liveDetailState.value = LiveDetailState.Idle
        _resumePositionMs.value = 0L
    }
}
