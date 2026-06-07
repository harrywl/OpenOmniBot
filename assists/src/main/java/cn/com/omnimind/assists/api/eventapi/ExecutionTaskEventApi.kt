package cn.com.omnimind.assists.api.eventapi

import android.content.Context
import cn.com.omnimind.assists.api.enums.TaskFinishType
import cn.com.omnimind.assists.task.vlmserver.VLMOperationTask
import cn.com.omnimind.omniintelligence.models.ScrollDirection

interface ExecutionTaskEventApi {
    var taskType: ExecutingTaskType
    var vlmTask: VLMOperationTask?
    /**
     * 准备执行vlm任务
     * @param task vlmTask实体
     */
    suspend fun onReadyStartVLMTask(task: VLMOperationTask)

    /**
     * 开始VLM任务
     */
    suspend fun onStartVLMTask(isCompanionRunning: Boolean)

    /**
     * vlm任务暂停
     */
    suspend fun onVlmTaskPaused(vmlTask: VLMOperationTask)

    /**
     * VLM任务结束
     */
    suspend fun onVLMTaskStop(
        finishType: TaskFinishType, message: String, isCompanionRunning: Boolean
    )

    /**
     * 准备打开三方应用
     */
    suspend fun readyOpenThirdAPP(packageName: String)


    /**
     * 点击坐标
     */
    suspend fun clickCoordinate(x: Float, y: Float, clickCoordinateFun: suspend () -> Unit): Unit

    /**
     * 非锁屏点击坐标
     */
    suspend fun clickCoordinateWithOutLock(
        x: Float, y: Float, clickCoordinateFun: suspend () -> Unit
    ): Unit

    /**
     * 返回
     */
    suspend fun goBack(goBackFun: suspend () -> Unit): Unit

    /**
     * 滑动坐标
     */
    suspend fun scrollCoordinate(
        x: Float,
        y: Float,
        direction: ScrollDirection,
        distance: Int,
        scrollCoordinateFun: suspend () -> Unit
    ): Unit

    /**
     * 长按坐标
     */
    suspend fun longClickCoordinate(
        x: Float, y: Float, longClickCoordinateFun: suspend () -> Unit
    ): Unit


    /**
     * 返回主页
     */
    suspend fun goHome(goHomeFun: suspend () -> Unit): Unit

    /**
     * 输入文本
     */
    suspend fun inputText(inputTextFun: suspend () -> Unit): Unit

    /**
     * 粘贴文本
     */
    suspend fun pasteText(pasteTextFun: suspend () -> Unit): Unit

    /**
     * 展示vlm结果
     */
    suspend fun showChatWithSummary()

    /**
     * 需要用户接管
     * @return 是否继续执行
     */
    suspend fun userTakeover(message: String): Boolean

    /**
     * 更新展示步骤文本
     */
    suspend fun updateShowStepText(message: String)

    /**
     * 关闭预约通知
     */
    suspend fun dismissScheduledNotification()
    /**
     * 开始使用提示信息
     */
    suspend fun startFirstUseMessage(string: String)

    /**
     * 展示预约任务提示
     */
    suspend fun showScheduledTip(closeTime: Long, doTaskTime: Long)

    /**
     * 展示预约任务通知
     */
    fun showTaskNotification(context: Context, delayTimes: Long, name: String, string2: String) {}

}

enum class ExecutingTaskType {
    VLM,
    EMPTY
}
