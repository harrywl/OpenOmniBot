package cn.com.omnimind.assists.task

import cn.com.omnimind.assists.TaskManager
import cn.com.omnimind.assists.api.enums.TaskFinishType
import cn.com.omnimind.assists.api.enums.TaskType
import cn.com.omnimind.assists.api.eventapi.AssistsEventApi
import cn.com.omnimind.assists.api.interfaces.TaskChangeListener
import cn.com.omnimind.assists.task.scheduled.worker.ScheduledStates

class TaskChangeImpl(val assistsEventApi: AssistsEventApi?) : TaskChangeListener {

    override suspend fun onTaskStart(
        taskType: TaskType, taskManager: TaskManager
    ) {
        when (taskType) {
            TaskType.COMPANION -> {
                assistsEventApi?.getCompanionEventImpl()?.startTask(false)
            }

            TaskType.CHAT -> {}

            TaskType.VLM_OPERATION_EXECUTION -> {
                assistsEventApi?.getExecutionEventImpl()
                    ?.onStartVLMTask(taskManager.isCompanionRunning())
            }

            TaskType.SCHEDULED -> {}

            TaskType.SCHEDULED_VLM_OPERATION_EXECUTION -> {
                taskManager.changeScheduledStates(ScheduledStates.RUNNING)
                assistsEventApi?.getExecutionEventImpl()?.dismissScheduledNotification()
                assistsEventApi?.getExecutionEventImpl()
                    ?.onStartVLMTask(taskManager.isCompanionRunning())
            }
        }
    }

    override suspend fun onTaskStop(
        taskType: TaskType, finishType: TaskFinishType, message: String, taskManager: TaskManager
    ) {
        when (taskType) {
            TaskType.COMPANION -> {
                assistsEventApi?.getCompanionEventImpl()?.onTaskStop(finishType, message, true)
            }

            TaskType.CHAT -> {}

            TaskType.VLM_OPERATION_EXECUTION -> {
                val isCompanionRunning = taskManager.isCompanionRunning()
                if (isCompanionRunning) {
                    taskManager.resumeCompanionTask()
                }
                assistsEventApi?.getExecutionEventImpl()
                    ?.onVLMTaskStop(finishType, message, isCompanionRunning)
            }

            TaskType.SCHEDULED -> {}

            TaskType.SCHEDULED_VLM_OPERATION_EXECUTION -> {
                val states = when (finishType) {
                    TaskFinishType.CANCEL -> ScheduledStates.CANCELED
                    TaskFinishType.ERROR -> ScheduledStates.FAILED
                    TaskFinishType.FINISH -> ScheduledStates.FINISHED
                    TaskFinishType.WAITING_INPUT -> ScheduledStates.RUNNING
                    TaskFinishType.USER_PAUSED -> ScheduledStates.RUNNING
                }
                taskManager.changeScheduledStates(states)
                if (taskManager.isCompanionRunning()) {
                    taskManager.resumeCompanionTask()
                }
                assistsEventApi?.getExecutionEventImpl()
                    ?.onVLMTaskStop(finishType, message, taskManager.isCompanionRunning())
            }
        }
    }
}
