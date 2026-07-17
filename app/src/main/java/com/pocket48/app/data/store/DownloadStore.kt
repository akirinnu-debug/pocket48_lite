package com.pocket48.app.data.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pocket48.app.data.model.DownloadItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.downloadDataStore by preferencesDataStore("download_index_prefs")

private val DOWNLOADS_KEY = stringPreferencesKey("download_list")

/**
 * 下载任务索引 (持久化)
 *
 * 只存"用户主动加入下载列表"的元信息 (liveId/title/cover/m3u8Url/lrcUrl 等)
 * 进度/状态从 VideoCacheManager.downloadManager 实时查询 (DownloadManager.downloadIndex)
 * 两者通过 liveId 关联 (DownloadRequest.id = liveId)
 *
 * 不存进度的原因: 进度由 DownloadManager 内部维护, 写入 DataStore 会双源同步困难
 */
class DownloadStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(DownloadItem.serializer())

    /** 下载列表 (响应式) */
    val downloadFlow: Flow<List<DownloadItem>> = context.downloadDataStore.data.map { prefs ->
        prefs[DOWNLOADS_KEY]?.let { text ->
            runCatching { json.decodeFromString(serializer, text) }.getOrNull()
        } ?: emptyList()
    }

    /** 添加一条下载索引 (重复 liveId 视为更新元信息) */
    suspend fun upsert(item: DownloadItem) {
        context.downloadDataStore.edit { prefs ->
            val current = readList(prefs[DOWNLOADS_KEY])
            val merged = (listOf(item) + current.filter { it.liveId != item.liveId })
            prefs[DOWNLOADS_KEY] = json.encodeToString(serializer, merged)
        }
    }

    /** 删除单条索引 (本地分片删除由 DownloadManager.removeDownload 处理) */
    suspend fun delete(liveId: String) {
        context.downloadDataStore.edit { prefs ->
            val current = readList(prefs[DOWNLOADS_KEY])
            prefs[DOWNLOADS_KEY] = json.encodeToString(
                serializer,
                current.filter { it.liveId != liveId },
            )
        }
    }

    /** 判断是否已在下载列表 */
    suspend fun contains(liveId: String): Boolean {
        var result = false
        context.downloadDataStore.edit { prefs ->
            val current = readList(prefs[DOWNLOADS_KEY])
            result = current.any { it.liveId == liveId }
        }
        return result
    }

    private fun readList(raw: String?): List<DownloadItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }
}
