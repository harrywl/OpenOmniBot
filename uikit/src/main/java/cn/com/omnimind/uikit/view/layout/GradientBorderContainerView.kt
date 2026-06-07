package cn.com.omnimind.uikit.view.layout

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.graphics.toColorInt
import cn.com.omnimind.baselib.util.dpToFloatPx

/**
 * 渐变边框容器视图
 * 白色背景，8px圆角，旋转渐变边框，阴影
 * 支持4种边框颜色变体：
 * - BLUE: #04b3ff (2464:5094, 2464:5193, 2464:5259)
 * - PURPLE: #4304ff (2464:5157)
 */
class GradientBorderContainerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class BorderColor {
        BLUE,      // #04b3ff
        PURPLE     // #4304ff
    }

    private var borderColorType: BorderColor = BorderColor.BLUE
    
    // 动态圆角半径：0.0 = 矩形，1.0 = 完全圆形
    var cornerRadiusProgress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.dpToFloatPx()
    }
    private val rectF = RectF()
    private val path = Path()
    private val matrix = Matrix()
    private var gradient: SweepGradient? = null
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3000 // 3秒完成一圈
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            matrix.setRotate(it.animatedValue as Float * 360, width / 2f, height / 2f)
            gradient?.setLocalMatrix(matrix)
            invalidate()
        }
    }

    init {
        setWillNotDraw(false)
    }

    /**
     * 设置边框颜色类型
     */
    fun setBorderColorType(type: BorderColor) {
        if (borderColorType != type) {
            borderColorType = type
            updateGradient()
        }
    }

    private fun updateGradient() {
        if (width > 0 && height > 0) {
            val centerX = width / 2f
            val centerY = height / 2f

            val colors = when (borderColorType) {
                BorderColor.BLUE -> intArrayOf(
                    Color.TRANSPARENT,
                    "#04b3ff".toColorInt(),
                    "#b794f6".toColorInt(),
                    "#04b3ff".toColorInt(),
                    Color.TRANSPARENT
                )

                BorderColor.PURPLE -> intArrayOf(
                    Color.TRANSPARENT,
                    "#4304ff".toColorInt(),
                    "#b794f6".toColorInt(),
                    "#4304ff".toColorInt(),
                    Color.TRANSPARENT
                )
            }

            val positions = floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f)

            gradient = SweepGradient(centerX, centerY, colors, positions).apply {
                setLocalMatrix(matrix)
            }
            borderPaint.shader = gradient
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rectF.set(0f, 0f, width.toFloat(), height.toFloat())
        updateGradient()

        if (!animator.isRunning) {
            animator.start()
        }
    }
    
    /**
     * 计算当前圆角半径
     */
    private fun getCurrentCornerRadius(): Float {
        val baseRadius = 8.dpToFloatPx()
        if (cornerRadiusProgress <= 0f) {
            return baseRadius
        }
        // 从矩形圆角过渡到完全圆形
        val minDimension = minOf(width, height)
        val targetRadius = minDimension / 2f
        return baseRadius + (targetRadius - baseRadius) * cornerRadiusProgress
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cornerRadius = getCurrentCornerRadius()

        // 绘制阴影
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(38, 0, 0, 0) // rgba(0,0,0,0.15)
            maskFilter = BlurMaskFilter(4.dpToFloatPx(), BlurMaskFilter.Blur.NORMAL)
        }
        val shadowRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, shadowPaint)

        // 绘制白色背景
        paint.color = Color.WHITE
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)

        // 绘制旋转渐变边框
        // 创建边框路径（在圆角矩形边缘）
        val borderRect = RectF(
            borderPaint.strokeWidth / 2,
            borderPaint.strokeWidth / 2,
            width - borderPaint.strokeWidth / 2,
            height - borderPaint.strokeWidth / 2
        )
        val borderPath = Path().apply {
            addRoundRect(borderRect, cornerRadius, cornerRadius, Path.Direction.CW)
        }

        // 绘制旋转渐变边框
        canvas.drawPath(borderPath, borderPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isRunning) {
            animator.start()
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}