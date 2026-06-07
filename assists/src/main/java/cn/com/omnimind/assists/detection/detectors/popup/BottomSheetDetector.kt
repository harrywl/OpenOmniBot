package cn.com.omnimind.assists.detection.detectors.popup

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import cn.com.omnimind.assists.detection.detectors.popup.models.*

// =============== BottomSheetDetector ===============

object BottomSheetDetector {
    fun detect(preproc: SharedPreprocessResult, cfg: MaskDetectConfig): SheetDetectResult {
        val v = preproc.hsvV
        val rowMeanV = preproc.rowMeanV
        val rows = v.rows()
        val cols = v.cols()

        // 1) 找最大跃迁点（上暗下亮的分割线）
        var bestY = -1
        var maxTransition = 0.0

        for (y in (rows * 0.2).toInt() until (rows * 0.8).toInt()) {
            // 计算上方窗口均值（y-10 到 y）
            val topStart = maxOf(0, y - 10)
            var topSum = 0.0
            for (yy in topStart until y) {
                topSum += rowMeanV[yy]
            }
            val topMean = if (y > topStart) topSum / (y - topStart) else 0.0

            // 计算下方窗口均值（y 到 y+10）
            val bottomEnd = minOf(rows, y + 10)
            var bottomSum = 0.0
            for (yy in y until bottomEnd) {
                bottomSum += rowMeanV[yy]
            }
            val bottomMean = if (bottomEnd > y) bottomSum / (bottomEnd - y) else 0.0

            val transition = bottomMean - topMean
            if (transition > maxTransition) {
                maxTransition = transition
                bestY = y
            }
        }

        if (bestY < 0 || maxTransition < cfg.sheetMinDelta) {
            return SheetDetectResult(0f, null, null)
        }

        // 2) 计算上半部和下半部的统计信息
        val topHalf = v.submat(0, bestY, 0, cols)
        val bottomHalf = v.submat(bestY, rows, 0, cols)

        val topMeanV = Core.mean(topHalf).`val`[0]
        val bottomMeanV = Core.mean(bottomHalf).`val`[0]
        val delta = bottomMeanV - topMeanV

        // 计算下半部亮区覆盖率（V > 200）
        val brightMask = Mat()
        Core.inRange(bottomHalf, Scalar(200.0), Scalar(255.0), brightMask)
        val bottomBrightCount = Core.countNonZero(brightMask).toFloat()
        val bottomArea = (bottomHalf.rows() * bottomHalf.cols()).toFloat()
        val coverBright = bottomBrightCount / bottomArea

        // 计算上半部暗区覆盖率（V < 100）
        val darkMask = Mat()
        Core.inRange(topHalf, Scalar(0.0), Scalar(100.0), darkMask)
        val topDarkCount = Core.countNonZero(darkMask).toFloat()
        val topArea = (topHalf.rows() * topHalf.cols()).toFloat()
        val coverDark = topDarkCount / topArea

        brightMask.release()
        darkMask.release()
        topHalf.release()
        bottomHalf.release()

        // 3) 计算 bbox（整个下半部作为 sheet 区域）
        val bbox = Rect(0, bestY, cols, rows - bestY)

        // 4) 计算贴边特征
        val bottomEdgeRatio = (rows - (bestY + bbox.height)).toFloat() / rows.toFloat()
        val leftRightEdgeRatio = 1.0f  // 总是贴左右

        // 5) 计算 sheetEnv（基于上下对比）
        val sheetEnv = when {
            delta >= 60.0 -> 1.10f
            delta >= 40.0 -> 1.05f
            else -> 1.0f
        }

        // 6) 计算 score
        var score = 0f

        // 亮度差贡献（0.5）
        if (delta >= cfg.sheetMinDelta) {
            score += 0.5f * ((delta - cfg.sheetMinDelta) / 60.0).coerceIn(0.0, 1.0).toFloat()
        }

        // 下半部亮覆盖率贡献（0.3）
        if (coverBright >= cfg.sheetMinBrightCover) {
            score += 0.3f * ((coverBright - cfg.sheetMinBrightCover) / 0.4f).coerceIn(0f, 1f)
        }

        // 上半部暗覆盖率贡献（0.2）
        if (coverDark >= cfg.sheetMinDarkCover) {
            score += 0.2f * ((coverDark - cfg.sheetMinDarkCover) / 0.4f).coerceIn(0f, 1f)
        }

        score = score.coerceIn(0f, 1f)

        val evidence = SheetEvidence(
            splitY = bestY,
            topMeanV = topMeanV,
            bottomMeanV = bottomMeanV,
            delta = delta,
            coverBright = coverBright,
            coverDark = coverDark,
            bottomEdgeRatio = bottomEdgeRatio,
            leftRightEdgeRatio = leftRightEdgeRatio,
            sheetEnv = sheetEnv
        )

        // 7) 生成 debug 图：rowMeanV 曲线 + bestY 竖线
        val debugPlot = generateRowMeanPlot(rowMeanV, bestY, rows)

        return SheetDetectResult(score, bbox, evidence, debugPlot)
    }

    private fun generateRowMeanPlot(rowMeanV: DoubleArray, bestY: Int, rows: Int): Mat {
        val plotW = 400
        val plotH = 300
        val plot = Mat(plotH, plotW, CvType.CV_8UC3, Scalar(255.0, 255.0, 255.0)) // 白色背景

        if (rowMeanV.isEmpty()) return plot

        // 找到 rowMeanV 的最大最小值
        val minV = rowMeanV.minOrNull() ?: 0.0
        val maxV = rowMeanV.maxOrNull() ?: 255.0
        val rangeV = maxV - minV
        if (rangeV <= 0) return plot

        val margin = 40
        val drawW = plotW - 2 * margin
        val drawH = plotH - 2 * margin

        // 绘制曲线
        for (i in 0 until rows - 1) {
            val x1 = margin + (i.toFloat() / rows * drawW).toInt()
            val y1 = margin + ((maxV - rowMeanV[i]) / rangeV * drawH).toInt()
            val x2 = margin + ((i + 1).toFloat() / rows * drawW).toInt()
            val y2 = margin + ((maxV - rowMeanV[i + 1]) / rangeV * drawH).toInt()
            Imgproc.line(plot, Point(x1.toDouble(), y1.toDouble()), Point(x2.toDouble(), y2.toDouble()),
                Scalar(0.0, 0.0, 255.0), 2) // 红色曲线
        }

        // 绘制 bestY 竖线
        if (bestY >= 0 && bestY < rows) {
            val xBest = margin + (bestY.toFloat() / rows * drawW).toInt()
            Imgproc.line(plot, Point(xBest.toDouble(), margin.toDouble()),
                Point(xBest.toDouble(), (margin + drawH).toDouble()),
                Scalar(0.0, 255.0, 0.0), 2) // 绿色竖线
        }

        // 绘制坐标轴标签
        Imgproc.putText(plot, "Row Index", Point(plotW / 2 - 30.0, plotH - 10.0),
            Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, Scalar(0.0, 0.0, 0.0), 1)
        Imgproc.putText(plot, "V", Point(10.0, 20.0),
            Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, Scalar(0.0, 0.0, 0.0), 1)

        return plot
    }
}

