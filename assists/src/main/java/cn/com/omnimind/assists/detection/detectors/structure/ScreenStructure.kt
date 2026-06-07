package cn.com.omnimind.assists.detection.detectors.structure

import cn.com.omnimind.assists.detection.detectors.icon.IconMatchResult
import org.opencv.core.Rect

data class Candidate(
    val rect: Rect,
    val kind: Kind,
    val score: Double,
    val tags: Set<Tag>,
    val reasons: List<String> = emptyList()
) {
    enum class Kind { ICON, INPUT_FIELD, OTHER }
    enum class Tag { TOP, BOTTOM, LEFT, RIGHT, CENTER }
}

data class ScreenAnalysisResult(
    val candidates: List<Candidate>,
    val matchedIcons: List<IconMatchResult> = emptyList(),
    val iconDebugs: Map<Rect, cn.com.omnimind.assists.detection.detectors.icon.IconTemplateMatcher.MatchDebugInfo> = emptyMap()
)
