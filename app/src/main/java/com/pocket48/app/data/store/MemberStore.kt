package com.pocket48.app.data.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pocket48.app.data.model.MemberInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.memberDataStore by preferencesDataStore("member_prefs")

/**
 * 本地成员数据管理
 * - 从 assets/members.json 加载成员数据
 * - 使用 DataStore 管理收藏的成员 ID
 */
class MemberStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val FAVORITE_KEY = stringSetPreferencesKey("favorite_ids")

    /** 收藏成员 ID 列表 (响应式) */
    val favoriteIds: Flow<Set<Long>> = context.memberDataStore.data.map { prefs ->
        prefs[FAVORITE_KEY]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }

    /** 从 assets 加载全部成员数据 */
    fun loadMembers(): List<MemberInfo> {
        return try {
            val text = context.assets.open("members.json").bufferedReader().use { it.readText() }
            json.decodeFromString(text)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 切换收藏状态 */
    suspend fun toggleFavorite(userId: Long) {
        context.memberDataStore.edit { prefs ->
            val current = prefs[FAVORITE_KEY] ?: emptySet()
            val key = userId.toString()
            prefs[FAVORITE_KEY] = if (key in current) {
                current - key
            } else {
                current + key
            }
        }
    }
}
