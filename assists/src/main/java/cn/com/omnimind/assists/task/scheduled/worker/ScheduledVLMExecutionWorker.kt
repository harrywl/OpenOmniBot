package cn.com.omnimind.assists.task.scheduled.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cn.com.omnimind.assists.AssistsCore
import com.google.gson.Gson
import com.tencent.mmkv.MMKV

class ScheduledVLMExecutionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val json = inputData.getString(ScheduledConstants.TASK_DATA_KEY)
        val data = Gson().fromJson(json, ScheduledVLMOperationTaskParamsData::class.java)
        val taskParams = data.toScheduledVLMOperationTaskParams(id.toString())
        return try {
            MMKV.defaultMMKV().encode("scheduled_task_id","")
            MMKV.defaultMMKV().encode("scheduled_task_jsonData","")
            AssistsCore.startTask(taskParams)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}