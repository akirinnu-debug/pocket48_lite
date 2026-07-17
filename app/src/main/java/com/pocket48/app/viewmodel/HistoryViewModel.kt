package com.pocket48.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocket48.app.Pocket48App
import com.pocket48.app.data.model.PlayHistory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 播放历史 ViewModel
 *
 * 数据直接来自 HistoryStore.historyFlow (响应式)
 * 写入由 LiveViewModel.loadLiveDetail / savePlayPosition 处理, 此处只负责读 + 删
 */
class HistoryViewModel : ViewModel() {

    private val historyStore = Pocket48App.instance.historyStore

    /** 历史列表 (按 lastPlayTime 倒序) */
    val history: StateFlow<List<PlayHistory>> = historyStore.historyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 删除单条 */
    fun delete(liveId: String) {
        viewModelScope.launch { historyStore.delete(liveId) }
    }

    /** 清空全部 */
    fun clearAll() {
        viewModelScope.launch { historyStore.clear() }
    }
}
