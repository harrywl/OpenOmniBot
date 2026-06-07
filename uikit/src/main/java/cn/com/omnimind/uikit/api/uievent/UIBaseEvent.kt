package cn.com.omnimind.uikit.api.uievent

import android.content.Context

interface UIBaseEvent {
    /**
     * UI初始化
     * @param context Context 上下文
     */
    fun onUIInit(context: Context)
    /**
     * 清除所有弹框(主线程调用)
     */
    fun closeWithOutTaskDoingInMain()

    /**
     * 隐藏小猫(主线程调用)
     */
    fun goneCatInMain()

    /**
     * 隐藏遮罩(主线程调用)
     */
    fun goneLockMaskInMain()

    /**
     * 恢复小猫展示(主线程调用)
     */
    fun visibleCatInMain()
    /**
     * 展示点击时的小蓝圈
     */
    suspend fun showClickIndicator(x: Int, y: Int)

    /**
     * 清除所有弹框
     */
    suspend fun closeWithOutTaskDoing()

    /**
     * 恢复小猫相关的状态
     */
    suspend fun visibleCat()


    /**
     * 执行事件前是否需要移动UI位置
     */
    suspend fun move(startX: Float, startY: Float, endX: Float, endY: Float): Boolean

    /**
     * 展示消息
     */
    suspend fun message(text: String)

    /**
     * 开始陪伴任务展示
     */
    suspend fun startCompanion()

    /**
     * 结束陪伴
     */
    suspend fun finishCompanion()

    /**
     * 执行解锁屏幕
     * @param block suspend () -> T 待执行的代码块
     * @param lockScreenDelay Long 锁屏延迟时间
     * @return T 返回值
     * 该方法一般用于执行解锁屏幕，锁屏延迟时间默认为200毫秒
     * 如果业务层需要阻塞用户操作,在执行无障碍任务前会执行该方法
     */
    suspend fun <T> doAssistsUnlockScreenMask(
        block: suspend () -> T,
        lockScreenDelay: Long = 200
    ): T

    /**
     * 执行锁屏
     */
    suspend fun lockScreenMask()

    /**
     * 取消锁屏
     */
    suspend fun cancelLockScreenMask()


}