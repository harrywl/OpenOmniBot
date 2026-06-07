package cn.com.omnimind.assists.detection.detectors.icon

import org.opencv.core.Rect

data class IconMatchResult(
    val iconId: String,
    val score: Double,
    val rect: Rect,
    val templateType: String // gray / edge
)
