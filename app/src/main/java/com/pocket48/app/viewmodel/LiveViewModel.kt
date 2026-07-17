package com.pocket48.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocket48.app.Pocket48App
import com.pocket48.app.data.api.Pocket48Api
import com.pocket48.app.data.danmaku.DanmakuParser
import com.pocket48.app.data.model.LiveDetail
import com.pocket48.app.data.model.LiveListItem
import com.pocket48.app.data.model.LrcLine
import com.pocket48.app.data.model.MemberInfo
import com.pocket48.app.data.model.sourceUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LiveViewModel : ViewModel() {

    private val api: Pocket48Api = Pocket48App.instance.pocket48Api
    private val store = Pocket48App.instance.memberStore
    private val historyStore = Pocket48App.instance.historyStore

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

    val favoriteMembers: StateFlow<List<MemberInfo>> = store.favoriteIds
        .combine(MutableStateFlow(Unit)) { favs, _ ->
            store.loadMembers().filter { it.userId in favs }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var nextCursor = "0"
    private var isLoadingMore = false

    /** 加载直播列表 (record=true 获取回放，总是有数据) */
    fun loadLiveList(refresh: Boolean = true) {
        if (refresh) {
            _liveListState.value = LiveListState.Loading
            nextCursor = "0"
        }
        viewModelScope.launch {
            val result = api.fetchLiveList(record = true, next = nextCursor, userId = _filterUserId.value)
            if (result != null) {
                nextCursor = result.next
                _liveListState.value = LiveListState.Success(
                    items = result.liveList,
                    hasMore = result.next != "0" && result.next.isNotEmpty(),
                )
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
        viewModelScope.launch {
            val result = api.fetchLiveList(record = true, next = nextCursor, userId = _filterUserId.value)
            if (result != null) {
                nextCursor = result.next
                // 去重
                val existingIds = current.map { it.liveId }.toSet()
                val newItems = result.liveList.filter { it.liveId !in existingIds }
                _liveListState.value = LiveListState.Success(
                    items = current + newItems,
                    hasMore = result.next != "0" && result.next.isNotEmpty(),
                )
            }
            isLoadingMore = false
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
                _liveDetailState.value = LiveDetailState.Error("加载直播详情失败")
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
