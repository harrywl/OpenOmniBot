package cn.com.omnimind.uikit.view.layout

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.annotation.RequiresApi

class HalfScreenView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo?) {
        super.onInitializeAccessibilityNodeInfo(info)
//        addExtraDataToAccessibilityNodeInfo( info!!, "HalfScreenView", Bundle())
        if (info != null) {
            val keys = ArrayList<String>()
            info.availableExtraData?.forEach {
                keys.add(it)
            }
            keys.add("HalfScreenView")
            info.availableExtraData=keys
        }
    }
}