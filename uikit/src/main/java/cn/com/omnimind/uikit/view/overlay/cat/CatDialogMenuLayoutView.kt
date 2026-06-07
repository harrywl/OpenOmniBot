package cn.com.omnimind.uikit.view.overlay.cat

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import cn.com.omnimind.uikit.R

import cn.com.omnimind.uikit.api.callback.MenuApi
import cn.com.omnimind.uikit.view.layout.MenuView

/**
 * 小猫的功能view,包含建议弹框，菜单，消息，学习中，执行任务，学习完成，等待用户确认
 */
@SuppressLint("ViewConstructor")
class CatDialogMenuLayoutView @JvmOverloads constructor(
    context: Context,
    var menuApi: MenuApi?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {


    private lateinit var menuView: MenuView //菜单布局
    private lateinit var subView: View//总布局
    private var isAttachedToLeft = false//是否吸附在左侧

    private var OnCloseFinishListener: () -> Unit = {}
    private var onCloseListener: () -> Unit = {
        //这里特殊处理弹框关闭场合
        setViewToCollapsed()
    }//弹框关闭监听器

    init {
        setupView()
    }

    fun setOnCloseFinishListener(listener: () -> Unit) {
        OnCloseFinishListener = listener
    }

    private fun setupView() {
        subView = inflate(context, R.layout.view_cat_menu_layout, this);
        menuView = subView.findViewById(R.id.menuView) //新加的
        menuView.setDraggableListener(menuApi)
        menuView.setOnCloseListener(onCloseListener)
        subView.setOnClickListener {
            setViewToCollapsed()
        }
    }


    fun setViewToCollapsed() {
        menuView.close(isAttachedToLeft) {
            subView.visibility = GONE
            OnCloseFinishListener.invoke()
        }

    }


    // 长按事件显示菜单按钮
    fun showOptionsMenu() {
        subView.visibility = VISIBLE
        menuView.show(isAttachedToLeft)

    }

    // 设置吸附方向
    fun setAttachmentSideView(isLeft: Boolean) {
        isAttachedToLeft = isLeft

    }

}