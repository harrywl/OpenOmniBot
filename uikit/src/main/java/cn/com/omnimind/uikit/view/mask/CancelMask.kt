package cn.com.omnimind.uikit.view.mask

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import cn.com.omnimind.baselib.util.getResColor
import cn.com.omnimind.uikit.R

@SuppressLint("ViewConstructor")
class CancelMask @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    init {
        setOnClickListener {
            clickListener.invoke()
        }
    }

    private var clickListener: () -> Unit = {}

    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(R.color.uikit000000.getResColor())
    }

    fun setCancelListener(clickListener: () -> Unit) {
        this.clickListener = clickListener
    }

}