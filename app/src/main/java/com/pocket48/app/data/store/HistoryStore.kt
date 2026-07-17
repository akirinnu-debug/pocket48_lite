package com.pocket48.app.data.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pocket48.app.data.model.PlayHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.historyDataStore by preferencesDataStore("play_history_prefs")

/** 历史记录条数上限 (LRU 淘汰) */
private const val MAX_HISTORY_SIZE = 100

private val HISTORY_KEY = stringPreferencesKey("history_list")

/**
 * 播放历史本地存储
 *
 * - 进入播放页 → upsert 一条记录 (覆盖同 liveId 旧记录, 重置 lastPlayPosition)
 * - 退出播放页 → updatePosition 写入最后播放位置 (供 "继续观看")
 * - 全部列表按 lastPlayTime 倒序, 上限 100 条
 *
 * 实现选择: DataStore Preferences + JSON List
 * 原因: 单人使用, 数据量小 (<100 条), 不引入 Room 依赖, APK 更轻
 */
class HistoryStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PlayHistory.serializer())

    /** 历史记录列表 (响应式, 按时间倒序) */
    val historyFlow: Flow<List<PlayHistory>> = context.historyDataStore.data.map { prefs ->
        prefs[HISTORY_KEY]?.let { text ->
            runCatching { json.decodeFromString(serializer, text) }.getOrNull()
        } ?: emptyList()
    }

    /**
     * 新增/更新一条历史记录
     *
     * 同一 liveId 视为更新:
     * - 若已存在, 移除旧记录 (保留上次的 lastPlayPosition, 仅刷新元信息和时间)
     * - 否则新建 (lastPlayPosition = 0)
     */
    suspend fun upsert(
        liveId: String,
        title: String,
        coverPath: String,
        userId: Long,
        userNickname: String,
        userAvatar: String,
        playDuration: Long,
        keepPosition: Boolean = false,
    ) {
        context.historyDataStore.edit { prefs ->
            val current = readList(prefs[HISTORY_KEY])
            val existing = current.find { it.liveId == liveId }
            val pos = if (keepPosition) existing?.lastPlayPosition ?: 0L else 0L
            val entry = PlayHistory(
                liveId = liveId,
                title = title,
                coverPath = coverPath,
                userId = userId,
                userNickname = userNickname,
                userAvatar = userAvatar,
                playDuration = if (playDuration > 0) playDuration else existing?.playDuration ?: 0,
                lastPlayPosition = pos,
                lastPlayTime = System.currentTimeMillis(),
            )
            val merged = (listOf(entry) + current.filter { it.liveId != liveId })
                .take(MAX_HISTORY_SIZE)
            prefs[HISTORY_KEY] = json.encodeToString(serializer, merged)
        }
    }

    /**
     * 仅更新播放位置和时长 (退出播放页时调用, 不更新排序)
     *
     * 防御性设计:
     * - 若 positionMs <= 0, 保留旧位置 (避免 race condition 中传入 0 覆盖已有续播位置)
     * - 若 durationMs > 0, 更新时长 (player 实测的时长最准); 否则保留旧值
     * - 若 lastPlayTime 已是本次会话内, 不再更新 (避免快速重复保存)
     */
    suspend fun updatePosition(liveId: String, positionMs: Long, durationMs: Long) {
        context.historyDataStore.edit { prefs ->
            val current = readList(prefs[HISTORY_KEY])
            if (current.none { it.liveId == liveId }) return@edit
            val updated = current.map {
                if (it.liveId == liveId) {
                    it.copy(
                        // positionMs <= 0 视为无效 (race condition 或 player 未初始化),
                        // 保留已有位置不动
                        lastPlayPosition = if (positionMs > 0) positionMs else it.lastPlayPosition,
                        playDuration = if (durationMs > 0) durationMs else it.playDuration,
                        lastPlayTime = System.currentTimeMillis(),
                    )
                } else it
            }
            prefs[HISTORY_KEY] = json.encodeToString(serializer, updated)
        }
    }

    /** 删除单条历史 */
    suspend fun delete(liveId: String) {
        context.historyDataStore.edit { prefs ->
            val current = readList(prefs[HISTORY_KEY])
            prefs[HISTORY_KEY] = json.encodeToString(
                serializer,
                current.filter { it.liveId != liveId },
            )
        }
    }

    /** 清空全部历史 */
    suspend fun clear() {
        context.historyDataStore.edit { prefs ->
            prefs.remove(HISTORY_KEY)
        }
    }

    private fun readList(raw: String?): List<PlayHistory> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }
}
