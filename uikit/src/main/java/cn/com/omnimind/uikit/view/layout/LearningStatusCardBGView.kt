package cn.com.omnimind.uikit.view.layout

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

class LearningStatusCardBGView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val rectF = RectF()
    private val path = Path()
    private val matrix = Matrix()
    private var gradient: SweepGradient? = null
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 5000
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            matrix.setRotate(it.animatedValue as Float * 360, width / 2f, height / 2f)
            gradient?.setLocalMatrix(matrix)
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rectF.set(0f, 0f, width.toFloat(), height.toFloat())
        gradient = SweepGradient(
            width.toFloat()/2, height.toFloat()/2,
            intArrayOf(
                "#00FFFFFF".toColorInt(),
                "#B0F2FF".toColorInt(),

                ),

            floatArrayOf(0.405f,1f)
        )
        paint.shader = gradient

        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        val cornerRadius = 32 * resources.displayMetrics.density
        
        // 使用 clipPath 确保圆角效果
        path.reset()
        path.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.clipPath(path)
        
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}