package cn.com.omnimind.assists.task.scheduled.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cn.com.omnimind.assists.AssistsCore

class ScheduledExecutionTipWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val key = inputData.getString(ScheduledConstants.TIP_TYPE_KEY)

            when (key) {
                ScheduledConstants.TYPE_TIP -> {
                    AssistsCore.showScheduledTip(ScheduledConstants.TIP_BEFORE_CLOSE_TIME,ScheduledConstants.TIP_BEFORE_TIME)
                    Result.success()

                }

                ScheduledConstants.TYPE_READY_DO_TASK_TIP -> {
                    AssistsCore.showScheduledTip(ScheduledConstants.READY_DO_TASK_TIP_BEFORE_TIME,ScheduledConstants.READY_DO_TASK_TIP_BEFORE_TIME)

                    Result.success()

                }

                else -> {
                    throw IllegalStateException("Invalid tip type")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}