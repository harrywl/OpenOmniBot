package cn.com.omnimind.assists.detection.detectors.popup

import org.opencv.core.Rect
import cn.com.omnimind.assists.detection.detectors.popup.models.*

// =============== MaskRouter ===============

object MaskRouter {
    fun route(
        sheet: SheetDetectResult,
        popup: PopupDetectResult,
        preproc: SharedPreprocessResult,
        cfg: MaskDetectConfig
    ): FinalDecision {
        val sheetHigh = sheet.score >= 0.85f
        val popupHigh = popup.score >= 0.85f

        val sheetEdge = sheet.evidence?.let {
            it.bottomEdgeRatio <= (1f - cfg.sheetEdgeThreshold) && it.leftRightEdgeRatio >= cfg.sheetEdgeThreshold
        } ?: false

        val popupCentered = popup.evidence?.let {
            it.distRatio < 0.08f
        } ?: false

        return when {
            // 规则1: Sheet 高分且贴边 -> SHEET
            sheetHigh && sheetEdge -> {
                val finalScore = (sheet.score * (sheet.evidence?.sheetEnv ?: 1.0f)).coerceIn(0f, 1f)
                val bboxNorm = sheet.bbox?.let { toNormalized(it, preproc) }
                FinalDecision(
                    type = MaskAdType.SHEET,
                    score = finalScore,
                    bboxNorm = bboxNorm,
                    reason = "Sheet高分(${String.format("%.2f", sheet.score)})且贴边，bottomEdge=${String.format("%.2f", sheet.evidence?.bottomEdgeRatio ?: 0f)}，leftRightEdge=${String.format("%.2f", sheet.evidence?.leftRightEdgeRatio ?: 0f)}"
                )
            }

            // 规则2: Popup 高分且居中 -> CENTER_POPUP
            popupHigh && popupCentered -> {
                val finalScore = (popup.score * (popup.evidence?.popupEnv ?: 1.0f)).coerceIn(0f, 1f)
                val bboxNorm = popup.bbox?.let { toNormalized(it, preproc) }
                FinalDecision(
                    type = MaskAdType.CENTER_POPUP,
                    score = finalScore,
                    bboxNorm = bboxNorm,
                    reason = "Popup高分(${String.format("%.2f", popup.score)})且居中，distRatio=${String.format("%.2f", popup.evidence?.distRatio ?: 0f)}"
                )
            }

            // 规则3: 冲突时，贴边优先 SHEET
            sheetHigh && popupHigh -> {
                if (sheetEdge) {
                    val finalScore = (sheet.score * (sheet.evidence?.sheetEnv ?: 1.0f)).coerceIn(0f, 1f)
                    val bboxNorm = sheet.bbox?.let { toNormalized(it, preproc) }
                    FinalDecision(
                        type = MaskAdType.SHEET,
                        score = finalScore,
                        bboxNorm = bboxNorm,
                        reason = "冲突：Sheet贴边优先，sheetScore=${String.format("%.2f", sheet.score)}，popupScore=${String.format("%.2f", popup.score)}"
                    )
                } else {
                    val finalScore = (popup.score * (popup.evidence?.popupEnv ?: 1.0f)).coerceIn(0f, 1f)
                    val bboxNorm = popup.bbox?.let { toNormalized(it, preproc) }
                    FinalDecision(
                        type = MaskAdType.CENTER_POPUP,
                        score = finalScore,
                        bboxNorm = bboxNorm,
                        reason = "冲突：Sheet不贴边，优先Popup，sheetScore=${String.format("%.2f", sheet.score)}，popupScore=${String.format("%.2f", popup.score)}"
                    )
                }
            }

            // 规则4: 只有一方高分
            sheetHigh -> {
                val finalScore = (sheet.score * (sheet.evidence?.sheetEnv ?: 1.0f)).coerceIn(0f, 1f)
                val bboxNorm = sheet.bbox?.let { toNormalized(it, preproc) }
                FinalDecision(
                    type = MaskAdType.SHEET,
                    score = finalScore,
                    bboxNorm = bboxNorm,
                    reason = "仅Sheet高分(${String.format("%.2f", sheet.score)})"
                )
            }

            popupHigh -> {
                val finalScore = (popup.score * (popup.evidence?.popupEnv ?: 1.0f)).coerceIn(0f, 1f)
                val bboxNorm = popup.bbox?.let { toNormalized(it, preproc) }
                FinalDecision(
                    type = MaskAdType.CENTER_POPUP,
                    score = finalScore,
                    bboxNorm = bboxNorm,
                    reason = "仅Popup高分(${String.format("%.2f", popup.score)})"
                )
            }

            // 规则5: 都不高，选分数高的
            sheet.score >= popup.score && sheet.score > 0.5f -> {
                val finalScore = (sheet.score * (sheet.evidence?.sheetEnv ?: 1.0f)).coerceIn(0f, 1f)
                val bboxNorm = sheet.bbox?.let { toNormalized(it, preproc) }
                FinalDecision(
                    type = MaskAdType.SHEET,
                    score = finalScore,
                    bboxNorm = bboxNorm,
                    reason = "中分Sheet(${String.format("%.2f", sheet.score)}) > Popup(${String.format("%.2f", popup.score)})"
                )
            }

            popup.score > sheet.score && popup.score > 0.5f -> {
                val finalScore = (popup.score * (popup.evidence?.popupEnv ?: 1.0f)).coerceIn(0f, 1f)
                val bboxNorm = popup.bbox?.let { toNormalized(it, preproc) }
                FinalDecision(
                    type = MaskAdType.CENTER_POPUP,
                    score = finalScore,
                    bboxNorm = bboxNorm,
                    reason = "中分Popup(${String.format("%.2f", popup.score)}) > Sheet(${String.format("%.2f", sheet.score)})"
                )
            }

            // 规则6: 都很低 -> NONE
            else -> {
                FinalDecision(
                    type = MaskAdType.NONE,
                    score = maxOf(sheet.score, popup.score) * 0.5f,
                    bboxNorm = null,
                    reason = "Sheet(${String.format("%.2f", sheet.score)})和Popup(${String.format("%.2f", popup.score)})都未达标"
                )
            }
        }
    }

    private fun toNormalized(bbox: Rect, preproc: SharedPreprocessResult): RectFNorm {
        val W = preproc.fullWidth.toFloat()
        val H = preproc.fullHeight.toFloat()
        val top = preproc.roiRect.y

        return RectFNorm(
            left = bbox.x.toFloat() / W,
            top = (bbox.y + top).toFloat() / H,
            right = (bbox.x + bbox.width).toFloat() / W,
            bottom = (bbox.y + top + bbox.height).toFloat() / H
        )
    }
}

