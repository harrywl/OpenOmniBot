package cn.com.omnimind.uikit.view.data

import cn.com.omnimind.baselib.util.DisplayUtil
import cn.com.omnimind.baselib.util.dpToPx
import cn.com.omnimind.uikit.view.overlay.cat.CatView


/**
 * CatDialogShowInfoView 的状态数据类
 * 封装所有状态相关的数据，包括位置、尺寸、动画等
 */
 object CatDialogStateData{
    // ========== 视图状态 ==========
    /**
     * 当前视图状态
     */
    var viewState: CatDialogViewState = CatDialogViewState.EMPTY

    var isLeft: Boolean = false

    var catX: Int = 0
    var catY: Int = 0
    /**
     * 消息形态动画前置位置和尺寸
     */
    fun getMessageInfoStartWH(): Pair<Int, Int> {
        return Pair(16.dpToPx(), 16.dpToPx())
    }

    fun getMessageInfoWH(): Pair<Int, Int> {
        return Pair(DisplayUtil.getScreenWidth() - 60.dpToPx(), 38.dpToPx())
    }
    fun getMessageInfoXY(): Pair<Int, Int> {
        return Pair(30.dpToPx(), DisplayUtil.getScreenHeight() - 92.dpToPx())
    }
    fun getDoingTaskXY(): Pair<Int, Int> {
        return Pair(30.dpToPx(), DisplayUtil.getScreenHeight() - 130.dpToPx())
    }

    /**
     * 任务形态动画位置和尺寸
     */
    fun getTaskDoingWH(): Pair<Int, Int> {
        return Pair(DisplayUtil.getScreenWidth() - 60.dpToPx(), 76.dpToPx())
    }
    fun getUserInfoWH(): Pair<Int, Int> {
        return Pair(150.dpToPx(), 38.dpToPx())
    }

    fun getUserInfoXY(): Pair<Int, Int> {
        if (isLeft) {
            return Pair(CatView.width.dpToPx()-10.dpToPx(), catY+10.dpToPx())
        }
        return Pair(DisplayUtil.getScreenWidth()-CatView.width.dpToPx() - 140.dpToPx(), catY+10.dpToPx())
    }


}

