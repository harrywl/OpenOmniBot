package cn.com.omnimind.uikit.view.layout

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

open class BaseFrameLayout(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var onCloseListener: () -> Unit? = {}
    var isLeft = false
    open var durations=300L;

    fun setOnCloseListener(listener: () -> Unit) {
        onCloseListener = listener
    }


    open fun show(isLeft: Boolean) {
        this.isLeft = isLeft;
        visibility = VISIBLE
    }

    open fun close(isLeft: Boolean,closeFinishListener: () -> Unit) {
        closeFinishListener.invoke()
        visibility=GONE
    }

    open fun doClose(){
        onCloseListener.invoke()

    }
    fun Int.dpToPxF(): Float {
        return this * resources.displayMetrics.density
    }

    fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

}