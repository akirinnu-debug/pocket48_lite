package com.pocket48.app.data

import android.content.Context
import androidx.media3.database.ExoDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import java.io.File

/**
 * 视频缓存 + 下载管理
 *
 * 设计原则:
 * - cache 路径放在 filesDir 而非 cacheDir
 *   (cacheDir 会被系统在低存储时自动清理, 下载内容必须持久化)
 * - 被动观看缓存 + 主动下载共用同一个 SimpleCache
 *   (播放时 CacheDataSource 自动判断本地有无, 离线可播)
 * - 容量上限 4GB, LRU 淘汰
 *   (原 2GB 在加入主动下载后偏紧, 4GB 仍属轻量范围)
 *
 * 提供能力:
 * 1. cacheDataSourceFactory() - 给 ExoPlayer 播放用 (优先读本地)
 * 2. downloadManager - 给 VideoDownloadService 用 (HLS 分片主动下载)
 * 3. enqueueDownload() - 业务层便捷入口
 * 4. isDownloaded() / removeDownload() - 离线状态查询与删除
 */
object VideoCacheManager {

    /** 缓存总上限 (4GB, 包含被动观看 + 主动下载) */
    private const val MAX_CACHE_BYTES = 4L * 1024 * 1024 * 1024

    /** cache 目录名 (位于 filesDir 下, 持久化) */
    private const val CACHE_DIR_NAME = "exo_cache"

    private var cache: SimpleCache? = null
    private var downloadManager: DownloadManager? = null

    /** 获取共享 SimpleCache 实例 (被动观看 + 下载共用) */
    @Synchronized
    fun getCache(context: Context): SimpleCache {
        return cache ?: run {
            val dbProvider = ExoDatabaseProvider(context)
            val cacheDir = File(context.filesDir, CACHE_DIR_NAME).apply { if (!exists()) mkdirs() }
            SimpleCache(
                cacheDir,
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                dbProvider,
            ).also { cache = it }
        }
    }

    /** 创建接入缓存的数据源工厂 (ExoPlayer 播放用) */
    fun cacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val cache = getCache(context)
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory()
                    .setCache(cache)
                    .setFragmentSize(4 * 1024 * 1024)
            )
            // 离线时若本地无缓存, 不再尝试网络 (避免反复超时)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** 当前缓存大小 (字节) */
    fun cacheSize(context: Context): Long = getCache(context).cacheSpace

    /** 获取 DownloadManager 单例 (VideoDownloadService 用) */
    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        return downloadManager ?: run {
            val ctx = context.applicationContext
            // Media3 1.4.1 无 Builder, 用 5 参数构造:
            // (context, databaseProvider, cache, upstreamFactory, executor)
            // CacheDataSource 内部由 DownloadManager 自动构造
            val mgr = DownloadManager(
                ctx,
                ExoDatabaseProvider(ctx),
                getCache(ctx),
                DefaultHttpDataSource.Factory(),
                java.util.concurrent.Executors.newFixedThreadPool(2),
            )
            // 并发为 1: 移动网络下单线下载更稳定, 避免多任务抢占带宽导致全部超时
            // (HLS 单流分片密集, 多并发对 CDN 友好度差且耗电)
            mgr.maxParallelDownloads = 1
            // 关键: 显式 resumeDownloads 触发内部 TaskManager 调度
            // 否则部分场景下 addDownload 后下载一直停在 STATE_QUEUED
            // (DownloadManager 构造时 paused=false, 但 task 调度需 resume 唤醒)
            mgr.resumeDownloads()
            downloadManager = mgr
            mgr
        }
    }

    /**
     * 业务层入口: 提交一个 HLS 视频下载请求
     *
     * - 同一直播重复提交会自动去重 (DownloadManager 内部按 contentId 处理)
     * - 通过 VideoDownloadService 前台服务执行, 通知栏显示进度
     */
    fun enqueueDownload(context: Context, liveId: String, m3u8Url: String) {
        val request = DownloadRequest.Builder(liveId, m3u8Url.toUri()).build()
        getDownloadManager(context).addDownload(request)
    }

    /** 暂停下载 (保留已下载分片) */
    fun pauseDownload(context: Context, liveId: String) {
        getDownloadManager(context).setStopReason(liveId, /* stopReason= */ 1)
    }

    /** 恢复下载 */
    fun resumeDownload(context: Context, liveId: String) {
        getDownloadManager(context).setStopReason(liveId, Download.STOP_REASON_NONE)
    }

    /** 删除下载 (同时清掉本地分片) */
    fun removeDownload(context: Context, liveId: String) {
        getDownloadManager(context).removeDownload(liveId)
    }

    /** 查询下载状态 (null 表示从未加入下载) */
    fun getDownload(context: Context, liveId: String): Download? {
        return getDownloadManager(context).downloadIndex.getDownload(liveId)
    }

    /** 是否已下载完成 */
    fun isDownloaded(context: Context, liveId: String): Boolean {
        val dl = getDownload(context, liveId) ?: return false
        return dl.state == Download.STATE_COMPLETED
    }

    /** 手动清除全部缓存 (被动观看 + 下载内容) */
    @Synchronized
    fun clearCache(context: Context) {
        downloadManager?.release()
        downloadManager = null
        cache?.release()
        cache = null
        File(context.filesDir, CACHE_DIR_NAME).deleteRecursively()
    }

    /** 给 Kotlin 的 String 转 Uri 用 (避免顶部 import 太多) */
    private fun String.toUri(): android.net.Uri = android.net.Uri.parse(this)
}