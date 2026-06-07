package cn.com.omnimind.bot.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import cn.com.omnimind.assists.AssistsCore
import cn.com.omnimind.assists.api.bean.TaskParams
import cn.com.omnimind.bot.R

class OmniForegroundService : Service() {
    private val CHANNEL_ID = "ForegroundServiceChannel"
    private val NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("小万正在陪伴中...")
            .setContentText("若您不需要陪伴,可返回APP点击停止陪伴")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // 执行后台任务
        doBackgroundWork()
        if (flags == START_FLAG_RETRY) {
            Log.d("Service", "服务被重启")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "陪伴模式",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun doBackgroundWork() {
        AssistsCore.startTask(TaskParams.CompanionTaskParams {
//                        onMessagePushListener.onTaskFinish()
        })
//

    }
}
