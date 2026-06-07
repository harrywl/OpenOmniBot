package cn.com.omnimind.accessibility.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import cn.com.omnimind.accessibility.action.AccessibilityNode
import kotlin.math.max
import kotlin.math.min

object ImageScreenshotUtils {
    fun drawMarkingsOnBitmap(
        bitmap: Bitmap,
        nodeMap: Map<String, AccessibilityNode>,
    ): Bitmap {
        val markedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(markedBitmap)

        val rectPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 5f
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.BLUE
            textSize = 40f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        val bgPaint = Paint().apply {
            color = Color.argb(128, 255, 255, 255)
            style = Paint.Style.FILL
        }

        val interactiveNodes = nodeMap.filter { it.value.interactive }.map { (id, node) ->
            Triple(id, node, node.bounds.width() * node.bounds.height())
        }

        val filteredNodes = filterNodes(interactiveNodes)

        for ((id, node) in filteredNodes) {
            val bounds = node.bounds

            canvas.drawRect(bounds, rectPaint)
            val idText = "ID: $id"
            val textWidth = textPaint.measureText(idText)
            val textX = bounds.left + 10f
            val textY = bounds.top + textPaint.textSize

            val bgRect = RectF(
                textX - 5f,
                bounds.top.toFloat(),
                textX + textWidth + 5f,
                bounds.top + textPaint.textSize + 15f,
            )
            canvas.drawRoundRect(bgRect, 5f, 5f, bgPaint)
            canvas.drawText(idText, textX, textY, textPaint)
        }

        return markedBitmap
    }

    private fun filterNodes(nodes: List<Triple<String, AccessibilityNode, Int>>): Map<String, AccessibilityNode> {
        val filteredMap = mutableMapOf<String, AccessibilityNode>()
        val sortedNodes = nodes.sortedBy { it.third }

        fun calculateIoU(
            rect1: Rect,
            rect2: Rect,
        ): Float {
            val intersection = Rect().apply {
                left = max(rect1.left, rect2.left)
                top = max(rect1.top, rect2.top)
                right = min(rect1.right, rect2.right)
                bottom = min(rect1.bottom, rect2.bottom)
            }

            if (!intersection.isEmpty) {
                val intersectionArea = intersection.width() * intersection.height()
                val area1 = rect1.width() * rect1.height()
                val area2 = rect2.width() * rect2.height()

                return intersectionArea / (area1 + area2 - intersectionArea).toFloat()
            }
            return 0f
        }

        fun contains(
            rect1: Rect,
            rect2: Rect,
        ): Boolean {
            if (rect1.left <= rect2.left && rect1.top <= rect2.top && rect1.right >= rect2.right && rect1.bottom >= rect2.bottom) {
                return true
            } else {
                return false
            }
        }

        val iouThreshold = 0.7f
        for ((id1, node1, area1) in sortedNodes) {
            var shouldAdd = true
            for ((id2, node2) in filteredMap) {
                val aContainsB = contains(node1.bounds, node2.bounds)
                if (aContainsB) {
                    shouldAdd = false
                    break
                }
                val iou = calculateIoU(node1.bounds, node2.bounds)
                if (iou > iouThreshold) {
                    shouldAdd = false
                    break
                }
            }
            if (shouldAdd) {
                filteredMap[id1] = node1
            }
        }
        return filteredMap
    }
}
