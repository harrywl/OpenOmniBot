package cn.com.omnimind.uikit.view.mask

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.graphics.toColorInt
import cn.com.omnimind.assists.task.learn.data.TouchPoint
import cn.com.omnimind.baselib.util.getResColor
import cn.com.omnimind.uikit.R
import cn.com.omnimind.uikit.view.data.Constant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 记录用户触摸轨迹并可以重放的自定义View
 * 支持滑动轨迹记录、点击轨迹记录和手势动画重放
 */
class TouchRecordMask @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(R.color.uikit44000000.getResColor())
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 25f
        maskFilter = BlurMaskFilter(25f, BlurMaskFilter.Blur.NORMAL)
    }

    private val bgPath = Path()
    private val matrix = Matrix()
    private var gradient: LinearGradient? = null
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3000
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            matrix.setRotate(it.animatedValue as Float * 360, width / 2f, height / 2f)
            gradient?.setLocalMatrix(matrix)
            invalidate()
        }
    }

    // 轨迹记录监听器接口
    interface OnTouchRecordListener {
        fun onRecordStart()
        fun onRecordStop(records: List<TouchPoint>)
        fun onPointRecorded(point: TouchPoint)
    }

    // 绘制相关
    private val paint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val path = Path()
    private val touchRecords = mutableListOf<TouchPoint>()
    private var startTime: Long = 0
    private var isRecording = false
    private var isReplaying = false

    // 回调监听器
    private var recordListener: OnTouchRecordListener? = null

    // 重放相关
    private var replayJob: Job? = null
    private val replayPaint = Paint().apply {
        isAntiAlias = true
        color = Color.RED
        strokeWidth = 12f
        style = Paint.Style.FILL
    }

    init {
        paint.color = "#ffffff".toColorInt()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gradient = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            Constant.Companion.BG_LINEAR_GRADIENT_COLORS,
            null,
            Shader.TileMode.MIRROR
        ).apply {
            bgPaint.shader = this
        }
        animator.start()
    }

    /**
     * 设置轨迹记录监听器
     */
    fun setOnTouchRecordListener(listener: OnTouchRecordListener) {
        this.recordListener = listener
    }

    /**
     * 开始记录触摸轨迹
     */
    fun startRecording() {
        if (isRecording) return

        isRecording = true
        touchRecords.clear()
        path.reset()
        startTime = System.currentTimeMillis()
        recordListener?.onRecordStart()
        invalidate()
    }

    /**
     * 停止记录触摸轨迹
     */
    fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        recordListener?.onRecordStop(touchRecords.toList())
        invalidate()
    }

    /**
     * 重放触摸轨迹动画
     */
    fun replayRecords() {
        if (touchRecords.isEmpty() || isReplaying) return

        isReplaying = true
        replayJob = CoroutineScope(Dispatchers.Main).launch {
            // 清除当前路径
            path.reset()
            invalidate()

            // 按时间顺序重放轨迹点
            touchRecords.forEachIndexed { index, point ->
                if (!isReplaying) return@launch // 如果中途停止则退出

                // 绘制轨迹点
                if (index == 0) {
                    path.moveTo(point.x, point.y)
                } else {
                    path.lineTo(point.x, point.y)
                }

                // 更新绘制
                invalidate()

                // 如果不是最后一个点，等待到下一个点的时间
                if (index < touchRecords.size - 1) {
                    val nextPoint = touchRecords[index + 1]
                    val delayTime = nextPoint.time - point.time
                    delay(delayTime.coerceAtMost(100)) // 限制最大延迟
                }
            }

            isReplaying = false
        }
    }

    /**
     * 停止重放
     */
    fun stopReplay() {
        isReplaying = false
        replayJob?.cancel()
        replayJob = null
    }

    /**
     * 获取记录的轨迹数据
     */
    fun getRecords(): List<TouchPoint> = touchRecords.toList()

    /**
     * 清除所有记录
     */
    fun clearRecords() {
        touchRecords.clear()
        path.reset()
        invalidate()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制记录的轨迹
        canvas.drawPath(path, paint)

        // 如果正在重放，绘制动画点
        if (isReplaying && touchRecords.isNotEmpty()) {
            // 绘制最后一个点作为动画点
            val lastPoint = touchRecords.lastOrNull()
            lastPoint?.let {
                canvas.drawCircle(it.x, it.y, 20f, replayPaint)
            }
        }

        // 绘制透明中心区域
        bgPath.reset()
        val centerWidth = width * 0.6f
        val centerHeight = height * 0.6f
        bgPath.addRect(
            (width - centerWidth) / 2,
            (height - centerHeight) / 2,
            (width + centerWidth) / 2,
            (height + centerHeight) / 2,
            Path.Direction.CW
        )
        canvas.clipOutPath(bgPath)

        // 绘制七彩流光边框
        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()), 30f, 30f, bgPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isRecording) return super.onTouchEvent(event)

        val currentTime = System.currentTimeMillis() - startTime
        val point = TouchPoint(event.x, event.y, currentTime, event.action)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 开始新的路径
                path.moveTo(event.x, event.y)
                touchRecords.add(point)
                recordListener?.onPointRecorded(point)
            }

            MotionEvent.ACTION_MOVE -> {
                // 添加移动轨迹点
                path.lineTo(event.x, event.y)
                touchRecords.add(point)
                recordListener?.onPointRecorded(point)
            }

            MotionEvent.ACTION_UP -> {
                // 添加结束点
                touchRecords.add(point)
                recordListener?.onPointRecorded(point)
            }
        }

        invalidate()
        return true
    }

    override fun onDetachedFromWindow() {
        animator.cancel()

        super.onDetachedFromWindow()
        stopReplay() // 确保在View销毁时停止重放
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }


}