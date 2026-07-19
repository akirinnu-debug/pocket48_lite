package com.pocket48.app.data.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pocket48.app.Pocket48App
import com.pocket48.app.data.model.MemberInfo
import com.pocket48.app.data.model.MembersData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File

private val Context.memberDataStore by preferencesDataStore("member_prefs")

private val FAVORITE_KEY = stringSetPreferencesKey("favorite_ids")
private val VERSION_KEY = intPreferencesKey("members_version")

/** 远程 members.json 固定 URL (raw.githubusercontent.com, master 分支, 仓库根目录) */
private const val REMOTE_URL =
    "https://raw.githubusercontent.com/akirinnu-debug/pocket48_lite/master/members.json"

/** members.json 远程更新检查结果 */
sealed class MemberUpdateResult {
    /** 本地已是最新版本 (远程 version <= 本地 version) */
    data class UpToDate(val version: Int) : MemberUpdateResult()
    /** 已成功拉取并写入新版本 */
    data class Updated(val version: Int, val count: Int) : MemberUpdateResult()
    /** 拉取失败 (网络/解析错误等), 不抛异常 */
    data class Failed(val message: String) : MemberUpdateResult()
}

/**
 * 本地成员数据管理 + 远程更新
 *
 * - loadMembers 优先读 filesDir/members.json (远程更新写入), 回退 assets/members.json (出厂版本)
 * - 兼容旧纯数组格式与新 wrapper 格式 (MembersData)
 * - checkAndUpdateFromRemote: 拉远程, 对比 version, 必要时写入 filesDir
 * - 收藏 ID 仍用 DataStore Preferences 持久化
 */
class MemberStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    /** 本地版本号 (响应式, 0 表示从未拉取过远程, 仅使用 assets) */
    val localVersion: Flow<Int> = context.memberDataStore.data.map { it[VERSION_KEY] ?: 0 }

    /** 收藏成员 ID 列表 (响应式) */
    val favoriteIds: Flow<Set<Long>> = context.memberDataStore.data.map { prefs ->
        prefs[FAVORITE_KEY]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }

    /** 加载全部成员
     *  优先 filesDir/members.json (远程更新写入), 回退 assets/members.json (出厂版本)
     *  兼容旧纯数组格式与新 wrapper 格式 */
    fun loadMembers(): List<MemberInfo> {
        // 1. filesDir (远程更新写入的覆盖文件)
        val localFile = File(context.filesDir, "members.json")
        if (localFile.exists()) {
            runCatching {
                val data = json.decodeFromString<MembersData>(localFile.readText())
                if (data.members.isNotEmpty()) return data.members
            }
        }
        // 2. assets (出厂版本, 新 wrapper 格式; 若解析失败回退旧纯数组格式)
        return try {
            val text = context.assets.open("members.json").bufferedReader().use { it.readText() }
            runCatching {
                json.decodeFromString<MembersData>(text).members
            }.getOrElse {
                // 旧 assets 格式 (纯数组, 历史版本兼容)
                json.decodeFromString<List<MemberInfo>>(text)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 切换收藏状态 */
    suspend fun toggleFavorite(userId: Long) {
        context.memberDataStore.edit { prefs ->
            val current = prefs[FAVORITE_KEY] ?: emptySet()
            val key = userId.toString()
            prefs[FAVORITE_KEY] = if (key in current) current - key else current + key
        }
    }

    /**
     * 检查并拉取远程 members.json 更新
     *
     * - 复用 Pocket48Api.httpClient (带 OkHttp 缓存, ETag/304 自动处理)
     * - version <= 本地 → 不更新 (返回 UpToDate)
     * - version > 本地 → 写入 filesDir/members.json, 更新本地 version (返回 Updated)
     * - 失败不抛异常, 返回 Failed(message)
     *
     * 调用方: Pocket48App.onCreate 启动后台检查 + AboutScreen "检查更新" 按钮
     */
    suspend fun checkAndUpdateFromRemote(): MemberUpdateResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(REMOTE_URL).build()
            val response = Pocket48App.instance.pocket48Api.httpClient.newCall(request).execute()
            val text = response.body?.string()
                ?: return@withContext MemberUpdateResult.Failed("空响应")
            val data = json.decodeFromString<MembersData>(text)
            val currentLocal = context.memberDataStore.data.first()[VERSION_KEY] ?: 0
            if (data.version <= currentLocal) {
                return@withContext MemberUpdateResult.UpToDate(data.version)
            }
            // 写入 filesDir/members.json (覆盖 assets 的出厂版本)
            File(context.filesDir, "members.json").writeText(text)
            // 更新本地版本号
            context.memberDataStore.edit { it[VERSION_KEY] = data.version }
            MemberUpdateResult.Updated(data.version, data.members.size)
        } catch (e: Exception) {
            MemberUpdateResult.Failed(e.message ?: "未知错误")
        }
    }
}
