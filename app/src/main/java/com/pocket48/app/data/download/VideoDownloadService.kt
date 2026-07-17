package com.pocket48.app.data.download

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.NotificationUtil
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.pocket48.app.MainActivity
import com.pocket48.app.R
import com.pocket48.app.data.VideoCacheManager

/**
 * 视频下载前台服务
 *
 * - 持有 DownloadManager, 在前台通知栏展示下载进度
 * - 通知点击 → 打开 App
 * - 通知渠道 IMPORTANCE_LOW (无声音, 不打扰)
 * - 通过 DownloadService.sendAddDownload() 静态方法触发启动
 *
 * minSdk 26 满足前台服务 + 通知渠道要求
 * targetSdk 34 需在 Manifest 声明 foregroundServiceType="dataSync"
 *
 * Media3 1.4.1 DownloadService 5 参数构造:
 * (notificationId, updateIntervalMs, channelId, channelNameResId, channelDescResId)
 */
class VideoDownloadService : DownloadService(
    NOTIFICATION_ID,
    /* foregroundNotificationUpdateInterval = */ 1000L,
    CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description,
) {

    override fun getDownloadManager(): DownloadManager {
        return VideoCacheManager.getDownloadManager(this)
    }

    /**
     * 调度器: 在 API 26+ 可用 PlatformScheduler (基于 JobScheduler)
     * 这里返回 null, 让 DownloadManager 用内置线程池执行下载
     * (Scheduler 主要用于"在 WiFi/充电 时延迟下载"等约束场景, 本项目不需要)
     */
    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int,
    ): Notification {
        val active = downloads.filter {
            it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED
        }
        val completed = downloads.count { it.state == Download.STATE_COMPLETED }
        val total = downloads.size

        return if (active.isNotEmpty()) {
            // 计算总体进度 (Media3 1.4.1: getPercentDownloaded() 返回 0..100 或 PERCENTAGE_UNSET=-1f)
            val percents = active.mapNotNull {
                val p = it.percentDownloaded
                if (p < 0f) null else p
            }
            val avgPercent = if (percents.isNotEmpty()) percents.average().toInt() else 0
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("正在下载视频")
                .setContentText("${active.size} 个任务进行中 · 已完成 $completed/$total")
                .setProgress(100, avgPercent, avgPercent == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(buildContentIntent())
                .build()
        } else {
            // 无活跃任务时显示静态状态 (用于停止前台服务前的最后通知)
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Pocket48 视频下载")
                .setContentText("已完成 $completed/$total 个任务")
                .setOngoing(false)
                .setContentIntent(buildContentIntent())
                .build()
        }
    }

    private fun buildContentIntent(): PendingIntent? {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "video_download"

        /**
         * 提交下载请求 (业务层入口, 由 DownloadViewModel 调用)
         *
         * 内部 sendAddDownload 会自动启动前台服务
         * foreground=true: 用 startForegroundService 启动, 确保服务在 Android 8+ 上能正确启动
         * (即使 app 短暂切到后台也不会被系统拒绝)
         */
        fun enqueue(
            context: Context,
            liveId: String,
            m3u8Url: String,
        ) {
            val request = androidx.media3.exoplayer.offline.DownloadRequest.Builder(
                liveId, android.net.Uri.parse(m3u8Url),
            ).build()
            sendAddDownload(context, VideoDownloadService::class.java, request, true)
        }

        fun pause(context: Context, liveId: String) {
            sendSetStopReason(context, VideoDownloadService::class.java, liveId, 1, false)
        }

        fun resume(context: Context, liveId: String) {
            sendSetStopReason(context, VideoDownloadService::class.java, liveId,
                androidx.media3.exoplayer.offline.Download.STOP_REASON_NONE, false)
        }

        fun remove(context: Context, liveId: String) {
            sendRemoveDownload(context, VideoDownloadService::class.java, liveId, false)
        }

        fun pauseAll(context: Context) {
            sendPauseDownloads(context, VideoDownloadService::class.java, false)
        }

        fun resumeAll(context: Context) {
            sendResumeDownloads(context, VideoDownloadService::class.java, false)
        }
    }
}
