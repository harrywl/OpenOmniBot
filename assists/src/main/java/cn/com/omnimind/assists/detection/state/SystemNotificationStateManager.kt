package cn.com.omnimind.assists.detection.state

import android.app.Notification
import android.view.accessibility.AccessibilityEvent
import cn.com.omnimind.baselib.util.OmniLog

/**
 * 系统通知状态管理器
 *
 * 职责：
 * 1. 维护当前是否有一个未消费的系统通知（只保留最新的一个）
 * 2. 如果系统通知3秒内未被消费，则自动过期转为已消费
 * 3. 提供外部接口：更新通知、消费通知、查询通知状态
 *
 * 设计理念：
 * detectTopBannerFromImage 仅在有系统弹窗时识别准确，
 * 因此需要此状态管理器来判断是否应该执行系统通知检测
 */
object SystemNotificationStateManager {
    private const val TAG = "SystemNotificationState"
    private const val EXPIRATION_TIMEOUT_MS = 3000L // 3秒超时自动过期
    private const val WINDOW_STATE_TIMEOUT_MS = 500L // 500毫秒内等待系统UI窗口事件

    // 系统通知状态
    private var hasUnconsumedNotification = false
    private var notificationTimestamp: Long = 0
    private var notificationPackage: String? = null

    // 用于跟踪TYPE_NOTIFICATION_STATE_CHANGED事件，等待对应的TYPE_WINDOW_STATE_CHANGED事件
    private var pendingNotificationTimestamp: Long = 0
    private var pendingNotificationPackage: String? = null

    /**
     * 处理无障碍事件，如果是通知事件则更新状态并记录日志
     * 优化后的逻辑：
     * 1. 收到TYPE_NOTIFICATION_STATE_CHANGED时，先记录为待确认状态
     * 2. 如果500ms内收到来自com.android.systemui的TYPE_WINDOW_STATE_CHANGED事件，
     *    则确认为有效的系统通知
     * 3. 这样可以处理关闭横幅权限后的情况
     * @param event 无障碍事件
     */
    @Synchronized
    fun handleAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val notification = event.parcelableData as? Notification
                val packageName = event.packageName?.toString()
                val text = event.text?.joinToString(" ")

                OmniLog.d(TAG, "TYPE_NOTIFICATION_STATE_CHANGED - Package: $packageName, Text: $text")

                // 提取通知详细信息并打印
                notification?.extras?.let { bundle ->
                    val title = bundle.getString(Notification.EXTRA_TITLE)
                    val content = bundle.getString(Notification.EXTRA_TEXT)
                    val bigText = bundle.getString(Notification.EXTRA_BIG_TEXT)
                    OmniLog.d(TAG, "Notification - Title: $title, Content: $content, BigText: $bigText")
                }

                // 记录为待确认状态，等待TYPE_WINDOW_STATE_CHANGED事件确认
                pendingNotificationTimestamp = System.currentTimeMillis()
                pendingNotificationPackage = packageName
                OmniLog.d(TAG, "Pending notification from package: $packageName, waiting for window state change")
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString()

                // 检查是否有待确认的通知
                if (pendingNotificationTimestamp > 0) {
                    val currentTime = System.currentTimeMillis()
                    val elapsed = currentTime - pendingNotificationTimestamp

                    // 如果在500ms内收到来自com.android.systemui的窗口状态变化事件
                    if (elapsed <= WINDOW_STATE_TIMEOUT_MS && packageName == "com.android.systemui") {
                        // 确认为有效的系统通知
                        hasUnconsumedNotification = true
                        notificationTimestamp = currentTime
                        notificationPackage = pendingNotificationPackage
                        OmniLog.d(TAG, "Confirmed unconsumed notification from package: $pendingNotificationPackage (systemui window detected after ${elapsed}ms)")

                        // 清除待确认状态
                        pendingNotificationTimestamp = 0
                        pendingNotificationPackage = null
                    } else if (elapsed > WINDOW_STATE_TIMEOUT_MS) {
                        // 超时，清除待确认状态
                        OmniLog.d(TAG, "Pending notification timeout after ${elapsed}ms, clearing (packageName=$packageName)")
                        pendingNotificationTimestamp = 0
                        pendingNotificationPackage = null
                    }
                }
            }
        }
    }

    /**
     * 消费系统通知
     */
    @Synchronized
    fun consumeNotification() {
        if (hasUnconsumedNotification) {
            OmniLog.d(TAG, "Notification consumed from package: $notificationPackage")
        }
        hasUnconsumedNotification = false
        notificationTimestamp = 0
        notificationPackage = null
    }

    /**
     * 获取当前是否有未消费的系统通知
     * 会自动检查是否过期，过期则自动消费
     * @return true 表示有有效的未消费通知
     */
    @Synchronized
    fun hasUnconsumedNotification(): Boolean {
        if (!hasUnconsumedNotification) {
            return false
        }

        // 检查是否过期
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - notificationTimestamp

        if (elapsed > EXPIRATION_TIMEOUT_MS) {
            OmniLog.d(TAG, "Notification expired after ${elapsed}ms from package: $notificationPackage")
            consumeNotification()
            return false
        }

        return true
    }

    /**
     * 获取通知来源包名（仅用于调试）
     */
    @Synchronized
    fun getNotificationPackage(): String? {
        return if (hasUnconsumedNotification()) notificationPackage else null
    }

    /**
     * 重置状态（用于特殊场景）
     */
    @Synchronized
    fun reset() {
        hasUnconsumedNotification = false
        notificationTimestamp = 0
        notificationPackage = null
        pendingNotificationTimestamp = 0
        pendingNotificationPackage = null
        OmniLog.d(TAG, "State reset")
    }
}
