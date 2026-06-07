package cn.com.omnimind.uikit.util.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.com.omnimind.assists.AssistsCore
import cn.com.omnimind.uikit.util.NotificationUtil

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_EXECUTE = "cn.com.omnimind.overlay.ACTION_EXECUTE"
        const val ACTION_CANCEL = "cn.com.omnimind.overlay.ACTION_CANCEL"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_EXECUTE -> {
                // Handle Execute Action
                AssistsCore.doScheduleNow()
                NotificationUtil.dismiss()
            }
            ACTION_CANCEL -> {
                // Handle Cancel Action
                AssistsCore.cancelScheduleTask()
                NotificationUtil.dismiss()
            }
        }
    }
}