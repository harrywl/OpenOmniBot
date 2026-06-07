package cn.com.omnimind.uikit.view.layout

import android.R
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import cn.com.omnimind.baselib.util.dpToFloatPx

/**
 * 渐变文字视图 - 流光效果
 * 统一的循环渐变：蓝色 → 紫色 → 粉色 → 蓝色
 */
@SuppressLint("ResourceType")
class GradientTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var gradient: LinearGradient? = null
    private val matrix = Matrix()
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14.dpToFloatPx()
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        isAntiAlias = true
        isDither = true
    }
    private var displayText: String = ""
    private var ellipsizedText: String = "" // 截断后的文字（包含省略号）
    private var gradientPeriodWidth: Float = 0f // 一个渐变周期的宽度
    private var maxWidth: Int = 0 // 最大宽度限制（0表示无限制）
    private var ellipsize: TextUtils.TruncateAt = TextUtils.TruncateAt.END // 省略号位置
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3000 // 3秒完成一次循环，更流畅
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            if (width > 0 && height > 0 && gradient != null && gradientPeriodWidth > 0) {
                // 平移渐变，实现流光效果
                // 一个动画周期对应一个渐变周期的宽度，实现无缝循环
                val translateX = it.animatedValue as Float * gradientPeriodWidth
                matrix.reset()
                matrix.setTranslate(translateX, 0f)
                gradient?.setLocalMatrix(matrix)
                invalidate()
            }
        }
    }

    init {
        setWillNotDraw(false)
        // 确保视图可见
        visibility = VISIBLE
        // 从 XML 属性中读取配置
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, intArrayOf(
                R.attr.maxWidth,
                R.attr.ellipsize
            ))
            maxWidth = typedArray.getDimensionPixelSize(0, 0)
            val ellipsizeValue = typedArray.getInt(1, 3) // 默认 END
            ellipsize = when (ellipsizeValue) {
                1 -> TextUtils.TruncateAt.START
                2 -> TextUtils.TruncateAt.MIDDLE
                3 -> TextUtils.TruncateAt.END
                else -> TextUtils.TruncateAt.END
            }
            typedArray.recycle()
        }
    }

    fun getText(): String = displayText

    fun setText(text: String) {
        val changed = displayText != text
        displayText = text
        ellipsizedText = text // 初始化为原始文字，后续会根据宽度截断

        if (changed) {
            // 确保视图可见
            visibility = VISIBLE
            // 文字改变后需要重新测量视图尺寸
            requestLayout()
            // 强制更新渐变
            updateGradient()
            invalidate()
        }
    }
    
    /**
     * 设置最大宽度限制
     */
    fun setMaxWidth(maxWidth: Int) {
        if (this.maxWidth != maxWidth) {
            this.maxWidth = maxWidth
            requestLayout()
            invalidate()
        }
    }
    
    /**
     * 设置省略号位置
     */
    fun setEllipsize(ellipsize: TextUtils.TruncateAt) {
        if (this.ellipsize != ellipsize) {
            this.ellipsize = ellipsize
            requestLayout()
            invalidate()
        }
    }

    private fun updateGradient() {
        // 使用实际绘制的文字宽度（可能是截断后的）或视图宽度来初始化渐变
        val textWidth = if (ellipsizedText.isNotEmpty()) {
            textPaint.measureText(ellipsizedText)
        } else if (displayText.isNotEmpty()) {
            textPaint.measureText(displayText)
        } else {
            0f
        }
        val viewWidth = if (width > 0) width.toFloat() else textWidth
        
        if (viewWidth > 0 || textWidth > 0) {
            val baseWidth = if (viewWidth > 0) viewWidth else textWidth
            // 一个渐变周期的宽度，使用文字宽度的2倍，确保有足够的渐变空间
            gradientPeriodWidth = baseWidth * 2f
            
            // 渐变的起始和结束位置
            // 从 -gradientPeriodWidth 开始，这样动画开始时不会显示纯蓝色
            val startX = -gradientPeriodWidth
            val endX = 0f

            // 创建一个完整的渐变周期：蓝色 → 紫色 → 粉色 → 蓝色
            // 首尾都是蓝色，使用 REPEAT 模式实现无缝循环
            val colors = intArrayOf(
                "#00b7ff".toColorInt(),  // 蓝色 (起点)
                "#7135ff".toColorInt(),  // 紫色
                "#fd3f99".toColorInt(),  // 粉色
                "#00b7ff".toColorInt()   // 蓝色 (终点，与起点相同，形成无缝循环)
            )

            // 位置数组，确保首尾都是蓝色
            val positions = floatArrayOf(0f, 0.33f, 0.66f, 1f)

            gradient = LinearGradient(
                startX, 0f, endX, 0f,
                colors,
                positions,
                Shader.TileMode.REPEAT  // 使用 REPEAT 模式实现无缝循环
            ).apply {
                // 初始位置设置为渐变周期的中间位置，避免开始时显示纯蓝色
                val initialTranslateX = -gradientPeriodWidth * 0.5f
                matrix.reset()
                matrix.setTranslate(initialTranslateX, 0f)
                setLocalMatrix(matrix)
            }
            textPaint.shader = gradient
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGradient()

        if (!animator.isRunning) {
            animator.start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (displayText.isNotEmpty()) {
            // 获取可用宽度（考虑 padding 和 maxWidth）
            val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
            val maxAvailableWidth = if (maxWidth > 0) {
                minOf(availableWidth, maxWidth) - paddingLeft - paddingRight
            } else {
                availableWidth - paddingLeft - paddingRight
            }
            
            // 计算原始文字宽度
            val originalTextWidth = textPaint.measureText(displayText)
            
            // 如果文字宽度超出可用宽度，需要截断
            if (maxAvailableWidth > 0 && originalTextWidth > maxAvailableWidth) {
                // 使用 TextUtils.ellipsize 截断文字
                ellipsizedText = TextUtils.ellipsize(
                    displayText,
                    textPaint,
                    maxAvailableWidth.toFloat(),
                    ellipsize
                ).toString()
            } else {
                // 文字可以完全显示
                ellipsizedText = displayText
            }
            
            // 使用截断后的文字宽度进行测量
            val textWidth = textPaint.measureText(ellipsizedText)
            val textHeight = textPaint.descent() - textPaint.ascent()
            val measuredWidth = resolveSize(
                (textWidth + paddingLeft + paddingRight).toInt(),
                widthMeasureSpec
            )
            val measuredHeight = resolveSize(
                (textHeight + paddingTop + paddingBottom).toInt(),
                heightMeasureSpec
            )
            setMeasuredDimension(measuredWidth, measuredHeight)
        } else {
            // 即使文字为空，也要设置一个最小尺寸，避免视图不可见
            val minHeight = textPaint.descent() - textPaint.ascent()
            val minWidth = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(
                resolveSize(minWidth, widthMeasureSpec),
                resolveSize((minHeight + paddingTop + paddingBottom).toInt(), heightMeasureSpec)
            )
            ellipsizedText = ""
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 使用截断后的文字进行绘制
        val textToDraw = if (ellipsizedText.isNotEmpty()) ellipsizedText else displayText
        if (textToDraw.isEmpty()) {
            return
        }
        
        // 确保渐变已初始化
        if (gradient == null) {
            updateGradient()
        }
        
        // 绘制文字 - 无论渐变是否初始化都要绘制
        if (gradient != null) {
            textPaint.shader = gradient
        } else {
            // 如果渐变还未初始化，使用默认颜色绘制（确保文字可见）
            textPaint.shader = null
            textPaint.color = "#00b7ff".toColorInt()
        }
        
        // 计算文字位置（左对齐，考虑padding）
        val x = paddingLeft.toFloat()
        val y = if (height > 0) {
            height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        } else {
            // 如果高度为0，使用文字高度计算
            -textPaint.ascent() + paddingTop
        }
        
        // 绘制截断后的文字（包含省略号）
        canvas.drawText(textToDraw, x, y, textPaint)
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