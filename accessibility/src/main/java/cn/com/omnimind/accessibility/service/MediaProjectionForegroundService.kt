package cn.com.omnimind.accessibility.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import cn.com.omnimind.accessibility.action.ScreenCaptureManager

/**
 * Android 10 (API 29) 起，MediaProjection 必须在声明为 FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION 的前台服务中使用。
 * 在请求录屏权限前先启动本服务并调用 startForeground()，授权结果通过本服务创建 MediaProjection 并交给 ScreenCaptureManager。
 */
class MediaProjectionForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOREGROUND -> {
                startForegroundWithNotification()
            }
            ACTION_ON_MEDIA_PROJECTION_RESULT -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                @Suppress("DEPRECATION")
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (data != null && resultCode == android.app.Activity.RESULT_OK) {
                    val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val mediaProjection = mpm.getMediaProjection(resultCode, data)
                    ScreenCaptureManager.getInstance().setMediaProjection(mediaProjection)
                    // 保持前台：MediaProjection 与前台服务绑定，服务一停投影即失效
                    updateNotificationContent("录屏权限已开启")
                }
                ScreenCaptureManager.getInstance().onMediaProjectionReady()
                // 不要 stopSelf()，否则 MediaProjection 会立刻失效，createVirtualDisplay 会报 Invalid media projection
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val channelId = "media_projection_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "屏幕录制",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = buildNotification("正在请求录屏权限…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotificationContent(text: String) {
        val notification = buildNotification(text)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(contentText: String): Notification {
        val channelId = "media_projection_channel"
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("屏幕录制")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "MediaProjectionFgService"
        const val ACTION_START_FOREGROUND = "cn.com.omnimind.accessibility.MEDIA_PROJECTION_START"
        const val ACTION_ON_MEDIA_PROJECTION_RESULT = "cn.com.omnimind.accessibility.MEDIA_PROJECTION_RESULT"
        const val ACTION_STOP = "cn.com.omnimind.accessibility.MEDIA_PROJECTION_STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val NOTIFICATION_ID = 2001
    }
}
