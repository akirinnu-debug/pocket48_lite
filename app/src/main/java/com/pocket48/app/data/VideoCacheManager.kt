package com.pocket48.app.data

import android.content.Context
import androidx.media3.database.ExoDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.DefaultHttpDataSource
import java.io.File

/** 视频离线缓存上限 (2GB) */
private const val MAX_CACHE_BYTES = 2L * 1024 * 1024 * 1024

/**
 * ExoPlayer 视频缓存管理器
 *
 * 看过的直播回放自动缓存 .ts 分片, 再次观看走本地无需网络
 * LRU 淘汰: 缓存满 2GB 时自动删除最久未访问的分片
 */
object VideoCacheManager {

    private var cache: SimpleCache? = null

    fun getCache(context: Context): SimpleCache {
        return cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(context.cacheDir, "exo_cache"),
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                ExoDatabaseProvider(context),
            ).also { cache = it }
        }
    }

    /** 创建接入缓存的数据源工厂 */
    fun cacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val cache = getCache(context)
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory()
                    .setCache(cache)  // 必须显式传入 cache，否则 HLS 加载时会 NPE
                    .setFragmentSize(4 * 1024 * 1024)
            )
    }

    /** 当前缓存大小 (字节) */
    fun cacheSize(context: Context): Long = getCache(context).cacheSpace

    /** 手动清除缓存 */
    fun clearCache(context: Context) {
        getCache(context).release()
        cache = null
        File(context.cacheDir, "exo_cache").deleteRecursively()
    }
}
