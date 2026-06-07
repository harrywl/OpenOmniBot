package cn.com.omnimind.uikit.view.indicator

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import cn.com.omnimind.uikit.R

class ClickIndicator(
    context: Context,
    private val x: Float,
    private val y: Float,
) : BaseIndicator(context
) {
    companion object {
        private const val INDICATOR_SIZE_DP = 40
        private const val OVERSHOOT_TENSION = 2.0f
        private const val POP_IN_DURATION_MS = 200L
        private const val PAUSE_DURATION_MS = 100L
        private const val FADE_OUT_DURATION_MS = 200L
    }

    private val popInInterpolator = OvershootInterpolator(OVERSHOOT_TENSION)
    private val fadeOutInterpolator = DecelerateInterpolator()

    /**
     * 检查 Context 是否有效
     * 
     * 注意：对于 TYPE_ACCESSIBILITY_OVERLAY 类型的窗口：
     * - 可以使用 Application Context（推荐），因为它不依赖 Activity token
     * - 如果使用 Activity Context，需要检查 Activity 是否还在运行
     * - Application Context 生命周期更长，更适合系统级窗口
     */
    private fun isContextValid(): Boolean {
        return try {
            // 对于 Activity Context，检查 Activity 是否还在运行
            if (context is Activity) {
                !context.isFinishing && !context.isDestroyed
            } else {
                // 对于 Application Context 或其他 Context，只需要验证 WindowManager 是否可用
                // TYPE_ACCESSIBILITY_OVERLAY 不需要 Activity token，所以 Application Context 也可以
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                windowManager != null
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun showInternal(onFinished: () -> Unit) {
        try {
            // 在添加窗口之前检查 Context 是否有效
            if (!isContextValid()) {
                onFinished()
                return
            }

            val density = context.resources.displayMetrics.density
            val finalIndicatorSizePx = (INDICATOR_SIZE_DP * density).toInt()

            val containerSizePx = (finalIndicatorSizePx * 1.5f).toInt()

            val childImageView =
                ImageView(context).apply {
                    setImageResource(R.drawable.click_indicator_background)
                    layoutParams =
                        FrameLayout.LayoutParams(
                            finalIndicatorSizePx,
                            finalIndicatorSizePx,
                            Gravity.CENTER,
                        )
                }

            val containerView =
                FrameLayout(context).apply {
                    addView(childImageView)
                }
            indicatorView = containerView

            val params =
                WindowManager.LayoutParams(
                    containerSizePx,
                    containerSizePx,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,

                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    this.x = (this@ClickIndicator.x - containerSizePx / 2).toInt()
                    this.y = (this@ClickIndicator.y - containerSizePx / 2).toInt()
                }

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (windowManager == null) {
                onFinished()
                return
            }

            windowManager.addView(containerView, params)

            childImageView.alpha = 0f
            childImageView.scaleX = 0.5f
            childImageView.scaleY = 0.5f

            childImageView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(POP_IN_DURATION_MS)
                .setInterpolator(popInInterpolator)
                .withEndAction {
                    childImageView.animate()
                        .alpha(0f)
                        .setDuration(FADE_OUT_DURATION_MS)
                        .setStartDelay(PAUSE_DURATION_MS)
                        .setInterpolator(fadeOutInterpolator)
                        .withEndAction {
                            cleanup()
                            onFinished()
                        }
                        .start()
                }
                .start()
        } catch (e: WindowManager.BadTokenException) {
            // Handle the BadTokenException when app is in background
            cleanup()
            onFinished()
        } catch (e: IllegalStateException) {
            // Handle IllegalStateException (e.g., when Activity is destroyed)
            cleanup()
            onFinished()
        } catch (e: Exception) {
            // Handle any other exceptions
            cleanup()
            onFinished()
        }
    }
}