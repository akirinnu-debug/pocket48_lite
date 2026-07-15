package com.pocket48.app.data.api

import android.util.Base64
import android.util.Log
import com.pocket48.app.data.model.LiveDetail
import com.pocket48.app.data.model.LiveListContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Pocket48 轻量 API 客户端
 * 仅保留直播相关接口，无需 Token 认证
 */
class Pocket48Api(cacheDir: java.io.File? = null) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .cache(cacheDir?.let { dir ->
            okhttp3.Cache(java.io.File(dir, "okhttp"), 50L * 1024 * 1024) // 50MB
        })
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val baseUrl = "https://pocketapi.48.cn"

    private val baseHeaders = mapOf(
        "Content-Type" to "application/json;charset=UTF-8",
        "User-Agent" to "PocketFans201807/7.0.4 (iPhone; iOS 16.3.1; Scale/2.00)",
        "Accept-Language" to "zh-Hans-CN;q=1",
        "Host" to "pocketapi.48.cn",
        "appInfo" to """{"vendor":"apple","deviceId":"debug-uuid","appVersion":"7.0.4","appBuild":"23011601","osVersion":"16.3.1","osType":"ios","deviceName":"iPhone XR","os":"ios"}""",
        "token" to "",
    )

    private fun genPa(): String {
        val ts = System.currentTimeMillis().toString()
        val u1 = UUID.randomUUID().toString()
        val u2 = UUID.randomUUID().toString()
        return Base64.encodeToString("$ts,$u1,$u2".toByteArray(), Base64.NO_WRAP)
    }

    private suspend fun post(path: String, body: JsonObject): JsonObject? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl$path")
                    .apply {
                        baseHeaders.forEach { (k, v) -> header(k, v) }
                        header("pa", genPa())
                    }
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val text = response.body?.string() ?: return@withContext null
                Log.d(TAG, "POST $path -> ${response.code}: ${text.take(300)}")
                json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                Log.e(TAG, "POST $path failed", e)
                null
            }
        }

    /** [2.1] 获取直播/回放列表 (免 Token) */
    suspend fun fetchLiveList(
        record: Boolean = true,
        next: String = "0",
        userId: Long = 0,
        groupId: Int = 0,
    ): LiveListContent? {
        val body = buildJsonObject {
            put("debug", true)
            put("next", next)
            put("record", record)
            if (userId != 0L) {
                put("userId", userId)
            } else {
                put("groupId", groupId)
            }
        }
        val resp = post("/live/api/v1/live/getLiveList", body) ?: return null
        if (resp["status"]?.jsonPrimitive?.intOrNull != 200) {
            Log.e(TAG, "fetchLiveList status != 200: ${resp["status"]}, message: ${resp["message"]}")
            return null
        }
        val content = resp["content"] ?: return null
        return json.decodeFromString<LiveListContent>(content.toString())
    }

    /** [2.2] 获取直播/回放详情 (免 Token, 含 m3u8 和 lrc 地址) */
    suspend fun fetchLiveOne(liveId: String): LiveDetail? {
        val body = buildJsonObject { put("liveId", liveId) }
        val resp = post("/live/api/v1/live/getLiveOne", body) ?: return null
        if (resp["status"]?.jsonPrimitive?.intOrNull != 200) {
            Log.e(TAG, "fetchLiveOne status != 200: ${resp["status"]}, message: ${resp["message"]}")
            return null
        }
        val content = resp["content"] ?: return null
        return json.decodeFromString<LiveDetail>(content.toString())
    }

    /** [14.1] 获取 LRC 弹幕/歌词文件 */
    suspend fun fetchLrc(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            response.body?.string()
        } catch (e: Exception) {
            Log.e(TAG, "fetchLrc failed", e)
            null
        }
    }

    companion object {
        private const val TAG = "Pocket48Api"
    }
}
