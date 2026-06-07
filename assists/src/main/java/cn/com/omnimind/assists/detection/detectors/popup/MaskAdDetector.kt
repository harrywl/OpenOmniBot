package cn.com.omnimind.assists.detection.detectors.popup

import android.graphics.Bitmap
import org.opencv.core.*
import cn.com.omnimind.assists.detection.detectors.popup.models.*

// =============== 主检测器 ===============

object MaskAdDetector {

    /**
     * 是否启用 BottomSheet 检测
     * false: 屏蔽 bottomSheet 检测，固定返回不符合的结果
     * true: 正常执行 bottomSheet 检测逻辑
     */
    private const val ENABLE_BOTTOM_SHEET_DETECTION = false

    fun detect(
        bitmap: Bitmap,
        cfg: MaskDetectConfig = MaskDetectConfig(),
        statusBarHeightPx: Int = 0,
        navigationBarHeightPx: Int = 0
    ): MaskDetectResult {
        val startTime = System.currentTimeMillis()

        // 1) 共享预处理
        val preproc = SharedPreprocess.process(bitmap, cfg, statusBarHeightPx, navigationBarHeightPx)

        // 2) Sheet 检测
        val sheetResult = if (ENABLE_BOTTOM_SHEET_DETECTION) {
            // 正常执行 bottomSheet 检测
            BottomSheetDetector.detect(preproc, cfg)
        } else {
            // 屏蔽检测，固定返回不符合的结果
            SheetDetectResult(
                score = 0.0f,
                bbox = null,
                evidence = null,
                debugRowMeanPlot = null
            )
        }

        // 3) Popup 检测
        val popupResult = CenterPopupDetector.detect(preproc, cfg)

        // 4) 路由决策
        val final = MaskRouter.route(sheetResult, popupResult, preproc, cfg)

        // 5) 构建候选列表
        val (candidates, candidateReasons) = buildCandidates(sheetResult, popupResult, preproc)

        // 6) debug 输出
        val debugImgs = if (cfg.enableDebugImages) {
            buildDebugImages(bitmap, preproc, sheetResult, popupResult)
        } else {
            emptyMap()
        }

        // 7) 将候选为空的原因添加到 debug 输出
        val debugWithReasons = if (candidates.isEmpty()) {
            debugImgs + candidateReasons
        } else {
            debugImgs
        }

        // 释放资源
        preproc.hsvV.release()
        preproc.hsvS.release()
        preproc.roi.release()

        // 释放 debug 图像 (无论是否启用 debug)
        sheetResult.debugRowMeanPlot?.release()
        popupResult.debugPopupBin?.release()
        popupResult.debugPopupMorph?.release()
        popupResult.debugRingDarkMask?.release()
        popupResult.debugRingNear?.release()
        popupResult.debugRingFar?.release()

        val elapsedTime = System.currentTimeMillis() - startTime

        return MaskDetectResult(
            candidates = candidates,
            final = final,
            elapsedTimeMs = elapsedTime,
            debug = debugWithReasons
        )
    }

    private fun buildCandidates(
        sheet: SheetDetectResult,
        popup: PopupDetectResult,
        preproc: SharedPreprocessResult
    ): Pair<List<MaskCandidate>, Map<String, String>> {
        val candidates = mutableListOf<MaskCandidate>()
        val reasons = mutableMapOf<String, String>()

        // Sheet 候选
        if (sheet.score > 0.3f && sheet.bbox != null && sheet.evidence != null) {
            val bboxNorm = toNormalized(sheet.bbox, preproc)
            candidates.add(
                MaskCandidate(
                    type = MaskAdType.SHEET,
                    score = sheet.score,
                    bboxNorm = bboxNorm,
                    evidence = mapOf(
                        "splitY" to sheet.evidence.splitY,
                        "topMeanV" to sheet.evidence.topMeanV,
                        "bottomMeanV" to sheet.evidence.bottomMeanV,
                        "delta" to sheet.evidence.delta,
                        "coverBright" to sheet.evidence.coverBright,
                        "coverDark" to sheet.evidence.coverDark,
                        "bottomEdgeRatio" to sheet.evidence.bottomEdgeRatio,
                        "leftRightEdgeRatio" to sheet.evidence.leftRightEdgeRatio,
                        "sheetEnv" to sheet.evidence.sheetEnv
                    )
                )
            )
        } else {
            // 记录 Sheet 未被添加的原因
            when {
                sheet.bbox == null -> reasons["sheet"] = "未检测到分割线或亮度跃迁不足 (maxTransition < 30.0)"
                sheet.score <= 0.3f -> reasons["sheet"] = "分数过低 (score=${String.format("%.2f", sheet.score)} ≤ 0.3), 证据不足: 亮度差/覆盖率偏低"
                else -> reasons["sheet"] = "未知原因 (bbox或evidence为null)"
            }
        }

        // Popup 候选
        if (popup.score > 0.3f && popup.bbox != null && popup.evidence != null) {
            val bboxNorm = toNormalized(popup.bbox, preproc)
            candidates.add(
                MaskCandidate(
                    type = MaskAdType.CENTER_POPUP,
                    score = popup.score,
                    bboxNorm = bboxNorm,
                    evidence = mapOf(
                        "ringOk" to popup.evidence.ringOk,
                        "deltaV" to popup.evidence.deltaV,
                        "ccFar" to popup.evidence.ccFar,
                        "ccNear" to popup.evidence.ccNear,
                        "areaRatio" to popup.evidence.areaRatio,
                        "distRatio" to popup.evidence.distRatio,
                        "insideMeanV" to popup.evidence.insideMeanV,
                        "ringMeanV" to popup.evidence.ringMeanV,
                        "popupEnv" to popup.evidence.popupEnv
                    )
                )
            )
        } else {
            // 记录 Popup 未被添加的原因
            when {
                popup.bbox == null && popup.evidence == null -> {
                    reasons["popup"] = "未检测到轮廓 (Otsu二值化+形态学处理后无轮廓)"
                }
                popup.bbox != null && popup.evidence != null && popup.score <= 0.3f -> {
                    reasons["popup"] = "分数过低 (score=${String.format("%.2f", popup.score)} ≤ 0.3), 证据不足: ring/deltaV/位置/尺寸"
                }
                popup.evidence?.rejectReason != null -> {
                    reasons["popup"] = "被reject: ${popup.evidence.rejectReason}"
                }
                else -> {
                    reasons["popup"] = "未知原因"
                }
            }
        }

        return Pair(candidates, reasons)
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

