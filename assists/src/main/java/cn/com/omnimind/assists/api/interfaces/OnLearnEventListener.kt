package cn.com.omnimind.assists.api.interfaces

import cn.com.omnimind.assists.task.learn.data.TouchPoint


interface OnLearnEventListener {
    fun onLearnClick(point: TouchPoint)
    fun onLearnLongPress(point: TouchPoint, duration: Long)
    fun onLearnScroll(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        distance: Double,
        direction: String
    )
}