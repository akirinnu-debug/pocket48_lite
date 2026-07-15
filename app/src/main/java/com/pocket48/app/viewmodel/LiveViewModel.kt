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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LiveViewModel : ViewModel() {

    private val api: Pocket48Api = Pocket48App.instance.pocket48Api
    private val store = Pocket48App.instance.memberStore

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
                    val lrcText = api.fetchLrc(fullUrl)
                    if (lrcText != null) {
                        lrcLines = DanmakuParser.parse(lrcText)
                    }
                }
                _liveDetailState.value = LiveDetailState.Success(detail, lrcLines)
            } else {
                _liveDetailState.value = LiveDetailState.Error("加载直播详情失败")
            }
        }
    }

    fun resetDetail() {
        _liveDetailState.value = LiveDetailState.Idle
    }
}
