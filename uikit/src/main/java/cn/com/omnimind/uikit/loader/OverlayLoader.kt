package cn.com.omnimind.uikit.loader

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.WindowManager
import cn.com.omnimind.baselib.util.OmniLog

abstract class OverlayLoader<T : View>(
    protected open val context: Context, open val view: T
) {
    private val TAG: String = "[OverlayLoader]"
    private lateinit var windowManager: WindowManager;
    var isAttachedToWindow: Boolean = false;

    @SuppressLint("SuspiciousIndentation")
    fun getWindowManager(): WindowManager {
        if (!this::windowManager.isInitialized) windowManager =
            context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return windowManager
    }

    abstract fun getParams(flagsValue: Int): WindowManager.LayoutParams
    public open fun load(flag: Int): Boolean {
        try {
            //判断overlay是否已经创建 若未创建重新创建
            val params = getParams(flag);
            if (isAttachedToWindow) {
                getWindowManager().updateViewLayout(view, params)
            } else {
                // 直接尝试添加窗口，如果 token 无效会抛出 BadTokenException
                try {
                    getWindowManager().addView(view, params)
                    isAttachedToWindow = true
                } catch (e: WindowManager.BadTokenException) {
                    OmniLog.e(TAG, "BadTokenException: Unable to add window, context may be invalid: ${e.message}")
                    // 重置状态，避免后续重试时出现问题
                    isAttachedToWindow = false
                    return false
                }
            }
            return true
        } catch (e: Exception) {
            OmniLog.e(TAG, "loadScreenMask: ${e.message}", e)
            // 如果是 BadTokenException，重置状态
            if (e is WindowManager.BadTokenException) {
                isAttachedToWindow = false
            }
            return false
        }
    }

    // 销毁资源
    open fun destroy() {
        try {
            // 只有在 view 已经附加到窗口时才尝试移除
            if (isAttachedToWindow) {
                // 双重检查：确保 view 确实附加到窗口
                if (view.windowToken != null) {
                    getWindowManager().removeView(view)
                }
                isAttachedToWindow = false
            }
        } catch (e: Exception) {
            OmniLog.e(TAG, "destroy: ${e.message}")
            // 无论是否成功，都重置状态
            isAttachedToWindow = false
        }
    }
}