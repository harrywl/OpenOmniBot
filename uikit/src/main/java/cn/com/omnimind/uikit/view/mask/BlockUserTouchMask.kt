package cn.com.omnimind.uikit.view.mask

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.annotation.RequiresApi
import androidx.core.graphics.toColorInt

class BlockUserTouchMask @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {



    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 40f
        maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL)
    }
    private  var circleX=0
    private  var circleY=0
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

    // 添加红色圆形动画相关属性
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
    }
    private var circleRadius = 0f
    private var circleAlpha = 0
    private val circleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 800
        interpolator = AccelerateInterpolator()
        addUpdateListener {
            //FIXME 这里的半径需要根据颜色渐变调整,目前做了个超大半径,为了最后透明效果好看 -wangxu
            circleRadius = it.animatedValue as Float * height.toFloat()*2
            invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // circleAnimator 完成后启动透明化渐变动画
//                fadeAnimator.start()
            }
        })
    }

    private val fadeAnimator = ValueAnimator.ofInt(255, 0).apply {
        duration = 800 // 渐隐动画持续时间
        addUpdateListener {
            val value = it.animatedValue as Int
            circleAlpha = value
            invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // 动画结束后将circleRadius设为0
//                circleRadius = 0f
                invalidate()
            }
        })
    }

    // 彩虹渐变
    private var rainbowShader: Shader? = null

    // 透明度渐变（从中心透明到边缘不透明）
    private var transparencyShader: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gradient = SweepGradient(
            width.toFloat() / 2, height.toFloat() / 2,
            intArrayOf(
                "#B0F2FF".toColorInt(),
                "#FAFAA3".toColorInt(),
                "#FFB472".toColorInt(),
                "#FB8DFF".toColorInt(),
                "#B0F2FF".toColorInt(),
                "#FB8DFF".toColorInt(),
                "#FFB472".toColorInt(),
                "#FAFAA3".toColorInt(),
                "#B0F2FF".toColorInt()
            ),

            floatArrayOf(0f, 0.13f, 0.257f, 0.37f, 0.505f, 0.634f, 0.744f, 0.87f, 1f)
        ).apply {
            paint.shader = this
        }

        // 创建彩虹色Shader
        rainbowShader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(
                "#B0F2FF".toColorInt(),
                "#FAFAA3".toColorInt(),
                "#FFB472".toColorInt(),
                "#FB8DFF".toColorInt(),
                "#B0F2FF".toColorInt(),
                "#FB8DFF".toColorInt(),
                "#FFB472".toColorInt(),
                "#FAFAA3".toColorInt(),
                "#B0F2FF".toColorInt()
            ),
            null,
            Shader.TileMode.CLAMP
        )

        // 创建透明度渐变Shader（从中心透明到边缘不透明）
        transparencyShader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(
                "#00FFFFFF".toColorInt(),
                "#7700AEFF".toColorInt(),
                "#FFFFFF".toColorInt(),
                "#77FFFFFF".toColorInt()
            ),
            floatArrayOf(0f, 0.4f, 0.8f, 1f),
            Shader.TileMode.CLAMP
        )

        animator.start()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onDraw(canvas: Canvas) {
        // Draw semi-transparent background

        // 绘制红色圆形动画
        if (circleRadius > 0) {
            // 使用彩虹Shader
            circlePaint.shader = rainbowShader
            circlePaint.alpha = circleAlpha // 设置透明度
            canvas.drawCircle(circleX.toFloat(), circleY.toFloat(), circleRadius, circlePaint)

            // 应用透明度渐变蒙层
            val transparentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val matrix = Matrix()
            matrix.setScale(circleRadius, circleRadius)
            matrix.postTranslate(circleX.toFloat(), circleY.toFloat())
            transparencyShader?.setLocalMatrix(matrix)
            transparentPaint.shader = transparencyShader
            transparentPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawCircle(circleX.toFloat(), circleY.toFloat(), circleRadius, transparentPaint)
        }

        // 绘制透明中心区域
        path.reset()
//        val centerWidth = width * 0.6f
//        val centerHeight = height * 0.6f
//        path.addRect(
//            (width - centerWidth) / 2,
//            (height - centerHeight) / 2,
//            (width + centerWidth) / 2,
//            (height + centerHeight) / 2,
//            Path.Direction.CW
//        )
//        canvas.clipOutPath(path)


        canvas.drawColor(Color.argb(if(100-circleAlpha<0) 0 else 100-circleAlpha,0,0,0))
        // 绘制七彩流光边框
        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            30f, 30f, paint
        )

    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()

    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        circleAnimator.cancel()
        super.onDetachedFromWindow()
    }

    // 公共方法用于启动圆形动画
    fun startCircleAnimation(x:Int,y:Int) {
        circleX=x
        circleY=y
        circleAlpha = 255 // 重置透明度

        circleAnimator.start()
        fadeAnimator.start() // circleAnimator结束后启动淡出动画

    }
}