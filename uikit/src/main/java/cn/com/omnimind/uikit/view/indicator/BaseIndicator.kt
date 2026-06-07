package cn.com.omnimind.uikit.view.indicator

import android.content.Context
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

abstract class BaseIndicator(protected val context: Context) {
    protected val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    protected var indicatorView: View? = null

    /**
     * Shows the indicator and suspends the coroutine until the indicator's
     * animation is complete. Handles cancellation automatically.
     */
    suspend fun show() {
        suspendCancellableCoroutine<Unit> { continuation ->
            continuation.invokeOnCancellation {
                dismiss()
            }

            coroutineScope.launch {
                cleanup()
                try {
                    showInternal {
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                } catch (e: WindowManager.BadTokenException) {
                    // Handle the BadTokenException when app is in background
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            }
        }
    }

    fun showWithoutSuspend(onComplete: () -> Unit) {
        coroutineScope.launch {
            cleanup()
            try {
                showInternal(onComplete)
            } catch (e: WindowManager.BadTokenException) {
                // Handle the BadTokenException when app is in background
                cleanup()
                onComplete()
            } catch (e: IllegalStateException) {
                // Handle IllegalStateException (e.g., when Activity is destroyed)
                cleanup()
                onComplete()
            } catch (e: Exception) {
                // Handle any other exceptions
                cleanup()
                onComplete()
            }
        }
    }

    fun dismiss() {
        coroutineScope.launch {
            cleanup()
        }
    }

    internal fun cleanup() {
        indicatorView?.let {
            try {
                if (it.isAttachedToWindow) {
                    windowManager.removeViewImmediate(it)
                }
            } catch (e: Exception) {
                // Ignore exceptions during cleanup
            }
        }
        indicatorView = null
    }

    protected val statusBarHeight: Int by lazy {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }

    /**
     * Subclasses implement this to create the view and run the animation.
     * @param onComplete The callback that *must* be invoked when the indicator is done.
     */
    protected abstract fun showInternal(onComplete: () -> Unit)
}