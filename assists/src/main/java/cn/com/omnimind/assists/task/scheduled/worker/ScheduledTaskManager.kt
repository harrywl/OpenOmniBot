package cn.com.omnimind.assists.task.scheduled.worker

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import cn.com.omnimind.assists.api.bean.TaskParams
import cn.com.omnimind.assists.api.eventapi.ExecutionTaskEventApi
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

class ScheduledTaskManager(private val context: Context,private val executionTaskEventApi: ExecutionTaskEventApi?) {

    /**
     * 安排一个定时任务，在指定的延迟时间后执行
     *
     * @param scheduledParams 执行参数
     */
   suspend fun scheduleExecutionTask(
        scheduledParams: ScheduledParams
    ): Scheduled {
        //创建提示定时任务
        var tipID = ""
        if (scheduledParams.isShowTip) {
            val workData = Data.Builder()
                .putString(ScheduledConstants.TIP_TYPE_KEY, ScheduledConstants.TYPE_TIP).build()
            val workRequest =
                OneTimeWorkRequestBuilder<ScheduledExecutionTipWorker>().setInputData(workData)
                    .setInitialDelay(
                        scheduledParams.delayTimes - ScheduledConstants.TIP_BEFORE_TIME,
                        TimeUnit.SECONDS
                    ).build()
            WorkManager.getInstance(context).enqueue(workRequest)
            tipID = workRequest.id.toString()
        }
        //创建预备执行提示定时任务
        var readyDoTaskTipID = ""
        if (scheduledParams.isShowTip) {
            val workData = Data.Builder().putString(
                ScheduledConstants.TIP_TYPE_KEY, ScheduledConstants.TYPE_READY_DO_TASK_TIP
            ).build()
            val workRequest =
                OneTimeWorkRequestBuilder<ScheduledExecutionTipWorker>().setInputData(workData)
                    .setInitialDelay(
                        scheduledParams.delayTimes - ScheduledConstants.READY_DO_TASK_TIP_BEFORE_TIME,
                        TimeUnit.SECONDS
                    ).build()
            WorkManager.getInstance(context).enqueue(workRequest)
            readyDoTaskTipID = workRequest.id.toString()
        }
        return when (scheduledParams.taskParams) {
            is TaskParams.ScheduledVLMOperationTaskParams -> {
                //创建定时执行任务
                val workData = Data.Builder().putString(
                    ScheduledConstants.TASK_DATA_KEY,
                    Gson().toJson(scheduledParams.taskParams.toScheduledVLMOperationTaskParamsData())
                ).build()
                val workRequest =
                    OneTimeWorkRequestBuilder<ScheduledVLMExecutionWorker>()
                        .setInputData(workData)
                        .setInitialDelay(
                            scheduledParams.delayTimes, TimeUnit.SECONDS
                        ).build()
                val operation = WorkManager.getInstance(context).enqueue(workRequest)
                withContext(Dispatchers.Main){
                    executionTaskEventApi?.showTaskNotification(
                        context,
                        scheduledParams.delayTimes,
                        scheduledParams.taskParams.name,
                        scheduledParams.taskParams.subTitle ?: "",
                    )
                }
                val jsonData=Gson().toJson(scheduledParams.toScheduledParamsJson())
                MMKV.defaultMMKV().encode("scheduled_task_id", workRequest.id.toString())
                MMKV.defaultMMKV().encode("scheduled_task_jsonData",jsonData)
                Scheduled(workRequest.id.toString(), tipID, readyDoTaskTipID, operation)

            }

            else -> {
                throw IllegalArgumentException("Only ScheduledVLMOperationTaskParams is supported")
            }
        }

    }

    /**
     * 取消指定的任务
     *
     * @param workId 工作ID
     */
    fun cancelScheduledTask(workId: String) {
        WorkManager.getInstance(context).cancelWorkById(UUID.fromString(workId))
    }

    /**
     * 取消所有已安排的任务
     */
    fun cancelAllScheduledTasks() {
        WorkManager.getInstance(context).cancelAllWork()
    }

}
