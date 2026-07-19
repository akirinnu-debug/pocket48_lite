package com.pocket48.app.data.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.cursorIndexDataStore by preferencesDataStore("cursor_index_prefs")

private val ENTRIES_KEY = stringPreferencesKey("entries")

/** Cursor 索引单条记录
 *
 *  表示"用 entryCursor 翻出的那一页"的时间范围元信息:
 *  - entryCursor: 翻这页时传给 API 的 next (即"从该 cursor 开始翻")
 *  - nextCursor:  这页返回的 next (翻下一页用)
 *  - firstItemTime / lastItemTime: 这页最新/最旧项的 displayTime
 *    (列表 DESC, firstItemTime >= lastItemTime)
 *  - itemCount: 这页的 item 数 (用于诊断)
 *  - fetchedAt: 写入时间戳, 用于 180 天 TTL 淘汰
 *
 *  userId != 0 时仅对应该成员的列表; userId == 0 是全成员列表索引 (本次未启用)
 */
@Serializable
data class CursorIndexEntry(
    val userId: Long,
    val entryCursor: String,
    val nextCursor: String,
    val firstItemTime: Long,
    val lastItemTime: Long,
    val itemCount: Int,
    val fetchedAt: Long,
)

/**
 * Cursor → 时间范围 持久化索引
 *
 * 沿用 DownloadStore 的 DataStore + JSON 范式. 每次 fetchLiveList 成功后追加一条,
 * 下次定位时二分查找跳过前面所有页, 显著加速二次定位.
 *
 * 关键设计:
 * - 按 userId 分组 (cursor 顺序在不同 userId 间不兼容)
 * - entryCursor == "0" 的 entry 直接覆盖 (page 1 内容随时间漂移, 不信任旧值)
 * - 200 条/userId FIFO 上限 (超出按 firstItemTime 最小=最旧 淘汰)
 * - 180 天 TTL (fetchedAt 早于 180 天的 entry 在 append 时清理)
 * - findCandidate 容忍补传非单调: 命中后额外探测前一个更旧的 entry
 */
class CursorIndexStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(CursorIndexEntry.serializer())

    /** 全部 entries (响应式) */
    val entriesFlow: Flow<List<CursorIndexEntry>> = context.cursorIndexDataStore.data.map { prefs ->
        prefs[ENTRIES_KEY]?.let { text ->
            runCatching { json.decodeFromString(serializer, text) }.getOrNull()
        } ?: emptyList()
    }

    /** 按 userId 分组的索引 (响应式) */
    val indexFlow: Flow<Map<Long, List<CursorIndexEntry>>> = entriesFlow.map { list ->
        list.groupBy { it.userId }
    }

    /** 追加一条 entry, 触发去重 / 覆盖 / 容量上限 / TTL 清理
     *  fire-and-forget 调用, 不阻塞 UI */
    suspend fun append(entry: CursorIndexEntry) {
        context.cursorIndexDataStore.edit { prefs ->
            val current = readList(prefs[ENTRIES_KEY])
            val now = System.currentTimeMillis()
            val ttlCutoff = now - TTL_MS

            // 1. TTL 清理: 丢掉 180 天前的 entry
            val afterTtl = current.filter { it.fetchedAt >= ttlCutoff }

            // 2. 同 userId 处理
            val sameUser = afterTtl.filter { it.userId == entry.userId }
            val otherUsers = afterTtl.filter { it.userId != entry.userId }

            // 3. cursor="0" 覆盖 (page 1 漂移); 同 entryCursor 也覆盖 (重新翻同一页)
            val dedupSameUser = sameUser.filter {
                it.entryCursor != "0" && it.entryCursor != entry.entryCursor
            }
            var merged = dedupSameUser + entry

            // 4. 容量上限 200/userId: 按 firstItemTime DESC 排序, 保留最新 200 条 (最旧的=最早的被淘汰)
            merged = merged.sortedByDescending { it.firstItemTime }.take(MAX_ENTRIES_PER_USER)

            prefs[ENTRIES_KEY] = json.encodeToString(serializer, otherUsers + merged)
        }
    }

    /**
     * 在 userId 的索引中找目标时间段对应的起跳 entry
     *
     * 策略:
     * 1. 按 firstItemTime DESC 排序 (newest 在前)
     * 2. 二分找第一个 firstItemTime <= targetMs 的 entry (即最新项不晚于目标的最早 entry)
     * 3. 若该 entry 的 lastItemTime <= targetMs → target 落在本页内, 返回该 entry
     * 4. 若 targetMs < lastItemTime → target 比本页还旧, 本页是最近的起跳点, 返回该 entry
     * 5. 容忍补传非单调: 额外探测下一个更旧的 entry, 若其区间包含 target 也返回
     *
     * @return 起跳 entry (用其 entryCursor 开始翻); null 表示索引未覆盖该时间段
     */
    suspend fun findCandidate(userId: Long, targetMs: Long): CursorIndexEntry? {
        val all = entriesFlow.first()
        val userEntries = all.filter { it.userId == userId }
            .sortedByDescending { it.firstItemTime }
        if (userEntries.isEmpty()) return null

        // 二分: 找第一个 firstItemTime <= targetMs
        // userEntries 是 DESC 排序 (firstItemTime 从大到小)
        // 我们要找首个 firstItemTime <= targetMs 的位置
        var lo = 0
        var hi = userEntries.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (userEntries[mid].firstItemTime > targetMs) {
                lo = mid + 1  // mid 的 firstItemTime 太大 (太新), 往后找
            } else {
                hi = mid
            }
        }
        // lo 是首个 firstItemTime <= targetMs 的索引; 若全部都比 target 新, lo == size
        if (lo >= userEntries.size) {
            // target 比所有 entry 都旧 → 用最旧的那个 entry 起跳
            return userEntries.last()
        }

        val candidate = userEntries[lo]
        // 容忍补传非单调: 探测下一个更旧的 entry
        val older = userEntries.getOrNull(lo + 1)
        if (older != null && targetMs in older.lastItemTime..older.firstItemTime) {
            return older
        }
        return candidate
    }

    private fun readList(raw: String?): List<CursorIndexEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    companion object {
        /** 单 userId 最多保留的 entry 数 */
        private const val MAX_ENTRIES_PER_USER = 200
        /** TTL: 180 天 */
        private const val TTL_MS = 180L * 24 * 60 * 60 * 1000
    }
}
