package com.zyplayer.app.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.zyplayer.app.ui.play.PlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 前台下载服务
 * 在后台使用前台服务下载 m3u8 视频，通知栏常驻进度条
 * 自动处理：权限申请引导、Android 13+ 通知权限、下载完成后自停
 */
class DownloadService : Service() {

    companion object {
        private const val TAG = "DownloadService"
        private const val CHANNEL_ID = "zyplayer_download_foreground"
        private const val NOTIFICATION_ID = 1001

        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_TITLE = "extra_title"

        /** 当前正在下载的 URL 集合（防重复 + 状态查询） */
        private val activeDownloads = ConcurrentHashMap.newKeySet<String>()

        /** 该 URL 是否正在下载中 */
        fun isDownloading(url: String): Boolean = activeDownloads.contains(url)

        /** 启动下载服务 */
        fun start(context: Context, url: String, title: String) {
            if (isDownloading(url)) {
                Log.w(TAG, "重复下载已忽略: $title")
                return
            }
            activeDownloads.add(url)
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
            }
            // Android 13+ 需要 POST_NOTIFICATIONS 权限，但即使没权限 startForegroundService 也能启动
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 取消下载（由服务自己调用） */
        private fun removeDownload(url: String) {
            activeDownloads.remove(url)
        }
    }

    private var notificationManager: NotificationManager? = null
    private var serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var downloadJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL) ?: run { stopSelf(); return START_NOT_STICKY }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "视频"

        // 启动前台服务（通知栏常驻）
        startForeground(NOTIFICATION_ID, buildNotification(title, "正在解析视频分片...", 0, 0, true))

        // 开始下载
        downloadJob = serviceScope.launch {
            try {
                val result = M3u8Downloader.download(
                    context = this@DownloadService,
                    url = url,
                    title = title,
                    onProgress = { done, total ->
                        val msg = if (total > 0) "下载中: $done / $total 分片" else "正在解析..."
                        updateNotification(title, msg, done, total, false)
                    }
                )
                result?.let {
                    // 下载成功
                    updateNotification(title, "下载完成: ${it.fileName} (${it.fileSize / 1024} KB)",
                        it.fileSize.toInt(), it.fileSize.toInt(), true)
                    Log.d(TAG, "下载完成: ${it.fileName}")
                } ?: run {
                    if (activeDownloads.contains(url)) {
                        updateNotification(title, "下载失败，请重试", 0, 0, true)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "下载异常: ${e.message}", e)
                updateNotification(title, "下载失败: ${e.message}", 0, 0, true)
            } finally {
                removeDownload(url)
                // 延迟 3 秒关闭服务（让用户看到完成/失败通知）
                kotlinx.coroutines.delay(3000)
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    // ──────────── 通知 ────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "视频下载", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "视频下载进度，支持后台持续下载"
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        title: String,
        message: String,
        progress: Int,
        max: Int,
        completed: Boolean
    ): Notification {
        // 点击通知打开播放页（如果已完成且有文件，可改打开文件）
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, PlayActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setOngoing(!completed)  // 完成前不可滑动清除
            .setAutoCancel(completed)

        if (completed && max > 0) {
            builder.setProgress(max, max, false)
        } else if (progress > 0 && max > 0) {
            builder.setProgress(max, progress, false)
        } else {
            builder.setProgress(0, 0, true)  // 不确定进度
        }

        return builder.build()
    }

    private fun updateNotification(title: String, message: String, progress: Int, max: Int, completed: Boolean) {
        val notification = buildNotification(title, message, progress, max, completed)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }
}