package cn.com.omnimind.accessibility.action

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import cn.com.omnimind.accessibility.util.ScreenStateUtil
import cn.com.omnimind.baselib.util.OmniLog

class ScreenStateReceiver(private val onScreenStateChanged: ScreenStateListener) {

    // 记录上次检查时间，避免频繁检查
    private var lastWindowCheckTime = 0L

    // DisplayManager 相关
    private var displayManager: DisplayManager? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var context: Context? = null
    private val handler = Handler(Looper.getMainLooper())

    // 状态跟踪，避免重复通知
    private var lastScreenState: Boolean? = null
    private var lastLockState: Boolean? = null

    // 用于延迟检查锁屏状态的 Runnable
    private var checkLockStateRunnable: Runnable? = null

    // 是否使用 DisplayManager（优先使用）
    private var useDisplayManager = false


    /**
     * 检查锁屏状态并通知
     */
    private fun checkLockState(context: Context) {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        // 检查屏幕是否亮屏且可交互
        val isInteractive = powerManager.isInteractive
        val isLocked = keyguardManager.isDeviceLocked
        // 只在状态变化时通知
        if (lastLockState != isLocked) {
            lastLockState = isLocked
            if (isLocked) {
                // 设备锁屏
                onScreenStateChanged.onLocked()
            } else {
                // 设备已解锁
                onScreenStateChanged.onUnlocked()
            }
        }
    }

    /**
     * 检查屏幕状态（用于 DisplayManager）
     */
    private fun checkScreenState(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isInteractive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            powerManager.isInteractive
        } else {
            @Suppress("DEPRECATION")
            powerManager.isScreenOn
        }

        // 检查屏幕状态是否变化
        if (lastScreenState != isInteractive) {
            lastScreenState = isInteractive

            if (isInteractive) {
                // 屏幕打开
                OmniLog.d(TAG, "Screen turned ON (via DisplayManager)")
                // 延迟检查锁屏状态（因为状态可能还未更新）
                handler.removeCallbacks(checkLockStateRunnable ?: Runnable {})
                checkLockStateRunnable = Runnable {
                    checkLockState(context)
                    checkAndNotifyOperableState()
                }
                handler.postDelayed(checkLockStateRunnable!!, 300)
            } else {
                // 屏幕关闭
                OmniLog.d(TAG, "Screen turned OFF (via DisplayManager)")
                handler.removeCallbacks(checkLockStateRunnable ?: Runnable {})
                onScreenStateChanged.onLocked()
                checkAndNotifyOperableState()
            }
        } else if (isInteractive) {
            // 屏幕已打开，但状态未变化，可能是其他原因触发的（如旋转）
            // 仍然检查锁屏状态
            handler.removeCallbacks(checkLockStateRunnable ?: Runnable {})
            checkLockStateRunnable = Runnable {
                checkLockState(context)
                checkAndNotifyOperableState()
            }
            handler.postDelayed(checkLockStateRunnable!!, 300)
        }
    }

    /**
     * 检查可操作状态并通知
     */
    private fun checkAndNotifyOperableState() {
        val isOperable = ScreenStateUtil.isOperable()
        onScreenStateChanged.onOperableStateChanged(isOperable)
    }

    companion object {
        private const val TAG = "ScreenStateReceiver"
        private const val WINDOW_CHECK_INTERVAL = 500L // 500ms间隔检查

        fun register(context: Context, receiver: ScreenStateReceiver) {
            receiver.context = context

            // 优先使用 DisplayManager.DisplayListener（API 17+）
            // 在魅族等定制系统上更可靠
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                try {
                    val displayManager =
                        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                    receiver.displayManager = displayManager

                    receiver.displayListener = object : DisplayManager.DisplayListener {
                        override fun onDisplayAdded(displayId: Int) {
                            // 显示器添加（通常不需要处理）
                        }

                        override fun onDisplayRemoved(displayId: Int) {
                            // 显示器移除（通常不需要处理）
                        }

                        override fun onDisplayChanged(displayId: Int) {
                            // 显示状态改变（包括屏幕开关、旋转等）
                            receiver.context?.let { ctx ->
                                receiver.checkScreenState(ctx)
                            }
                        }
                    }

                    displayManager.registerDisplayListener(
                        receiver.displayListener,
                        Handler(Looper.getMainLooper())
                    )

                    receiver.useDisplayManager = true
                    OmniLog.d(
                        TAG,
                        "Registered DisplayManager.DisplayListener for screen state monitoring"
                    )

                    // 立即检查一次当前状态
                    receiver.checkScreenState(context)
                } catch (e: Exception) {
                    OmniLog.e(TAG, "Failed to register DisplayManager listener: ${e.message}", e)
                    receiver.useDisplayManager = false
                }
            } else {
                receiver.useDisplayManager = false
                OmniLog.d(
                    TAG,
                    "DisplayManager not available (API < 17), using broadcast receiver only"
                )
            }

        }

        fun unregister(context: Context, receiver: ScreenStateReceiver) {
            // 注销 DisplayListener
            receiver.displayManager?.let { dm ->
                receiver.displayListener?.let { listener ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        try {
                            dm.unregisterDisplayListener(listener)
                            OmniLog.d(TAG, "Unregistered DisplayManager.DisplayListener")
                        } catch (e: Exception) {
                            OmniLog.e(
                                TAG,
                                "Failed to unregister DisplayManager listener: ${e.message}",
                                e
                            )
                        }
                    }
                }
            }

            // 清理 Handler 回调
            receiver.handler.removeCallbacks(receiver.checkLockStateRunnable ?: Runnable {})
            receiver.displayManager = null
            receiver.displayListener = null
            receiver.context = null

        }


    }
}

/**
 * 屏幕状态监听器接口
 */
interface ScreenStateListener {

    /**
     * 设备锁屏时调用（屏幕亮屏但未解锁）
     */
    fun onLocked()

    /**
     * 设备解锁时调用
     */
    fun onUnlocked()

    /**
     * 可操作状态变化时调用
     * @param isOperable true 表示可操作状态（屏幕亮屏且已解锁），false 表示非可操作状态（熄屏、锁屏、屏保）
     */
    fun onOperableStateChanged(isOperable: Boolean) {
        // 默认空实现，子类可选择实现
    }

}