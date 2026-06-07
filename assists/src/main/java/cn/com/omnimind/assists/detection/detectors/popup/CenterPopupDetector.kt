package cn.com.omnimind.assists.detection.detectors.popup

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import cn.com.omnimind.assists.detection.detectors.popup.models.*
import kotlin.math.*

// =============== CenterPopupDetector (圆角/异形友好版) ===============

object CenterPopupDetector {
    fun detect(preproc: SharedPreprocessResult, cfg: MaskDetectConfig): PopupDetectResult {
        val v = preproc.hsvV
        val rows = v.rows()
        val cols = v.cols()

        // 阈值：用 Otsu
        val bin = Mat()
        Imgproc.threshold(v, bin, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)

        // Clone bin for debug
        val debugPopupBin = bin.clone()

        // 形态学：闭运算填洞，开运算去噪
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
        val closed = Mat()
        Imgproc.morphologyEx(bin, closed, Imgproc.MORPH_CLOSE, kernel)
        val opened = Mat()
        Imgproc.morphologyEx(closed, opened, Imgproc.MORPH_OPEN, kernel)

        // Clone opened for debug
        val debugPopupMorph = opened.clone()

        bin.release()
        closed.release()
        kernel.release()

        // 找轮廓
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(opened, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()
        opened.release()

        if (contours.isEmpty()) {
            debugPopupBin.release()
            debugPopupMorph.release()
            return PopupDetectResult(0f, null, null)
        }

        val areaTotal = (rows * cols).toDouble()
        val centerX = cols / 2.0
        val centerY = rows / 2.0
        val diag = sqrt((cols * cols + rows * rows).toDouble())

        // ========== A. 候选提取改造（圆角/异形友好）==========
        val candidates = extractCandidates(contours, areaTotal, centerX, centerY, diag, cfg)

        var bestRect: Rect? = null
        var bestScore = 0f
        var bestEvidence: PopupEvidence? = null
        var bestDebugDarkMask: Mat? = null
        var bestDebugDarkNear: Mat? = null
        var bestDebugDarkFar: Mat? = null

        for (cand in candidates) {
            // ========== B. Reject（硬门）检查 ==========
            val rejectResult = checkReject(cand, v, cfg)
            if (rejectResult.rejected) {
                // 被 reject，跳过
                rejectResult.debugDarkMask?.release()
                rejectResult.debugDarkNear?.release()
                rejectResult.debugDarkFar?.release()
                continue
            }

            // ========== B2. 打分（软分）==========
            val scoreResult = computePopupScore(cand, rejectResult, cfg)

            val finalScore = scoreResult.score

            if (finalScore > bestScore) {
                // Release old best debug images
                bestDebugDarkMask?.release()
                bestDebugDarkNear?.release()
                bestDebugDarkFar?.release()

                bestScore = finalScore
                bestRect = cand.rect
                bestEvidence = PopupEvidence(
                    ringOk = rejectResult.ringOk,
                    deltaV = rejectResult.deltaV,
                    ccFar = rejectResult.ccFar,
                    ccNear = rejectResult.ccNear,
                    areaRatio = cand.areaRatio,
                    distRatio = cand.distRatio,
                    insideMeanV = rejectResult.insideMeanV,
                    ringMeanV = rejectResult.ringMeanV,
                    popupEnv = computePopupEnv(rejectResult.ringOk, rejectResult.deltaV, rejectResult.ccFar, cand.areaRatio, cand.distRatio, cfg),

                    // 形状特征
                    extent = cand.extent,
                    solidity = cand.solidity,
                    circularity = cand.circularity,
                    candQuality = cand.quality,

                    // ring 质量
                    ringDarkRatio = rejectResult.ringDarkRatio,

                    // 打分项
                    ringScore = scoreResult.ringScore,
                    dvScore = scoreResult.dvScore,
                    distScore = scoreResult.distScore,
                    areaScore = scoreResult.areaScore,
                    shapeBonus = scoreResult.shapeBonus,

                    // reject 原因
                    rejectReason = null
                )
                bestDebugDarkMask = rejectResult.debugDarkMask
                bestDebugDarkNear = rejectResult.debugDarkNear
                bestDebugDarkFar = rejectResult.debugDarkFar
            } else {
                // Release debug images of non-best candidates
                rejectResult.debugDarkMask?.release()
                rejectResult.debugDarkNear?.release()
                rejectResult.debugDarkFar?.release()
            }
        }

        for (c in contours) c.release()

        return PopupDetectResult(
            bestScore,
            bestRect,
            bestEvidence,
            debugPopupBin,
            debugPopupMorph,
            bestDebugDarkMask,
            bestDebugDarkNear,
            bestDebugDarkFar
        )
    }

    // ========== A. 候选提取：计算形状特征 + 综合质量分 ==========
    private data class CandidateInfo(
        val rect: Rect,
        val area: Double,
        val areaRatio: Float,
        val distRatio: Float,
        val extent: Float,        // area / bboxArea
        val solidity: Float,      // area / hullArea
        val circularity: Float,   // 4π*area/(perimeter^2)
        val quality: Float        // 综合质量分
    )

    private fun extractCandidates(
        contours: List<MatOfPoint>,
        areaTotal: Double,
        centerX: Double,
        centerY: Double,
        diag: Double,
        cfg: MaskDetectConfig
    ): List<CandidateInfo> {
        val candidates = mutableListOf<CandidateInfo>()

        // 只处理面积前 N 大的轮廓（性能优化）
        val sorted = contours.sortedByDescending { Imgproc.contourArea(it) }.take(5)

        for (c in sorted) {
            val rect = Imgproc.boundingRect(c)
            val area = Imgproc.contourArea(c)

            // 基础过滤：面积必须在合理范围（但不作为 reject）
            val areaRatio = (area / areaTotal).toFloat()
            if (areaRatio < cfg.minPopupAreaRatio * 0.5f || areaRatio > cfg.maxPopupAreaRatio * 1.5f) continue

            val bboxArea = rect.area().toDouble()
            if (bboxArea <= 0) continue

            // 计算形状特征
            val extent = (area / bboxArea).toFloat().coerceIn(0f, 1f)

            // convexHull + solidity
            val hull = MatOfInt()
            Imgproc.convexHull(c, hull)
            val hullPoints = MatOfPoint()
            val hullIndices = hull.toArray()
            val contourPoints = c.toArray()
            val hullPointsArray = Array(hullIndices.size) { i -> contourPoints[hullIndices[i]] }
            hullPoints.fromArray(*hullPointsArray)
            val hullArea = Imgproc.contourArea(hullPoints).coerceAtLeast(1.0)
            hull.release()
            hullPoints.release()

            val solidity = (area / hullArea).toFloat().coerceIn(0f, 1f)

            // circularity
            val perimeter = Imgproc.arcLength(MatOfPoint2f(*c.toArray()), true).coerceAtLeast(1.0)
            val circularity = (4 * PI * area / (perimeter * perimeter)).toFloat().coerceIn(0f, 1f)

            // 位置
            val rcx = rect.x + rect.width / 2.0
            val rcy = rect.y + rect.height / 2.0
            val dist = sqrt((rcx - centerX) * (rcx - centerX) + (rcy - centerY) * (rcy - centerY))
            val distRatio = (dist / diag).toFloat()

            // 综合质量分（用于候选选择，不影响最终打分）
            val normAreaRatio = clamp01((areaRatio - cfg.minPopupAreaRatio) / (cfg.maxPopupAreaRatio - cfg.minPopupAreaRatio))
            val quality = 0.6f * normAreaRatio + 0.2f * solidity + 0.2f * extent

            candidates.add(
                CandidateInfo(
                    rect = rect,
                    area = area,
                    areaRatio = areaRatio,
                    distRatio = distRatio,
                    extent = extent,
                    solidity = solidity,
                    circularity = circularity,
                    quality = quality
                )
            )
        }

        // 按 quality 排序，返回前 3 个
        return candidates.sortedByDescending { it.quality }.take(3)
    }

    // ========== B. Reject 检查（硬门） ==========
    private data class RejectCheckResult(
        val rejected: Boolean,
        val rejectReason: String?,
        val ringOk: Boolean,
        val deltaV: Double,
        val ccFar: Float,
        val ccNear: Float,
        val insideMeanV: Double,
        val ringMeanV: Double,
        val ringDarkRatio: Float,
        val debugDarkMask: Mat? = null,
        val debugDarkNear: Mat? = null,
        val debugDarkFar: Mat? = null
    )

    private fun checkReject(cand: CandidateInfo, v: Mat, cfg: MaskDetectConfig): RejectCheckResult {
        val rows = v.rows()
        val cols = v.cols()

        // 硬门 1: areaRatio 超出范围
        if (cand.areaRatio < cfg.minPopupAreaRatio || cand.areaRatio > cfg.maxPopupAreaRatio) {
            return RejectCheckResult(
                rejected = true,
                rejectReason = "areaRatio out of range: ${cand.areaRatio}",
                ringOk = false, deltaV = 0.0, ccFar = 0f, ccNear = 0f,
                insideMeanV = 0.0, ringMeanV = 0.0, ringDarkRatio = 0f
            )
        }

        // 硬门 2: distRatio 太大
        if (cand.distRatio > cfg.centerMaxDistRatio) {
            return RejectCheckResult(
                rejected = true,
                rejectReason = "distRatio too large: ${cand.distRatio}",
                ringOk = false, deltaV = 0.0, ccFar = 0f, ccNear = 0f,
                insideMeanV = 0.0, ringMeanV = 0.0, ringDarkRatio = 0f
            )
        }

        // 计算 inside vs ring 的 deltaV
        val insideRoi = v.submat(cand.rect)
        val insideMean = Core.mean(insideRoi).`val`[0]
        insideRoi.release()

        // 简单外扩估算 ringMean
        val simpleExpand = maxOf(cand.rect.width, cand.rect.height) / 5
        val sx = (cand.rect.x - simpleExpand).coerceIn(0, cols - 1)
        val sy = (cand.rect.y - simpleExpand).coerceIn(0, rows - 1)
        val sx2 = (cand.rect.x + cand.rect.width + simpleExpand).coerceIn(0, cols)
        val sy2 = (cand.rect.y + cand.rect.height + simpleExpand).coerceIn(0, rows)
        val simpleOuter = Rect(sx, sy, sx2 - sx, sy2 - sy)
        val simpleOuterMat = v.submat(simpleOuter)
        val simpleRingMean = Core.mean(simpleOuterMat).`val`[0]
        simpleOuterMat.release()
        val deltaV = insideMean - simpleRingMean

        // 硬门 3: deltaV 不足（亮度对比不够）
        if (deltaV < cfg.minInsideRingDeltaV) {
            return RejectCheckResult(
                rejected = true,
                rejectReason = "deltaV insufficient: $deltaV",
                ringOk = false, deltaV = deltaV, ccFar = 0f, ccNear = 0f,
                insideMeanV = insideMean, ringMeanV = simpleRingMean, ringDarkRatio = 0f
            )
        }

        // 自适应暗阈值
        val darkThr = simpleRingMean + cfg.darkThresholdT

        // 双圈暗连续性验证
        val doubleRingResult = computeDoubleRingCC(v, cand.rect, darkThr, cfg)
        val ccNear = doubleRingResult.ccNear
        val ccFar = doubleRingResult.ccFar
        val ringOk = doubleRingResult.ringOk
        val ringDarkRatio = doubleRingResult.ringDarkRatio

        // 硬门 4: ringOk == false（暗连续性失败）
        if (!ringOk) {
            return RejectCheckResult(
                rejected = true,
                rejectReason = "ring continuity failed",
                ringOk = false, deltaV = deltaV, ccFar = ccFar, ccNear = ccNear,
                insideMeanV = insideMean, ringMeanV = simpleRingMean, ringDarkRatio = ringDarkRatio,
                debugDarkMask = doubleRingResult.debugDarkMask,
                debugDarkNear = doubleRingResult.debugDarkNear,
                debugDarkFar = doubleRingResult.debugDarkFar
            )
        }

        // 硬门 5: ringDarkRatio 过低（防误连通）
        // 如果 ringOk==true 但暗像素占比很低，说明是噪声/误连通
        if (ringDarkRatio < 0.25f) {
            return RejectCheckResult(
                rejected = true,
                rejectReason = "ringDarkRatio too low: $ringDarkRatio (fake continuity)",
                ringOk = ringOk, deltaV = deltaV, ccFar = ccFar, ccNear = ccNear,
                insideMeanV = insideMean, ringMeanV = simpleRingMean, ringDarkRatio = ringDarkRatio,
                debugDarkMask = doubleRingResult.debugDarkMask,
                debugDarkNear = doubleRingResult.debugDarkNear,
                debugDarkFar = doubleRingResult.debugDarkFar
            )
        }

        // 硬门 6（可选）：整体太暗（防黑屏/夜间误判）
        if (insideMean < 30.0 && simpleRingMean < 30.0) {
            return RejectCheckResult(
                rejected = true,
                rejectReason = "overall too dark (inside=$insideMean, ring=$simpleRingMean)",
                ringOk = ringOk, deltaV = deltaV, ccFar = ccFar, ccNear = ccNear,
                insideMeanV = insideMean, ringMeanV = simpleRingMean, ringDarkRatio = ringDarkRatio,
                debugDarkMask = doubleRingResult.debugDarkMask,
                debugDarkNear = doubleRingResult.debugDarkNear,
                debugDarkFar = doubleRingResult.debugDarkFar
            )
        }

        // 通过所有硬门
        return RejectCheckResult(
            rejected = false,
            rejectReason = null,
            ringOk = ringOk,
            deltaV = deltaV,
            ccFar = ccFar,
            ccNear = ccNear,
            insideMeanV = insideMean,
            ringMeanV = simpleRingMean,
            ringDarkRatio = ringDarkRatio,
            debugDarkMask = doubleRingResult.debugDarkMask,
            debugDarkNear = doubleRingResult.debugDarkNear,
            debugDarkFar = doubleRingResult.debugDarkFar
        )
    }

    // ========== B2. 打分（软分）==========
    private data class ScoreResult(
        val score: Float,
        val ringScore: Float,
        val dvScore: Float,
        val distScore: Float,
        val areaScore: Float,
        val shapeBonus: Float
    )

    private fun computePopupScore(
        cand: CandidateInfo,
        rejectResult: RejectCheckResult,
        cfg: MaskDetectConfig
    ): ScoreResult {
        // 1. Ring 证据分（0~1）
        val ccNearContrib = smoothstep(rejectResult.ccNear, 0.4f, 0.8f)
        val ccFarContrib = smoothstep(rejectResult.ccFar, cfg.farRingCCLo, cfg.farRingCCHi)
        val ringScore = (0.5f * ccNearContrib + 0.5f * ccFarContrib).coerceIn(0f, 1f)

        // 2. DeltaV 证据分（0~1）
        val dvScore = smoothstep(rejectResult.deltaV, 20.0, 80.0)

        // 3. 位置先验分（0~1）
        val distScore = (1f - (cand.distRatio / cfg.centerMaxDistRatio)).coerceIn(0f, 1f)

        // 4. 尺寸先验分（0~1）：落在舒适区加分，过小/过大轻微扣分
        val comfortLo = 0.12f
        val comfortHi = 0.55f
        val areaScore = when {
            cand.areaRatio in comfortLo..comfortHi -> {
                // 舒适区内，给满分
                1f
            }
            cand.areaRatio < comfortLo -> {
                // 偏小，线性衰减
                (cand.areaRatio / comfortLo).coerceIn(0f, 1f)
            }
            else -> {
                // 偏大，线性衰减
                (1f - (cand.areaRatio - comfortHi) / (cfg.maxPopupAreaRatio - comfortHi)).coerceIn(0f, 1f)
            }
        }

        // 5. 形状加分（0~0.08）：轻微加分，永不致命扣分
        val shapeScore = 0.6f * cand.solidity + 0.4f * cand.extent
        val shapeBonus = (0.08f * clamp01(shapeScore)).coerceIn(0f, 0.08f)

        // 最终分数：证据加权 + 形状加分
        val finalScore = clamp01(
            0.45f * ringScore +
            0.45f * dvScore +
            0.05f * distScore +
            0.05f * areaScore +
            shapeBonus
        )

        return ScoreResult(
            score = finalScore,
            ringScore = ringScore,
            dvScore = dvScore,
            distScore = distScore,
            areaScore = areaScore,
            shapeBonus = shapeBonus
        )
    }

    private fun computePopupEnv(
        ringOk: Boolean,
        deltaV: Double,
        ccFar: Float,
        areaRatio: Float,
        distRatio: Float,
        cfg: MaskDetectConfig
    ): Float {
        val base = 0.75f
        val dvTerm = if (ringOk) 0.20f * smoothstep(deltaV, cfg.minInsideRingDeltaV, 25.0) else 0f
        val ccTerm = if (ringOk) 0.05f * smoothstep(ccFar, 0.45f, 0.75f) else 0f
        val areaTerm = 0.05f * smoothstep(areaRatio, 0.15f, 0.40f)
        val distPenalty = -0.05f * smoothstep(distRatio, 0.05f, 0.15f)

        return (base + dvTerm + ccTerm + areaTerm + distPenalty).coerceIn(0f, 2f)
    }

    private data class DoubleRingCCResult(
        val ccNear: Float,
        val ccFar: Float,
        val ringOk: Boolean,
        val ringDarkRatio: Float,  // 新增：ring区域暗像素占比
        val debugDarkMask: Mat? = null,
        val debugDarkNear: Mat? = null,
        val debugDarkFar: Mat? = null
    )

    private fun computeDoubleRingCC(
        v: Mat,
        innerRect: Rect,
        darkThr: Double,
        cfg: MaskDetectConfig
    ): DoubleRingCCResult {
        val rows = v.rows()
        val cols = v.cols()

        val shortSide = minOf(innerRect.width, innerRect.height)
        val outerExpand = maxOf((shortSide * cfg.outerExpandRatio).toInt(), cfg.outerExpandMinPx)
        val bufferExpand = maxOf((outerExpand * cfg.bufferExpandRatio).toInt(), cfg.bufferExpandMinPx)

        val ex1 = (innerRect.x - bufferExpand).coerceIn(0, cols - 1)
        val ey1 = (innerRect.y - bufferExpand).coerceIn(0, rows - 1)
        val ex2 = (innerRect.x + innerRect.width + bufferExpand).coerceIn(0, cols)
        val ey2 = (innerRect.y + innerRect.height + bufferExpand).coerceIn(0, rows)
        val bufferRect = Rect(ex1, ey1, ex2 - ex1, ey2 - ey1)

        val ox1 = (innerRect.x - outerExpand).coerceIn(0, cols - 1)
        val oy1 = (innerRect.y - outerExpand).coerceIn(0, rows - 1)
        val ox2 = (innerRect.x + innerRect.width + outerExpand).coerceIn(0, cols)
        val oy2 = (innerRect.y + innerRect.height + outerExpand).coerceIn(0, rows)
        val outerRect = Rect(ox1, oy1, ox2 - ox1, oy2 - oy1)

        val outerArea = outerRect.area().toDouble()
        val bufferArea = bufferRect.area().toDouble()
        val innerArea = innerRect.area().toDouble()
        if (outerArea <= bufferArea || bufferArea <= innerArea) {
            return DoubleRingCCResult(0f, 0f, false, 0f)
        }

        val outerV = v.submat(outerRect)

        val clampedDarkThr = darkThr.coerceIn(0.0, 255.0)
        val darkMask = Mat()
        Core.inRange(outerV, Scalar(0.0), Scalar(clampedDarkThr), darkMask)

        val nearRingMask = Mat.zeros(outerRect.height, outerRect.width, CvType.CV_8U)
        val farRingMask = Mat.zeros(outerRect.height, outerRect.width, CvType.CV_8U)

        val innerInOuter = Rect(
            innerRect.x - outerRect.x,
            innerRect.y - outerRect.y,
            innerRect.width,
            innerRect.height
        )
        val bufferInOuter = Rect(
            bufferRect.x - outerRect.x,
            bufferRect.y - outerRect.y,
            bufferRect.width,
            bufferRect.height
        )

        Imgproc.rectangle(nearRingMask, Point(bufferInOuter.x.toDouble(), bufferInOuter.y.toDouble()),
            Point((bufferInOuter.x + bufferInOuter.width - 1).toDouble(), (bufferInOuter.y + bufferInOuter.height - 1).toDouble()),
            Scalar(255.0), -1)
        Imgproc.rectangle(nearRingMask, Point(innerInOuter.x.toDouble(), innerInOuter.y.toDouble()),
            Point((innerInOuter.x + innerInOuter.width - 1).toDouble(), (innerInOuter.y + innerInOuter.height - 1).toDouble()),
            Scalar(0.0), -1)

        Imgproc.rectangle(farRingMask, Point(0.0, 0.0),
            Point((outerRect.width - 1).toDouble(), (outerRect.height - 1).toDouble()),
            Scalar(255.0), -1)
        Imgproc.rectangle(farRingMask, Point(bufferInOuter.x.toDouble(), bufferInOuter.y.toDouble()),
            Point((bufferInOuter.x + bufferInOuter.width - 1).toDouble(), (bufferInOuter.y + bufferInOuter.height - 1).toDouble()),
            Scalar(0.0), -1)

        val darkNear = Mat()
        val darkFar = Mat()
        Core.bitwise_and(darkMask, nearRingMask, darkNear)
        Core.bitwise_and(darkMask, farRingMask, darkFar)

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.morphologyEx(darkNear, darkNear, Imgproc.MORPH_OPEN, kernel)
        Imgproc.morphologyEx(darkFar, darkFar, Imgproc.MORPH_OPEN, kernel)

        val nearRingArea = Core.countNonZero(nearRingMask).toFloat()
        val farRingArea = Core.countNonZero(farRingMask).toFloat()

        // 计算 ringDarkRatio（整个 ring 区域的暗像素占比）
        val totalRingMask = Mat()
        Core.bitwise_or(nearRingMask, farRingMask, totalRingMask)
        val totalRingArea = Core.countNonZero(totalRingMask).toFloat()
        val darkInRing = Mat()
        Core.bitwise_and(darkMask, totalRingMask, darkInRing)
        val darkInRingCount = Core.countNonZero(darkInRing).toFloat()
        val ringDarkRatio = if (totalRingArea > 0) darkInRingCount / totalRingArea else 0f

        totalRingMask.release()
        darkInRing.release()

        fun largestCC(mask: Mat): Float {
            val labels = Mat()
            val stats = Mat()
            val centroids = Mat()
            val num = Imgproc.connectedComponentsWithStats(mask, labels, stats, centroids, 8, CvType.CV_32S)
            var maxArea = 0
            for (i in 1 until num) {
                val area = stats.get(i, Imgproc.CC_STAT_AREA)[0].toInt()
                if (area > maxArea) maxArea = area
            }
            labels.release()
            stats.release()
            centroids.release()
            return maxArea.toFloat()
        }

        val largestNear = largestCC(darkNear)
        val largestFar = largestCC(darkFar)

        val ccNear = if (nearRingArea > 0) (largestNear / nearRingArea) else 0f
        val ccFar = if (farRingArea > 0) (largestFar / farRingArea) else 0f

        // Clone debug images before releasing
        val debugDarkMask = darkMask.clone()
        val debugDarkNear = darkNear.clone()
        val debugDarkFar = darkFar.clone()

        outerV.release()
        darkMask.release()
        nearRingMask.release()
        farRingMask.release()
        darkNear.release()
        darkFar.release()
        kernel.release()

        return DoubleRingCCResult(ccNear, ccFar, true, ringDarkRatio, debugDarkMask, debugDarkNear, debugDarkFar)
    }
}

// =============== 辅助函数 ===============

internal fun smoothstep(x: Float, lo: Float, hi: Float): Float {
    if (x <= lo) return 0f
    if (x >= hi) return 1f
    val t = ((x - lo) / (hi - lo)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun smoothstep(x: Double, lo: Double, hi: Double): Float {
    return smoothstep(x.toFloat(), lo.toFloat(), hi.toFloat())
}

internal fun clamp01(x: Float): Float = x.coerceIn(0f, 1f)
