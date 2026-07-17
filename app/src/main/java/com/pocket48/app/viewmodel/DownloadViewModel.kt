package com.pocket48.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import com.pocket48.app.Pocket48App
import com.pocket48.app.data.VideoCacheManager
import com.pocket48.app.data.download.VideoDownloadService
import com.pocket48.app.data.model.DownloadItem
import com.pocket48.app.data.model.DownloadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 下载管理 ViewModel
 *
 * 数据来源:
 * - downloads (持久化): DownloadStore 索引列表 (用户主动加入的)
 * - statusMap (实时): DownloadManager 状态 (通过 Listener 触发刷新)
 * 两者通过 liveId 关联
 *
 * 使用 AndroidViewModel 因为需要 Application 创建 DownloadManager
 */
class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val downloadStore = Pocket48App.instance.downloadStore
    private val downloadManager = VideoCacheManager.getDownloadManager(app)

    /** 持久化的下载列表 (用户主动加入的) */
    val downloads: StateFlow<List<DownloadItem>> = downloadStore.downloadFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 实时下载状态 (liveId → DownloadStatus), 由 DownloadManager.Listener 触发更新 */
    private val _statusMap = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val statusMap: StateFlow<Map<String, DownloadStatus>> = _statusMap.asStateFlow()

    init {
        downloadManager.addListener(object : DownloadManager.Listener {
            // Media3 1.4.1 没有 onDownloadsChanged (复数), 用单数回调替代
            // onDownloadChanged 只在状态切换时触发 (QUEUED→DOWNLOADING→COMPLETED)
            // 不会在字节增长时触发, 进度刷新靠下面的轮询
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?,
            ) {
                refreshStatus()
            }
            override fun onDownloadRemoved(
                downloadManager: DownloadManager,
                download: Download,
            ) {
                refreshStatus()
            }
            // Requirements 变化 (例如网络恢复) 也要刷新, 否则会卡在 STATE_QUEUED 显示
            override fun onWaitingForRequirementsChanged(
                downloadManager: DownloadManager,
                waitingForRequirements: Boolean,
            ) {
                refreshStatus()
            }
        })
        refreshStatus()

        // 进度轮询: Media3 1.4.1 DownloadManager.Listener 无 onDownloadProgress 回调,
        // 下载过程中的字节增长不会触发任何监听器, 必须主动轮询 downloadIndex
        // 仅当存在非终态任务时才轮询, 减少无谓开销
        viewModelScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(800)
                val items = downloads.value
                if (items.isEmpty()) continue
                // 检查是否有活跃任务 (非 COMPLETED/FAILED/REMOVING)
                val hasActive = items.any { item ->
                    val state = downloadManager.downloadIndex.getDownload(item.liveId)?.state
                    state == Download.STATE_QUEUED ||
                        state == Download.STATE_DOWNLOADING ||
                        state == Download.STATE_RESTARTING
                }
                if (hasActive) refreshStatus()
            }
        }
    }

    /** 刷新实时状态 (拉取所有 downloadIndex) */
    private fun refreshStatus() {
        viewModelScope.launch {
            val items = downloads.value
            if (items.isEmpty()) {
                _statusMap.value = emptyMap()
                return@launch
            }
            val map = mutableMapOf<String, DownloadStatus>()
            items.forEach { item ->
                downloadManager.downloadIndex.getDownload(item.liveId)?.let { dl ->
                    // Media3 1.4.1 Download: contentLength 替代 totalBytes
                    // getBytesDownloaded()/getPercentDownloaded() 是 getter, Kotlin 直接属性访问
                    // C.PERCENTAGE_UNSET (-1f) / C.LENGTH_UNSET (Long.MIN_VALUE+1) 均为负数
                    val total = dl.contentLength
                    val percent = dl.percentDownloaded.let { p ->
                        if (p < 0f) 0f else p
                    }
                    map[item.liveId] = DownloadStatus(
                        liveId = item.liveId,
                        state = dl.state,
                        percent = percent,
                        downloadedBytes = dl.bytesDownloaded,
                        totalBytes = if (total > 0L) total else 0L,
                    )
                }
            }
            _statusMap.value = map
        }
    }

    /** 加入下载队列 */
    fun enqueueDownload(item: DownloadItem) {
        viewModelScope.launch {
            downloadStore.upsert(item)
            VideoDownloadService.enqueue(getApplication(), item.liveId, item.m3u8Url)
            refreshStatus()
        }
    }

    /** 判断是否已在下载列表 */
    suspend fun isInDownloadList(liveId: String): Boolean = downloadStore.contains(liveId)

    /** 暂停单条 */
    fun pauseDownload(liveId: String) {
        VideoDownloadService.pause(getApplication(), liveId)
    }

    /** 恢复单条 */
    fun resumeDownload(liveId: String) {
        VideoDownloadService.resume(getApplication(), liveId)
    }

    /** 删除单条 (同时清本地分片 + 索引) */
    fun deleteDownload(liveId: String) {
        viewModelScope.launch {
            VideoDownloadService.remove(getApplication(), liveId)
            downloadStore.delete(liveId)
        }
    }

    /** 暂停全部 */
    fun pauseAll() {
        VideoDownloadService.pauseAll(getApplication())
    }

    /** 恢复全部 */
    fun resumeAll() {
        VideoDownloadService.resumeAll(getApplication())
    }
}
