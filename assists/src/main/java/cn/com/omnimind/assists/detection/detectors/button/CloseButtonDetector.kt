package cn.com.omnimind.assists.detection.detectors.button

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

/**
 * 四臂验证数据（新增 tightness 和线宽）
 */
data class ArmVerification(
    val cover: Double,        // 覆盖率 [0,1]
    val longestRun: Double,   // 最长连续命中段长度（像素）
    val tightness: Double,    // 贴线度 [0,1]
    val width: Double,        // 【新增】线宽（平均命中段宽度，像素）
    val passed: Boolean       // 是否通过门禁
)

/**
 * X 检测 Debug 信息（增强版：新增更多调试字段）
 */
data class XDetectDebug(
    // 基础信息
    val patchW: Int,
    val patchH: Int,
    val linesTotal: Int,
    val lines45: Int,
    val lines135: Int,

    // 最佳候选线段
    val bestPair45X1: Double?,
    val bestPair45Y1: Double?,
    val bestPair45X2: Double?,
    val bestPair45Y2: Double?,
    val bestPair135X1: Double?,
    val bestPair135Y1: Double?,
    val bestPair135X2: Double?,
    val bestPair135Y2: Double?,

    // 交点信息
    val intersectionX: Double?,
    val intersectionY: Double?,
    val centerDist: Double?,
    val deltaAngle: Double?,       // 两条线夹角与 90° 的差值

    // 四臂验证（核心）
    val armPlus45Cover: Double?,   // +45° 方向覆盖率
    val armMinus45Cover: Double?,  // -45° 方向覆盖率
    val armPlus135Cover: Double?,  // +135° 方向覆盖率
    val armMinus135Cover: Double?, // -135° 方向覆盖率
    val armPlus45Run: Double?,     // +45° 方向最长连续段
    val armMinus45Run: Double?,    // -45° 方向最长连续段
    val armPlus135Run: Double?,    // +135° 方向最长连续段
    val armMinus135Run: Double?,   // -135° 方向最长连续段
    val armPlus45Tightness: Double?,   // +45° 贴线度
    val armMinus45Tightness: Double?,  // -45° 贴线度
    val armPlus135Tightness: Double?,  // +135° 贴线度
    val armMinus135Tightness: Double?, // -135° 贴线度
    val armPassCount: Int,          // 通过门禁的臂数量

    // 各项评分（0-100）
    val arm4Score: Int,            // 四臂综合分
    val centerScore: Int,          // 中心分
    val angleScore: Int,           // 角度分
    val containerScore: Int,       // 容器形状分
    val contrastScore: Int,        // 对比度分
    val diagonalEnergyScore: Int,  // 对角线能量集中度分

    // 【新增】调试信息
    val candidatesSize: Int,              // Stage B 后剩余候选数量
    val diagonalEnergyValid: Boolean,     // 对角线能量特征是否有效
    val diagonalEnergyRawRatio: Double?,  // 原始 inBand/outBand ratio
    val diagonalEnergyInBandCount: Int?,  // 对角线带内边缘点数
    val diagonalEnergyOutBandCount: Int?, // 对角线带外边缘点数
    val diagonalEnergyROIRadius: Double?, // ROI 半径
    val diagonalEnergyBandWidth: Double?, // 带宽
    val geometricFilterStats: Map<String, Int>, // 几何过滤原因统计
    val filterReasons: List<String>,      // 候选被过滤的原因列表

    // 【新增】precision 增强特征
    val widthConsistencyScore: Double,    // 线宽一致性分数 [0,1]
    val meanWidth: Double,                // 四臂平均线宽（像素）
    val stdWidth: Double,                 // 四臂线宽标准差（像素）
    val fitScore: Double,                 // 交点附近二线拟合度 [0,1]
    val fitP80Distance: Double,           // 拟合距离 80 分位数（像素）
    val fitROIPointCount: Int,            // 拟合 ROI 内点数
    val intersectionDist1: Double,        // 交点到线段1的距离（像素）
    val intersectionDist2: Double,        // 交点到线段2的距离（像素）
    val intersectionSupportScore: Double, // 交点支撑度 [0,1]（专杀延长线交点）
    val centerDensity: Double,            // 中心圆形区域 edge 密度 [0,1]
    val ringDensity: Double,              // 外环区域 edge 密度 [0,1]
    val buttonContextScore: Double,       // 按钮上下文分数 [0,1]（区分按钮 X vs 纹理 X）
    val centerCrossNorm45: Double,        // 中心 45° 对角线带内 edge 密度 [0,1]
    val centerCrossNorm135: Double,       // 中心 135° 对角线带内 edge 密度 [0,1]
    val centerCrossEvidence: Double,      // 中心交叉证据 [0,1]（两条对角线密度的最小值）
    val centerCrossThreshold: Double,     // 中心交叉阈值（用于判定）
    val buttonSemanticGateEnabled: Boolean, // 是否启用按钮语义门控
    val buttonContextPenalty: Double,     // 按钮上下文惩罚系数 [0.65, 1]
    val buttonCenterPenalty: Double,      // 按钮中心惩罚系数 [0.90, 1]（轻惩罚）
    val centerLowPenaltyTriggered: Boolean, // 中心极低惩罚是否触发（centerScore <= 10）

    // 【新增】中心窗口 off-band 杂质惩罚
    val edgeTotalInWindow: Int,           // 中心窗口内边缘像素总数
    val edgeInDiagBands: Int,             // 中心窗口内对角线带内边缘像素数
    val offBandRatio: Double,             // off-band 杂质比率 [0,1]
    val offBandPenalty: Double,           // off-band 惩罚系数 [0,1]
    val offBandPenaltyEnabled: Boolean,   // 是否启用 off-band 惩罚
    val offBandEnableReason: String,      // 启用原因：NONE/CLUTTER_HIGH/WEAK_X/BOTH/STRONG_X_BYPASS
    val offBandMixA: Double,              // 混合系数 a（finalScore *= (a + b * penalty)）
    val offBandMixB: Double,              // 混合系数 b
    val strongXBypassTriggered: Boolean,  // 强 X 豁免是否触发

    // 【新增】badFitReject 和 visualStrongX
    val badFitReject: Boolean,            // 坏拟合拒绝（假 X 强杀）
    val visualStrongX: Boolean,           // 视觉强 X（豁免语义惩罚）
    val strongGeomBoost: Boolean,         // 强几何证据加分（保底分）
    val weakArmRescueBoost: Boolean,      // 弱臂救援加分（救 arm 低但其它强的真 X）
    val boostApplied: String,             // 应用的 boost 类型：NONE/STRONG_GEOM/WEAK_ARM_RESCUE
    val boostFloor: Int,                  // boost 保底分数
    val aspectOk: Boolean,                // patch 宽高比是否合理（>=0.75）
    val ringOk: Boolean,                  // ringDensity 是否合理（<=0.22）
    val scoreBeforeBoost: Int,            // strongGeomBoost 前的分数
    val scoreAfterBoost: Int,             // strongGeomBoost 后的分数
    val finalIsXThreshold: Int,           // isX 判定阈值
    val buttonContextPenaltyBefore: Double, // 应用下限前的 buttonContextPenalty
    val buttonContextPenaltyAfter: Double,  // 应用下限后的 buttonContextPenalty
    val finalScoreBeforeReject: Double,   // badFitReject 前的 finalScore
    val finalScoreAfterAll: Double,       // 所有惩罚后的最终 finalScore

    // 其他
    val hasCrossing: Boolean,
    val crossingInBounds: Boolean,
    val usedRescueChannel: Boolean // 是否使用了救援通道
)

data class XDetectResult(
    val score0to100: Int,
    val isX: Boolean,
    val debug: XDetectDebug,
    val elapsedTimeMs: Long = 0
)

/**
 * 检测关闭按钮的 X 符号（修复版：移除硬门禁，提升 recall）
 *
 * 修复要点：
 * 1. 移除所有硬门禁（diagonalEnergy、对称性、tightness 改为软门禁扣分）
 * 2. computeDiagonalEnergyScore 改为 ROI 内统计，降低圆环容器影响
 * 3. verifyArmContinuity 自适应放宽 tightness，以 cover/run 为主
 * 4. 复杂度收紧降级（linesTotal>50 不强制 4 臂，允许 >=3）
 * 5. 增强调试输出
 */
fun detectCloseX(patchBitmap: Bitmap): XDetectResult {
    val startTime = System.currentTimeMillis()

    // 缩放到合理尺寸
    val maxSide = 200
    val scale = min(1.0, maxSide.toDouble() / max(patchBitmap.width, patchBitmap.height).toDouble())
    val patch = if (scale < 1.0) {
        Bitmap.createScaledBitmap(
            patchBitmap,
            (patchBitmap.width * scale).roundToInt().coerceAtLeast(40),
            (patchBitmap.height * scale).roundToInt().coerceAtLeast(40),
            true
        )
    } else patchBitmap

    val src = Mat()
    Utils.bitmapToMat(patch, src)

    val gray = Mat()
    Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

    val w = gray.cols().toDouble()
    val h = gray.rows().toDouble()
    val s = min(w, h)
    val cx = w / 2.0
    val cy = h / 2.0

    // ===== Stage A: 候选生成（主通道 + 救援通道） =====

    val isSmallPatch = s < 80

    // 主通道
    var (edges, lines, group45, group135, usedRescue) = runMainChannel(gray, s, isSmallPatch)

    // 救援通道：低对比度救援
    if (shouldTriggerRescue(lines, edges, w, h)) {
        val rescue = runRescueChannel(gray, s)
        edges = rescue.first
        lines = rescue.second
        group45 = rescue.third
        group135 = rescue.fourth
        usedRescue = true
    }

    fun ang0to180(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        var a = atan2(y2 - y1, x2 - x1) * 180 / Math.PI
        if (a < 0) a += 180.0
        return a
    }

    // 取 topK 候选（K=6）
    val K = 6
    val top45 = group45.sortedByDescending { it.len }.take(min(K, group45.size))
    val top135 = group135.sortedByDescending { it.len }.take(min(K, group135.size))

    if (top45.isEmpty() || top135.isEmpty()) {
        val emptyStats = mapOf(
            "parallel" to 0,
            "outOfBounds" to 0,
            "angleError" to 0,
            "lineTooShort" to 0
        )
        return emptyResult(startTime, edges.cols(), edges.rows(), lines.rows(),
                          group45.size, group135.size, usedRescue,
                          emptyStats)
    }

    // ===== Stage B: 结构验证 =====

    // 计算两条线段（无限延长）的交点
    fun intersection(a: Line, b: Line): Point? {
        val den = (a.x1 - a.x2) * (b.y1 - b.y2) - (a.y1 - a.y2) * (b.x1 - b.x2)
        if (abs(den) < 1e-6) return null

        val px = ((a.x1*a.y2 - a.y1*a.x2) * (b.x1 - b.x2) -
                 (a.x1 - a.x2) * (b.x1*b.y2 - b.y1*b.x2)) / den
        val py = ((a.x1*a.y2 - a.y1*a.x2) * (b.y1 - b.y2) -
                 (a.y1 - a.y2) * (b.x1*b.y2 - b.y1*b.x2)) / den
        return Point(px, py)
    }

    // 计算两条直线的夹角（0-90°）
    fun angleBetweenLines(ang45: Double, ang135: Double): Double {
        val delta = abs(ang135 - ang45)
        return if (delta > 90.0) 180.0 - delta else delta
    }

    /**
     * 【修复】四臂连续性验证：放宽并自适应
     *
     * 改动：
     * 1. tightnessThreshold 自适应（粗线/大 patch 允许 0.30~0.45）
     * 2. passed 规则改为 cover/run 为主，tightness 为辅
     * 3. cover>=0.50 且 run>=0.16*s 即可通过（tightness 仅作为次级门禁）
     */
    fun verifyArmContinuity(
        edges: Mat,
        p: Point,
        dx: Double,
        dy: Double,
        sampLen: Double
    ): ArmVerification {
        val cols = edges.cols()
        val rows = edges.rows()
        val numSamples = max(10, (sampLen / 1.0).roundToInt())

        // 法线方向（垂直于臂方向）
        val nx = -dy  // 法线 X
        val ny = dx   // 法线 Y

        val offsets = mutableListOf<Double>()
        val widths = mutableListOf<Double>()  // 【新增】记录每个采样点的线宽
        var hitCount = 0
        var currentRun = 0
        var maxRun = 0

        val searchRadius = 3  // 法线搜索半径

        for (i in 0 until numSamples) {
            val t = i.toDouble()
            val x = (p.x + t * dx).roundToInt()
            val y = (p.y + t * dy).roundToInt()

            // 【新增】沿法线方向搜索 ±searchRadius 像素，记录线宽
            var found = false
            var minOffset = Double.MAX_VALUE
            var firstEdgeOffset = Double.MAX_VALUE
            var lastEdgeOffset = Double.MIN_VALUE

            for (r in -searchRadius..searchRadius) {
                val xx = (x + r * nx).roundToInt()
                val yy = (y + r * ny).roundToInt()

                if (xx in 0 until cols && yy in 0 until rows) {
                    if (edges.get(yy, xx)[0] > 0) {
                        found = true
                        val offset = abs(r.toDouble())
                        if (offset < minOffset) {
                            minOffset = offset
                        }
                        // 【新增】记录首末 edge 位置
                        if (r.toDouble() < firstEdgeOffset) {
                            firstEdgeOffset = r.toDouble()
                        }
                        if (r.toDouble() > lastEdgeOffset) {
                            lastEdgeOffset = r.toDouble()
                        }
                    }
                }
            }

            if (found) {
                hitCount++
                currentRun++
                if (currentRun > maxRun) {
                    maxRun = currentRun
                }
                offsets.add(minOffset)
                // 【新增】计算线宽（首末 edge 距离）
                val lineWidth = abs(lastEdgeOffset - firstEdgeOffset)
                widths.add(lineWidth)
            } else {
                currentRun = 0
            }
        }

        val cover = hitCount.toDouble() / numSamples
        val longestRun = maxRun.toDouble()

        // 计算 tightness：1 - mean(abs(offset)) / searchRadius
        val tightness = if (offsets.isNotEmpty()) {
            val meanOffset = offsets.average()
            (1.0 - meanOffset / searchRadius).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        // 【新增】计算平均线宽
        val width = if (widths.isNotEmpty()) {
            widths.average()
        } else {
            0.0
        }

        // 【修复】自适应 tightness 阈值
        // 粗线/大 patch：允许更低的 tightness
        val tightnessThreshold = if (s > 120) {
            0.30  // 大 patch 允许更松散
        } else if (s > 80) {
            0.40  // 中等 patch
        } else {
            0.50  // 小 patch 要求更紧
        }

        // 【修复】passed 规则：cover/run 为主，tightness 为辅
        // 主条件：cover >= 0.50 且 run >= 0.16*s
        // 次级门禁：如果主条件通过，tightness 仅作为软门禁（不一票否决）
        val coverThreshold = 0.50  // 放宽从 0.45 到 0.50
        val runThreshold = 0.16 * s  // 放宽从 0.18*s 到 0.16*s

        val mainPassed = (cover >= coverThreshold) && (longestRun >= runThreshold)
        val tightnessPassed = (tightness >= tightnessThreshold)

        // 容错逻辑：只要主条件通过，就允许通过（tightness 仅影响评分）
        val passed = mainPassed

        return ArmVerification(cover, longestRun, tightness, width, passed)
    }

    data class Candidate(
        val line45: Line,
        val line135: Line,
        val intersection: Point,
        val deltaAngle: Double,
        val centerDist: Double,
        val armPlus45: ArmVerification,
        val armMinus45: ArmVerification,
        val armPlus135: ArmVerification,
        val armMinus135: ArmVerification,
        val armPassCount: Int,
        val symmetry45OK: Boolean,    // 45° 对称性（用于评分）
        val symmetry135OK: Boolean,   // 135° 对称性（用于评分）
        val softReasons: List<String> // 软门禁原因（仅用于扣分）
    )

    val candidates = mutableListOf<Candidate>()

    // 几何过滤统计
    val geometricFilterStats = mutableMapOf(
        "parallel" to 0,           // 平行线/无交点
        "outOfBounds" to 0,        // 交点出界
        "angleError" to 0,         // 夹角不在 [70°,110°]
        "lineTooShort" to 0        // 线段太短
    )

    // 【修复】只做必要几何过滤，禁止外观硬门禁
    val minLineLen = 0.12 * s  // 最小线段长度
    val linesTotal = lines.rows()
    val isComplexScene = linesTotal > 50

    for (a in top45) {
        for (b in top135) {
            // 几何过滤 1：线段长度检查
            if (a.len < minLineLen || b.len < minLineLen) {
                geometricFilterStats["lineTooShort"] = geometricFilterStats["lineTooShort"]!! + 1
                continue
            }

            // 几何过滤 2：交点计算
            val p = intersection(a, b)
            if (p == null) {
                geometricFilterStats["parallel"] = geometricFilterStats["parallel"]!! + 1
                continue
            }

            // 几何过滤 3：交点出界检查（允许 10% 出界）
            val boundsMargin = 0.1
            if (p.x !in (w * -boundsMargin)..(w * (1 + boundsMargin)) ||
                p.y !in (h * -boundsMargin)..(h * (1 + boundsMargin))) {
                geometricFilterStats["outOfBounds"] = geometricFilterStats["outOfBounds"]!! + 1
                continue
            }

            // 几何过滤 4：夹角检查（[70°, 110°]，即 angleErr <= 20°）
            val deltaAngle = angleBetweenLines(a.ang, b.ang)
            val angleErr = abs(deltaAngle - 90.0)
            if (angleErr > 20.0) {
                geometricFilterStats["angleError"] = geometricFilterStats["angleError"]!! + 1
                continue
            }

            // ===== 以下全部为软门禁（不过滤，仅用于评分扣分） =====

            // 计算四臂验证（用于评分）
            val sampLen = 0.30 * s

            val dx45 = (a.x2 - a.x1) / a.len
            val dy45 = (a.y2 - a.y1) / a.len
            val dx135 = (b.x2 - b.x1) / b.len
            val dy135 = (b.y2 - b.y1) / b.len

            val armPlus45 = verifyArmContinuity(edges, p, dx45, dy45, sampLen)
            val armMinus45 = verifyArmContinuity(edges, p, -dx45, -dy45, sampLen)
            val armPlus135 = verifyArmContinuity(edges, p, dx135, dy135, sampLen)
            val armMinus135 = verifyArmContinuity(edges, p, -dx135, -dy135, sampLen)

            val armPassCount = listOf(
                armPlus45.passed,
                armMinus45.passed,
                armPlus135.passed,
                armMinus135.passed
            ).count { it }

            // 计算对称性（软门禁，用于评分）
            fun ratio(a: Double, b: Double): Double {
                val eps = 1e-6
                return max(a, b) / max(min(a, b), eps)
            }

            val ratio45Cover = ratio(armPlus45.cover, armMinus45.cover)
            val ratio45Run = ratio(armPlus45.longestRun, armMinus45.longestRun)
            val symmetry45OK = (ratio45Cover in 0.5..2.0 && ratio45Run in 0.5..2.0)

            val ratio135Cover = ratio(armPlus135.cover, armMinus135.cover)
            val ratio135Run = ratio(armPlus135.longestRun, armMinus135.longestRun)
            val symmetry135OK = (ratio135Cover in 0.5..2.0 && ratio135Run in 0.5..2.0)

            // 计算中心距离（软门禁，用于评分）
            val centerDist = hypot(p.x - cx, p.y - cy)

            // 记录软门禁原因（不过滤）
            val softReasons = mutableListOf<String>()
            if (armPassCount < 3) {
                softReasons.add("Arms < 3: $armPassCount")
            }
            if (!symmetry45OK) {
                softReasons.add("Asymmetry 45°")
            }
            if (!symmetry135OK) {
                softReasons.add("Asymmetry 135°")
            }
            if (centerDist > 0.22 * s) {
                softReasons.add("Center dist > 0.22*s")
            }

            // 所有候选都保留，不过滤
            candidates.add(Candidate(
                line45 = a,
                line135 = b,
                intersection = p,
                deltaAngle = deltaAngle,
                centerDist = centerDist,
                armPlus45 = armPlus45,
                armMinus45 = armMinus45,
                armPlus135 = armPlus135,
                armMinus135 = armMinus135,
                armPassCount = armPassCount,
                symmetry45OK = symmetry45OK,
                symmetry135OK = symmetry135OK,
                softReasons = softReasons
            ))
        }
    }

    if (candidates.isEmpty()) {
        return emptyResult(startTime, edges.cols(), edges.rows(), lines.rows(),
                          group45.size, group135.size, usedRescue,
                          geometricFilterStats,
                          top45.firstOrNull(), top135.firstOrNull())
    }

    // ===== Stage C: 形状先验 =====

    /**
     * C1. 检测圆形/圆角矩形容器
     */
    fun detectContainer(edges: Mat): Double {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            edges.clone(),
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        if (contours.isEmpty()) return 0.0

        val maxContour = contours.maxByOrNull { Imgproc.contourArea(it) } ?: return 0.0
        val area = Imgproc.contourArea(maxContour)
        val imageArea = w * h

        if (area < imageArea * 0.15 || area > imageArea * 0.85) return 0.0

        val center = Point()
        val radius = FloatArray(1)
        Imgproc.minEnclosingCircle(MatOfPoint2f(*maxContour.toArray()), center, radius)

        val circleArea = Math.PI * radius[0] * radius[0]
        val circularity = if (circleArea > 0) area / circleArea else 0.0

        return circularity.coerceIn(0.0, 1.0)
    }

    /**
     * C2. 计算对比度分数
     */
    fun computeContrastScore(gray: Mat, p: Point): Double {
        val cols = gray.cols()
        val rows = gray.rows()
        val x = p.x.roundToInt()
        val y = p.y.roundToInt()

        if (x !in 0 until cols || y !in 0 until rows) return 0.0

        var lineSum = 0.0
        var lineCount = 0
        val lineRadius = 3
        for (dy in -lineRadius..lineRadius) {
            for (dx in -lineRadius..lineRadius) {
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until cols && yy in 0 until rows) {
                    lineSum += gray.get(yy, xx)[0]
                    lineCount++
                }
            }
        }
        val lineMean = if (lineCount > 0) lineSum / lineCount else 0.0

        var bgSum = 0.0
        var bgCount = 0
        val bgRadius = (s * 0.1).roundToInt()
        for (i in 0 until 20) {
            val angle = i * Math.PI / 10
            val xx = (cx + cos(angle) * bgRadius).roundToInt()
            val yy = (cy + sin(angle) * bgRadius).roundToInt()
            if (xx in 0 until cols && yy in 0 until rows) {
                bgSum += gray.get(yy, xx)[0]
                bgCount++
            }
        }
        val bgMean = if (bgCount > 0) bgSum / bgCount else 0.0

        val contrast = abs(lineMean - bgMean)
        return (contrast / 128.0).coerceIn(0.0, 1.0)
    }

    /**
     * 【修复】对角线能量集中度：ROI 内统计 + 有效性判定
     *
     * 改动：
     * 1. ROI 半径 = 0.35*s，bandHalf = 0.05*s
     * 2. 有效性判定：(in+out) >= 12 且 out >= 6
     * 3. 若无效，score=0 且不参与评分
     * 4. 若有效，ratio clamp 到 [0,3] 防止虚高
     * 5. 使用双精度坐标计算，确保不会全 0
     */
    fun computeDiagonalEnergyScore(
        edges: Mat,
        p: Point,
        s: Double
    ): Triple<Double, Double, DiagonalEnergyDebug> {
        val cols = edges.cols()
        val rows = edges.rows()

        // ROI 半径：只关注交点附近
        val roiRadius = (0.35 * s).roundToInt()
        val bandHalf = (0.05 * s).coerceAtLeast(2.0)  // 使用双精度

        // 轻度腐蚀，降低圆环容器边缘影响
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0))
        val edgesEroded = Mat()
        Imgproc.erode(edges, edgesEroded, kernel)

        var inBandCount = 0
        var outBandCount = 0

        val roiX1 = (p.x - roiRadius).roundToInt().coerceAtLeast(0)
        val roiY1 = (p.y - roiRadius).roundToInt().coerceAtLeast(0)
        val roiX2 = (p.x + roiRadius).roundToInt().coerceAtMost(cols - 1)
        val roiY2 = (p.y + roiRadius).roundToInt().coerceAtMost(rows - 1)

        for (y in roiY1..roiY2) {
            for (x in roiX1..roiX2) {
                if (edgesEroded.get(y, x)[0] == 0.0) continue

                // 只统计 ROI 内的点（使用双精度距离计算）
                val dx = x.toDouble() - p.x
                val dy = y.toDouble() - p.y
                val distToCenter = hypot(dx, dy)
                if (distToCenter > roiRadius) continue

                // 计算点到两条对角线的距离（使用双精度）
                // 距离 45° 线：|x - y| / sqrt(2)
                val dist45 = abs(dx - dy) / sqrt(2.0)
                // 距离 135° 线：|x + y| / sqrt(2)
                val dist135 = abs(dx + dy) / sqrt(2.0)

                val minDist = min(dist45, dist135)

                if (minDist <= bandHalf) {
                    inBandCount++
                } else {
                    outBandCount++
                }
            }
        }

        // 有效性判定
        val totalCount = inBandCount + outBandCount
        val isValid = (totalCount >= 12) && (outBandCount >= 6)

        val rawRatio: Double
        val normalizedScore: Double

        if (isValid) {
            // 有效：计算 ratio 并 clamp 到 [0, 3]
            val eps = 1.0
            rawRatio = (inBandCount.toDouble() / (outBandCount + eps)).coerceIn(0.0, 3.0)
            // 归一化分数（范围 0-1）
            normalizedScore = (rawRatio / 3.0).coerceIn(0.0, 1.0)
        } else {
            // 无效：ratio 为原始值（供调试），但 score = 0
            rawRatio = if (outBandCount > 0) {
                inBandCount.toDouble() / outBandCount.toDouble()
            } else {
                0.0
            }
            normalizedScore = 0.0
        }

        val debug = DiagonalEnergyDebug(
            isValid = isValid,
            rawRatio = rawRatio,
            roiRadius = roiRadius.toDouble(),
            bandWidth = bandHalf,
            inBandCount = inBandCount,
            outBandCount = outBandCount
        )

        return Triple(normalizedScore, rawRatio, debug)
    }

    val containerScore = detectContainer(edges)

    /**
     * 【修复】计算线宽一致性分数（改为指数形式，更宽容）
     * 真 X 的四臂线宽应该一致；圆环/弧形会导致线宽差异大
     */
    fun computeWidthConsistency(cand: Candidate): Triple<Double, Double, Double> {
        val widths = listOf(
            cand.armPlus45.width,
            cand.armMinus45.width,
            cand.armPlus135.width,
            cand.armMinus135.width
        )

        val meanWidth = widths.average()
        val stdWidth = if (widths.size > 1) {
            val variance = widths.map { (it - meanWidth) * (it - meanWidth) }.average()
            sqrt(variance)
        } else {
            0.0
        }

        // 【修复】改为指数衰减：widthConsistencyScore = exp(- (std/mean)^2)
        val eps = 0.01
        val ratio = stdWidth / (meanWidth + eps)
        val widthConsistencyScore = exp(- ratio * ratio)

        return Triple(widthConsistencyScore, meanWidth, stdWidth)
    }

    /**
     * 【修复】计算交点附近二线拟合度（放宽带宽 + 指数衰减）
     * 真 X 的 edge 点应该紧贴两条理想对角线；碎边缘会偏离
     */
    fun computeIntersectionFitScore(
        edges: Mat,
        p: Point,
        s: Double
    ): Triple<Double, Double, Int> {
        val cols = edges.cols()
        val rows = edges.rows()

        // ROI 半径
        val roiRadius = (0.25 * s).roundToInt()
        // 【修复】放宽带宽：从 0.08*s 改为 0.09*s（大图更宽容）
        val bandHalf = max(4.0, 0.09 * s)

        // 收集 ROI 内的 edge 点
        val distances = mutableListOf<Double>()

        val roiX1 = (p.x - roiRadius).roundToInt().coerceAtLeast(0)
        val roiY1 = (p.y - roiRadius).roundToInt().coerceAtLeast(0)
        val roiX2 = (p.x + roiRadius).roundToInt().coerceAtMost(cols - 1)
        val roiY2 = (p.y + roiRadius).roundToInt().coerceAtMost(rows - 1)

        for (y in roiY1..roiY2) {
            for (x in roiX1..roiX2) {
                if (edges.get(y, x)[0] > 0) {
                    // 计算点到交点的距离
                    val dx = x.toDouble() - p.x
                    val dy = y.toDouble() - p.y
                    val distToCenter = hypot(dx, dy)

                    // 只统计 ROI 内的点
                    if (distToCenter <= roiRadius) {
                        // 计算点到两条理想对角线（45°/135°）的距离
                        // 45° 线：y - p.y = (x - p.x)，即 dy = dx
                        val dist45 = abs(dx - dy) / sqrt(2.0)
                        // 135° 线：y - p.y = -(x - p.x)，即 dy = -dx
                        val dist135 = abs(dx + dy) / sqrt(2.0)

                        val minDist = min(dist45, dist135)
                        distances.add(minDist)
                    }
                }
            }
        }

        val pointCount = distances.size

        // 计算 80 分位数
        val p80Distance = if (distances.isNotEmpty()) {
            distances.sort()
            val idx = (distances.size * 0.80).toInt().coerceIn(0, distances.size - 1)
            distances[idx]
        } else {
            bandHalf  // 默认值
        }

        // 【修复】计算拟合分数：改为指数衰减（更宽容系数 0.6）
        // fitScore = exp(-0.6 * (p80 / bandHalf)^2)，范围自然在 (0,1]
        val fitScore = exp(-0.6 * (p80Distance / bandHalf) * (p80Distance / bandHalf))

        return Triple(fitScore, p80Distance, pointCount)
    }

    /**
     * 【新增】计算交点支撑度（专杀延长线交点的 not_x）
     * 真 X 的交点应该在两条线段上或附近；延长线交点距离线段远
     */
    fun computeIntersectionSupportScore(
        p: Point,
        line1: Line,
        line2: Line,
        s: Double
    ): Triple<Double, Double, Double> {
        // 计算点到线段的距离（不是到无限延长线）
        fun pointToSegmentDistance(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
            val dx = x2 - x1
            val dy = y2 - y1
            val lengthSq = dx * dx + dy * dy

            if (lengthSq < 1e-9) {
                // 线段退化为点
                return hypot(px - x1, py - y1)
            }

            // 计算投影参数 t（点在线段上的投影位置）
            var t = ((px - x1) * dx + (py - y1) * dy) / lengthSq
            t = t.coerceIn(0.0, 1.0)  // 限制在线段范围内

            // 投影点坐标
            val projX = x1 + t * dx
            val projY = y1 + t * dy

            return hypot(px - projX, py - projY)
        }

        val dist1 = pointToSegmentDistance(p.x, p.y, line1.x1, line1.y1, line1.x2, line1.y2)
        val dist2 = pointToSegmentDistance(p.x, p.y, line2.x1, line2.y1, line2.x2, line2.y2)

        // supportScore = exp(-(dist1+dist2)/(0.04*s))
        // 真 X：交点在线段上或附近，dist1+dist2 ≈ 0，supportScore ≈ 1
        // not_x：交点是延长线交点，dist1+dist2 大，supportScore 低
        val threshold = 0.04 * s
        val supportScore = exp(-(dist1 + dist2) / threshold).coerceIn(0.0, 1.0)

        return Triple(dist1, dist2, supportScore)
    }

    /**
     * 【新增】计算按钮上下文分数（区分按钮 X vs 内容纹理 X）
     * 按钮型 X：中心有 X，周围环形区域边缘稀疏（背景或简单边框）
     * 纹理型 X：中心有 X，周围环形区域边缘密集（图片内容、文字等）
     */
    fun computeButtonContextScore(
        edges: Mat,
        p: Point,
        s: Double
    ): Triple<Double, Double, Double> {
        val cols = edges.cols()
        val rows = edges.rows()

        // 中心圆形区域：半径 0.1*s
        val centerRadius = 0.1 * s
        var centerEdgeCount = 0
        var centerTotalCount = 0

        // 外环区域：半径 [0.3*s, 0.45*s]
        val ringInnerRadius = 0.3 * s
        val ringOuterRadius = 0.45 * s
        var ringEdgeCount = 0
        var ringTotalCount = 0

        // 扫描矩形区域（包含外环）
        val roiX1 = (p.x - ringOuterRadius).roundToInt().coerceAtLeast(0)
        val roiY1 = (p.y - ringOuterRadius).roundToInt().coerceAtLeast(0)
        val roiX2 = (p.x + ringOuterRadius).roundToInt().coerceAtMost(cols - 1)
        val roiY2 = (p.y + ringOuterRadius).roundToInt().coerceAtMost(rows - 1)

        for (y in roiY1..roiY2) {
            for (x in roiX1..roiX2) {
                val dx = x.toDouble() - p.x
                val dy = y.toDouble() - p.y
                val dist = hypot(dx, dy)

                val isEdge = edges.get(y, x)[0] > 0

                // 中心圆形区域
                if (dist <= centerRadius) {
                    centerTotalCount++
                    if (isEdge) centerEdgeCount++
                }

                // 外环区域
                if (dist >= ringInnerRadius && dist <= ringOuterRadius) {
                    ringTotalCount++
                    if (isEdge) ringEdgeCount++
                }
            }
        }

        // 计算密度
        val centerDensity = if (centerTotalCount > 0) {
            centerEdgeCount.toDouble() / centerTotalCount.toDouble()
        } else {
            0.0
        }

        val ringDensity = if (ringTotalCount > 0) {
            ringEdgeCount.toDouble() / ringTotalCount.toDouble()
        } else {
            0.0
        }

        // 计算上下文分数
        // 按钮型 X：ringDensity 低 → contextScore 高
        // 纹理型 X：ringDensity 高 → contextScore 低
        val eps = 0.01
        val contextScore = (1.0 - ringDensity / (centerDensity + eps)).coerceIn(0.0, 1.0)

        return Triple(centerDensity, ringDensity, contextScore)
    }

    /**
     * 【新增】计算中心交叉证据（自然压制"arm/angle 很像但中心不交叉"的假阳性）
     * 真 X：中心交叉区域在两条对角线带内都有明显边缘像素
     * 假阳性：arm/angle 很像，但中心缺乏真正的交叉，对角线带内边缘密度低
     */
    fun computeCenterCrossEvidence(
        edges: Mat,
        p: Point,
        s: Double
    ): Quadruple<Double, Double, Double, Double> {
        val cols = edges.cols()
        val rows = edges.rows()

        // 窗口半径：0.18*s
        val windowRadius = 0.18 * s

        // 对角线带宽：0.06*s
        val bandWidth = 0.06 * s
        val bandHalfWidth = bandWidth / 2.0

        var count45Total = 0
        var count45Edge = 0
        var count135Total = 0
        var count135Edge = 0

        // 扫描矩形区域（包含窗口）
        val roiX1 = (p.x - windowRadius).roundToInt().coerceAtLeast(0)
        val roiY1 = (p.y - windowRadius).roundToInt().coerceAtLeast(0)
        val roiX2 = (p.x + windowRadius).roundToInt().coerceAtMost(cols - 1)
        val roiY2 = (p.y + windowRadius).roundToInt().coerceAtMost(rows - 1)

        for (y in roiY1..roiY2) {
            for (x in roiX1..roiX2) {
                val dx = x.toDouble() - p.x
                val dy = y.toDouble() - p.y
                val dist = hypot(dx, dy)

                // 只统计窗口内的点
                if (dist > windowRadius) continue

                val isEdge = edges.get(y, x)[0] > 0

                // 45° 对角线：dx = dy，即 dx - dy = 0
                // 点到 45° 线的距离：|dx - dy| / sqrt(2)
                val dist45 = abs(dx - dy) / sqrt(2.0)
                if (dist45 <= bandHalfWidth) {
                    count45Total++
                    if (isEdge) count45Edge++
                }

                // 135° 对角线：dx = -dy，即 dx + dy = 0
                // 点到 135° 线的距离：|dx + dy| / sqrt(2)
                val dist135 = abs(dx + dy) / sqrt(2.0)
                if (dist135 <= bandHalfWidth) {
                    count135Total++
                    if (isEdge) count135Edge++
                }
            }
        }

        // 计算密度
        val norm45 = if (count45Total > 0) {
            count45Edge.toDouble() / count45Total.toDouble()
        } else {
            0.0
        }

        val norm135 = if (count135Total > 0) {
            count135Edge.toDouble() / count135Total.toDouble()
        } else {
            0.0
        }

        // 中心交叉证据：两条对角线密度的最小值
        val centerCrossEvidence = min(norm45, norm135)

        // 阈值：自适应（小 patch 要求更高，大 patch 更宽容）
        val threshold = if (s < 80) {
            0.25  // 小 patch 要求 25% 边缘密度
        } else {
            0.20  // 大 patch 要求 20% 边缘密度
        }

        return Quadruple(norm45, norm135, centerCrossEvidence, threshold)
    }

    /**
     * 【新增】计算中心窗口 off-band 杂质惩罚（降低文字/数字/纹理误判）
     * 真 X：中心窗口内边缘像素主要在对角线带内，off-band 杂质少
     * 误判（文字/数字/纹理）：中心窗口内边缘像素大量分布在对角线带外
     */
    fun computeOffBandClutterPenalty(
        edges: Mat,
        p: Point,
        s: Double
    ): Triple<Int, Int, Double> {
        val cols = edges.cols()
        val rows = edges.rows()

        // 复用中心窗口半径：0.18*s（与 centerCrossEvidence 一致）
        val windowRadius = 0.18 * s

        // 对角线带宽：0.06*s（与 centerCrossEvidence 一致）
        val bandWidth = 0.06 * s
        val bandHalfWidth = bandWidth / 2.0

        var edgeTotalInWindow = 0
        var edgeInDiagBands = 0

        // 扫描矩形区域（包含窗口）
        val roiX1 = (p.x - windowRadius).roundToInt().coerceAtLeast(0)
        val roiY1 = (p.y - windowRadius).roundToInt().coerceAtLeast(0)
        val roiX2 = (p.x + windowRadius).roundToInt().coerceAtMost(cols - 1)
        val roiY2 = (p.y + windowRadius).roundToInt().coerceAtMost(rows - 1)

        for (y in roiY1..roiY2) {
            for (x in roiX1..roiX2) {
                val dx = x.toDouble() - p.x
                val dy = y.toDouble() - p.y
                val dist = hypot(dx, dy)

                // 只统计窗口内的点
                if (dist > windowRadius) continue

                val isEdge = edges.get(y, x)[0] > 0

                if (isEdge) {
                    edgeTotalInWindow++

                    // 判断是否在对角线带内
                    // 点到 45° 线的距离：|dx - dy| / sqrt(2)
                    val dist45 = abs(dx - dy) / sqrt(2.0)
                    // 点到 135° 线的距离：|dx + dy| / sqrt(2)
                    val dist135 = abs(dx + dy) / sqrt(2.0)

                    // 在任一对角线带内即计入
                    if (dist45 <= bandHalfWidth || dist135 <= bandHalfWidth) {
                        edgeInDiagBands++
                    }
                }
            }
        }

        // 计算 off-band ratio（带外杂质比率）
        val offBandRatio = if (edgeTotalInWindow > 0) {
            (edgeTotalInWindow - edgeInDiagBands).toDouble() / edgeTotalInWindow.toDouble()
        } else {
            0.0
        }

        return Triple(edgeTotalInWindow, edgeInDiagBands, offBandRatio)
    }

    // ===== 步骤4: 选择最佳候选并计算评分 =====

    val bestCandidate = candidates.maxByOrNull { cand ->
        val avgCover = (cand.armPlus45.cover + cand.armMinus45.cover +
                       cand.armPlus135.cover + cand.armMinus135.cover) / 4.0
        val avgRun = (cand.armPlus45.longestRun + cand.armMinus45.longestRun +
                     cand.armPlus135.longestRun + cand.armMinus135.longestRun) / 4.0

        // 【修复】tightness 作为软门禁，不影响主评分，仅用于微调
        val avgTightness = (cand.armPlus45.tightness + cand.armMinus45.tightness +
                           cand.armPlus135.tightness + cand.armMinus135.tightness) / 4.0

        val arm4Score = 0.7 * avgCover + 0.3 * (avgRun / (0.25 * s)).coerceIn(0.0, 1.0)

        val centerScore = (1.0 - cand.centerDist / (0.22 * s)).coerceIn(0.0, 1.0)
        val angleScore = (1.0 - abs(cand.deltaAngle - 90.0) / 15.0).coerceIn(0.0, 1.0)

        // 【修复】降低复杂场景的 arm 权重，但不强制
        if (isComplexScene) {
            0.45 * arm4Score + 0.20 * centerScore + 0.15 * angleScore +
            0.10 * containerScore + 0.05 * avgTightness
        } else {
            0.60 * arm4Score + 0.20 * centerScore + 0.15 * angleScore +
            0.05 * containerScore
        }
    }!!

    val best45 = bestCandidate.line45
    val best135 = bestCandidate.line135
    val p = bestCandidate.intersection

    // 计算对比度分数
    val contrastScore = computeContrastScore(gray, p)

    // 【修复】对角线能量集中度：改为软门禁，不一票否决
    val (diagonalEnergyScore, diagonalEnergyRawRatio, diagonalEnergyDebug) =
        computeDiagonalEnergyScore(edges, p, s)

    // 【移除硬门禁】不再 return emptyResult
    // 原代码：if (diagonalEnergyScore < 0.3) return emptyResult(...)

    // 【新增】计算线宽一致性（precision 强区分特征）
    val (widthConsistencyScore, meanWidth, stdWidth) = computeWidthConsistency(bestCandidate)

    // 【新增】计算交点拟合度（precision 强区分特征）
    val (fitScore, fitP80Distance, fitROIPointCount) = computeIntersectionFitScore(edges, p, s)

    // 【新增】计算交点支撑度（专杀延长线交点的 not_x）
    val (intersectionDist1, intersectionDist2, intersectionSupportScore) =
        computeIntersectionSupportScore(p, best45, best135, s)

    // 【新增】计算按钮上下文分数（区分按钮 X vs 内容纹理 X）
    val (centerDensity, ringDensity, buttonContextScore) =
        computeButtonContextScore(edges, p, s)

    // 【新增】计算中心交叉证据（自然压制"arm/angle 很像但中心不交叉"的假阳性）
    val (centerCrossNorm45, centerCrossNorm135, centerCrossEvidence, centerCrossThreshold) =
        computeCenterCrossEvidence(edges, p, s)

    // 【新增】计算中心窗口 off-band 杂质惩罚（降低文字/数字/纹理误判）
    val (edgeTotalInWindow, edgeInDiagBands, offBandRatio) =
        computeOffBandClutterPenalty(edges, p, s)

    // ===== 最终评分：软门禁扣分 =====
    val avgCover = (bestCandidate.armPlus45.cover + bestCandidate.armMinus45.cover +
                   bestCandidate.armPlus135.cover + bestCandidate.armMinus135.cover) / 4.0
    val avgRun = (bestCandidate.armPlus45.longestRun + bestCandidate.armMinus45.longestRun +
                 bestCandidate.armPlus135.longestRun + bestCandidate.armMinus135.longestRun) / 4.0
    val avgTightness = (bestCandidate.armPlus45.tightness + bestCandidate.armMinus45.tightness +
                       bestCandidate.armPlus135.tightness + bestCandidate.armMinus135.tightness) / 4.0

    // 基础 arm4Score
    var arm4ScoreRaw = 0.7 * avgCover + 0.3 * (avgRun / (0.25 * s)).coerceIn(0.0, 1.0)

    // 【修复】软门禁 1：非线性 armPassCount 扣分（强化区分度）
    val armPassCountPenalty = when (bestCandidate.armPassCount) {
        4 -> 1.0      // 四臂全过：无扣分
        3 -> 0.70     // 三臂通过：强扣分 30%
        else -> 0.40  // <=2 臂：重扣分 60%
    }
    arm4ScoreRaw *= armPassCountPenalty

    // 【修复】软门禁 2：连续对称性扣分（而非固定 0.10）
    // 计算对称性偏离度（ratio 越接近 1.0 越对称）
    fun ratio(a: Double, b: Double): Double {
        val eps = 1e-6
        return max(a, b) / max(min(a, b), eps)
    }

    val ratio45Cover = ratio(bestCandidate.armPlus45.cover, bestCandidate.armMinus45.cover)
    val ratio45Run = ratio(bestCandidate.armPlus45.longestRun, bestCandidate.armMinus45.longestRun)
    val ratio135Cover = ratio(bestCandidate.armPlus135.cover, bestCandidate.armMinus135.cover)
    val ratio135Run = ratio(bestCandidate.armPlus135.longestRun, bestCandidate.armMinus135.longestRun)

    // 对称性得分：ratio 在 [0.5, 2.0] 内得 1.0，超出范围线性降低
    fun symmetryScore(r: Double): Double {
        return if (r in 0.5..2.0) {
            1.0
        } else if (r < 0.5) {
            (r / 0.5).coerceIn(0.0, 1.0)
        } else {
            (2.0 / r).coerceIn(0.0, 1.0)
        }
    }

    val sym45 = (symmetryScore(ratio45Cover) + symmetryScore(ratio45Run)) / 2.0
    val sym135 = (symmetryScore(ratio135Cover) + symmetryScore(ratio135Run)) / 2.0
    val avgSymmetry = (sym45 + sym135) / 2.0  // [0, 1]

    // 对称性扣分：avgSymmetry 低时扣分多
    arm4ScoreRaw *= (0.6 + 0.4 * avgSymmetry)  // 最多扣 40%

    var arm4Score = arm4ScoreRaw.coerceIn(0.0, 1.0)

    val centerScore = (1.0 - bestCandidate.centerDist / (0.22 * s)).coerceIn(0.0, 1.0)
    val angleScore = (1.0 - abs(bestCandidate.deltaAngle - 90.0) / 20.0).coerceIn(0.0, 1.0)

    // 【新增】计算整数版本分数（用于后续条件判断）
    val arm4ScoreInt = (arm4Score * 100).roundToInt()
    val centerScoreInt = (centerScore * 100).roundToInt()
    val angleScoreInt = (angleScore * 100).roundToInt()

    // 【修复】arm4Score 兜底：为灰色/粗线真 X 提供保底分（避免 x_5 被 arm 拉死）
    // 条件：几何约束极强 + 有足够线段，说明是真 X 但 arm 验证可能失败
    // 从 centerScore>=85 提升到 >=90，保底分从 45 提升到 50
    if (centerScore >= 0.90 && angleScore >= 0.95 && group45.size >= 3 && group135.size >= 3) {
        arm4Score = max(arm4Score, 0.50)  // 保底 50 分
    }

    // 【修复】动态权重 + 降低 diagonalEnergy 权重到 0.01~0.02（暂时禁用不稳定特征）
    var finalScore = if (isComplexScene) {
        0.50 * arm4Score + 0.15 * centerScore + 0.10 * angleScore +
        0.10 * containerScore + 0.08 * contrastScore + 0.01 * diagonalEnergyScore +
        0.02 * avgTightness
    } else {
        0.55 * arm4Score + 0.20 * centerScore + 0.15 * angleScore +
        0.05 * containerScore + 0.03 * contrastScore + 0.01 * diagonalEnergyScore
    }

    // 【修复】应用 precision 轻扣分项：线宽一致性（降低权重避免误伤真 X）
    // widthConsistency 低时轻度压分（真 X 线宽一致，圆环/弧线宽度不一致）
    finalScore *= (0.85 + 0.15 * widthConsistencyScore)

    // 【修复】应用 precision 轻权重项：交点拟合度（进一步降低权重）
    // fitScore 低时轻度压分（真 X 边缘点紧贴对角线，碎边缘/点阵偏离大）
    finalScore *= (0.85 + 0.15 * fitScore)

    // 【新增】应用交点支撑度：专杀延长线交点的 not_x
    // supportScore 低时强烈压分（真 X 交点在线段上，延长线交点距离线段远）
    finalScore *= (0.7 + 0.3 * intersectionSupportScore)

    // 【新增】应用按钮上下文约束：区分按钮 X vs 内容纹理 X（仅对高分启用）
    // 计算 rawScore（惩罚前的分数）
    val rawScore = (finalScore * 100.0).roundToInt().coerceIn(0, 100)

    // 仅当 rawScore >= 60 时启用上下文惩罚（避免误伤低分真 X）
    if (rawScore >= 60) {
        // 按钮型 X：ringDensity 低 → contextScore 高 → 分数基本不变
        // 纹理型 X：ringDensity 高 → contextScore 低 → 显著降分
        finalScore *= (0.6 + 0.4 * buttonContextScore)
    }

    // 【新增】召回友好的中心窗口 off-band 杂质惩罚
    // 策略：只在"疑似误判场景"启用，强 X 豁免（带拟合硬门槛）

    // 计算惩罚系数（统一计算，后面决定是否启用）
    // 使用更缓和的指数 0.5（原 0.8），让真 X 在 offBandRatio 稍大时不掉太多分
    val offBandPenalty = Math.pow(1.0 - offBandRatio, 0.5).coerceIn(0.0, 1.0)

    // 前置条件：rawScore >= 60（沿用原逻辑）
    var offBandPenaltyEnabled = false
    var offBandEnableReason = "NONE"
    var strongXBypassTriggered = false
    var offBandMixA = 0.0
    var offBandMixB = 0.0

    if (rawScore >= 60) {
        // 【步骤1】检查"中心太吵强制启用"（优先级最高，无法被 bypass 覆盖）
        val isCenterTooNoisy = (fitROIPointCount >= 520) || (edgeTotalInWindow >= 400)

        if (isCenterTooNoisy) {
            // 强制启用：中心窗口杂质过多，必须压制
            offBandPenaltyEnabled = true
            offBandEnableReason = "FORCE_CENTER_NOISY"
            // 强制启用场景：重度惩罚
            offBandMixA = 0.65
            offBandMixB = 0.35
        } else {
            // 【步骤2】检查"强 X 豁免"（增加拟合硬门槛）
            val isStrongX = (intersectionSupportScore >= 0.98) &&
                            (angleScore >= 0.95) &&
                            (fitScore >= 0.78) &&
                            (fitP80Distance <= 7.0) &&
                            (fitROIPointCount <= 420) &&
                            (centerCrossEvidence >= centerCrossThreshold + 0.02)

            if (isStrongX) {
                // 强 X：不启用惩罚
                offBandPenaltyEnabled = false
                offBandEnableReason = "STRONG_X_BYPASS"
                strongXBypassTriggered = true
            } else {
                // 【步骤3】检查"疑似误判场景"（满足条件A或B才启用）

                // 条件A：中心窗口边缘异常多（纹理/文字特征）
                // 自适应阈值：小 patch 要求更高密度，大 patch 可以绝对数量
                val clutterThreshold = if (s < 100) {
                    max(180.0, s * 1.8)  // 小图：至少 180 或 1.8*s
                } else {
                    max(200.0, s * 1.5)  // 大图：至少 200 或 1.5*s
                }
                val isClutterHigh = (edgeTotalInWindow >= clutterThreshold)

                // 条件B：X 证据偏弱（对角线拟合不够好 或 中心交叉证据不足）
                val isWeakX = (centerCrossEvidence <= centerCrossThreshold + 0.03) ||
                              (fitScore < 0.70)

                if (isClutterHigh && isWeakX) {
                    // 同时满足 A+B：启用正常惩罚
                    offBandPenaltyEnabled = true
                    offBandEnableReason = "BOTH"
                    // 普通启用场景：中度惩罚
                    offBandMixA = 0.82
                    offBandMixB = 0.18
                } else if (isClutterHigh) {
                    // 只满足 A（高杂质）：启用轻度惩罚
                    offBandPenaltyEnabled = true
                    offBandEnableReason = "CLUTTER_HIGH"
                    // 普通启用场景：中度惩罚
                    offBandMixA = 0.82
                    offBandMixB = 0.18
                } else if (isWeakX) {
                    // 只满足 B（弱 X）：启用中度惩罚
                    offBandPenaltyEnabled = true
                    offBandEnableReason = "WEAK_X"
                    // 普通启用场景：中度惩罚
                    offBandMixA = 0.82
                    offBandMixB = 0.18
                }
            }
        }
    }

    // 应用惩罚
    if (offBandPenaltyEnabled) {
        finalScore *= (offBandMixA + offBandMixB * offBandPenalty)
    }

    // 【新增】步骤1：轻 centerPenalty（全局应用，避免恒定 ~0.75）
    // centerPenalty = 0.90 + 0.10 * (centerScore/100.0), clamp [0.90, 1.0]
    // not_x_10: centerScore≈0 → penalty=0.90 (降分 10%)
    // 真 X: centerScore≈80+ → penalty≈0.98+ (降分 <2%)
    val centerScoreNorm = centerScoreInt / 100.0  // 确保只除一次 100，Double 计算
    val buttonCenterPenalty = (0.90 + 0.10 * centerScoreNorm).coerceIn(0.90, 1.0)
    finalScore *= buttonCenterPenalty

    // 【新增】步骤2："中心极低"单独轻惩罚（不影响真 X）
    // 真 X 的 centerScore 一般 >30，此惩罚仅针对极端假阳性
    val centerLowPenaltyTriggered = (centerScoreInt <= 10)
    if (centerLowPenaltyTriggered) {
        finalScore *= 0.85  // 降分 15%
    }

    // 【新增】步骤3：Button Semantic Gate（用 intersectionSupportScore 代替 arm 门槛）
    // gateEnabled = (intersectionSupportScore >= 0.95 && angleScore >= 90)
    val buttonSemanticGateEnabled = (intersectionSupportScore >= 0.95 && angleScoreInt >= 90)

    // 【新增】检测 visualStrongX（视觉强 X，豁免语义惩罚）
    val visualStrongX = (intersectionSupportScore >= 0.98) &&
                        (angleScoreInt >= 95) &&
                        (centerCrossEvidence >= (centerCrossThreshold - 0.01)) &&
                        (group45.size >= 2) &&
                        (group135.size >= 2)

    // 【新增】检测 badFitReject（坏拟合拒绝，专杀文字/纹理假 X）
    // 优先级最高，一票否决
    val badFitReject = ((fitScore < 0.62) && (fitP80Distance > 10.0) && (fitROIPointCount > 600)) ||
                       ((fitP80Distance > 12.0) && (fitROIPointCount > 500))

    // 【新增】计算防纹理门禁条件
    val patchW = edges.cols()
    val patchH = edges.rows()
    val aspectOk = (min(patchW, patchH).toDouble() / max(patchW, patchH).toDouble()) >= 0.75
    val ringOk = ringDensity <= 0.22

    // 【新增】检测 strongGeomBoost（强几何证据，保底分）
    // 收紧条件：加入防纹理门禁
    val strongGeomBoostBase = (intersectionSupportScore >= 0.98) &&
                              (angleScoreInt >= 95) &&
                              (arm4ScoreInt >= 75) &&
                              (group45.size >= 2) &&
                              (group135.size >= 2) &&
                              (centerCrossEvidence >= (centerCrossThreshold - 0.02))
    val strongGeomBoost = strongGeomBoostBase && aspectOk && ringOk

    // 【新增】检测 weakArmRescueBoost（弱臂救援，救 arm 低但其它强的真 X）
    val weakArmRescueBoost = (intersectionSupportScore >= 0.98) &&
                             (angleScoreInt >= 95) &&
                             (centerScoreInt >= 85) &&
                             (centerCrossEvidence >= (centerCrossThreshold - 0.01)) &&
                             aspectOk &&
                             ringOk

    var buttonContextPenaltyBefore = 1.0
    var buttonContextPenaltyAfter = 1.0

    if (buttonSemanticGateEnabled) {
        // 计算原始 contextPenalty
        buttonContextPenaltyBefore = (0.65 + 0.35 * buttonContextScore).coerceIn(0.65, 1.0)

        // 【新增】visualStrongX 豁免：钳制下限到 0.88
        if (visualStrongX && !badFitReject) {
            // 强视觉 X 但语义弱：豁免重惩罚
            buttonContextPenaltyAfter = max(buttonContextPenaltyBefore, 0.88)
        } else if (buttonContextScore == 0.0 && visualStrongX && !badFitReject) {
            // buttonContextScore == 0 的强视觉 X：也应用下限
            buttonContextPenaltyAfter = 0.88
        } else {
            // 正常场景：使用原始 penalty
            buttonContextPenaltyAfter = buttonContextPenaltyBefore
        }

        // gate 内只应用 contextPenalty，不再使用重 centerPenalty（已在步骤1应用轻版本）
        finalScore *= buttonContextPenaltyAfter
    } else {
        // 未启用门控，无额外惩罚
        buttonContextPenaltyBefore = 1.0
        buttonContextPenaltyAfter = 1.0
    }

    // 记录 badFitReject 前的分数
    val finalScoreBeforeReject = finalScore

    val finalScoreAfterAll = finalScore

    // 计算初步分数（0-100）
    var score = (finalScore * 100.0).roundToInt().coerceIn(0, 100)

    // 记录 boost 前的分数
    val scoreBeforeBoost = score

    // 【新增】应用 boost（保底分数制，不封顶）
    var boostApplied = "NONE"
    var boostFloor = 0

    if (!badFitReject) {
        // 优先检查 strongGeomBoost（保底分更高）
        if (strongGeomBoost) {
            boostFloor = 72  // 强几何保底 72 分
            score = max(score, boostFloor)
            boostApplied = "STRONG_GEOM"
        } else if (weakArmRescueBoost) {
            boostFloor = 68  // 弱臂救援保底 68 分
            score = max(score, boostFloor)
            boostApplied = "WEAK_ARM_RESCUE"
        }
    }

    // 【新增】应用 badFitReject 硬惩罚（一票否决，最高优先级）
    if (badFitReject) {
        // 坏拟合强杀：clamp 到 33 分以下
        score = min(score, 33)
    }

    val scoreAfterBoost = score

    // 判定阈值
    val finalIsXThreshold = 55

    // 【修复】最终判定
    val isX = if (badFitReject) {
        false  // badFitReject 一票否决
    } else {
        score >= finalIsXThreshold
    }

    val dbg = XDetectDebug(
        patchW = edges.cols(),
        patchH = edges.rows(),
        linesTotal = lines.rows(),
        lines45 = group45.size,
        lines135 = group135.size,
        bestPair45X1 = best45.x1,
        bestPair45Y1 = best45.y1,
        bestPair45X2 = best45.x2,
        bestPair45Y2 = best45.y2,
        bestPair135X1 = best135.x1,
        bestPair135Y1 = best135.y1,
        bestPair135X2 = best135.x2,
        bestPair135Y2 = best135.y2,
        intersectionX = p.x,
        intersectionY = p.y,
        centerDist = bestCandidate.centerDist,
        deltaAngle = abs(bestCandidate.deltaAngle - 90.0),
        armPlus45Cover = bestCandidate.armPlus45.cover,
        armMinus45Cover = bestCandidate.armMinus45.cover,
        armPlus135Cover = bestCandidate.armPlus135.cover,
        armMinus135Cover = bestCandidate.armMinus135.cover,
        armPlus45Run = bestCandidate.armPlus45.longestRun,
        armMinus45Run = bestCandidate.armMinus45.longestRun,
        armPlus135Run = bestCandidate.armPlus135.longestRun,
        armMinus135Run = bestCandidate.armMinus135.longestRun,
        armPlus45Tightness = bestCandidate.armPlus45.tightness,
        armMinus45Tightness = bestCandidate.armMinus45.tightness,
        armPlus135Tightness = bestCandidate.armPlus135.tightness,
        armMinus135Tightness = bestCandidate.armMinus135.tightness,
        armPassCount = bestCandidate.armPassCount,
        arm4Score = (arm4Score * 100).roundToInt(),
        centerScore = (centerScore * 100).roundToInt(),
        angleScore = (angleScore * 100).roundToInt(),
        containerScore = (containerScore * 100).roundToInt(),
        contrastScore = (contrastScore * 100).roundToInt(),
        diagonalEnergyScore = (diagonalEnergyScore * 100).roundToInt(),
        candidatesSize = candidates.size,
        diagonalEnergyValid = diagonalEnergyDebug.isValid,
        diagonalEnergyRawRatio = diagonalEnergyRawRatio,
        diagonalEnergyInBandCount = diagonalEnergyDebug.inBandCount,
        diagonalEnergyOutBandCount = diagonalEnergyDebug.outBandCount,
        diagonalEnergyROIRadius = diagonalEnergyDebug.roiRadius,
        diagonalEnergyBandWidth = diagonalEnergyDebug.bandWidth,
        geometricFilterStats = geometricFilterStats,
        filterReasons = bestCandidate.softReasons,
        widthConsistencyScore = widthConsistencyScore,
        meanWidth = meanWidth,
        stdWidth = stdWidth,
        fitScore = fitScore,
        fitP80Distance = fitP80Distance,
        fitROIPointCount = fitROIPointCount,
        intersectionDist1 = intersectionDist1,
        intersectionDist2 = intersectionDist2,
        intersectionSupportScore = intersectionSupportScore,
        centerDensity = centerDensity,
        ringDensity = ringDensity,
        buttonContextScore = buttonContextScore,
        centerCrossNorm45 = centerCrossNorm45,
        centerCrossNorm135 = centerCrossNorm135,
        centerCrossEvidence = centerCrossEvidence,
        centerCrossThreshold = centerCrossThreshold,
        buttonSemanticGateEnabled = buttonSemanticGateEnabled,
        buttonContextPenalty = buttonContextPenaltyAfter,
        buttonCenterPenalty = buttonCenterPenalty,
        centerLowPenaltyTriggered = centerLowPenaltyTriggered,
        edgeTotalInWindow = edgeTotalInWindow,
        edgeInDiagBands = edgeInDiagBands,
        offBandRatio = offBandRatio,
        offBandPenalty = offBandPenalty,
        offBandPenaltyEnabled = offBandPenaltyEnabled,
        offBandEnableReason = offBandEnableReason,
        offBandMixA = offBandMixA,
        offBandMixB = offBandMixB,
        strongXBypassTriggered = strongXBypassTriggered,
        badFitReject = badFitReject,
        visualStrongX = visualStrongX,
        strongGeomBoost = strongGeomBoost,
        weakArmRescueBoost = weakArmRescueBoost,
        boostApplied = boostApplied,
        boostFloor = boostFloor,
        aspectOk = aspectOk,
        ringOk = ringOk,
        scoreBeforeBoost = scoreBeforeBoost,
        scoreAfterBoost = scoreAfterBoost,
        finalIsXThreshold = finalIsXThreshold,
        buttonContextPenaltyBefore = buttonContextPenaltyBefore,
        buttonContextPenaltyAfter = buttonContextPenaltyAfter,
        finalScoreBeforeReject = finalScoreBeforeReject,
        finalScoreAfterAll = finalScoreAfterAll,
        hasCrossing = true,
        crossingInBounds = (p.x in 0.0..w && p.y in 0.0..h),
        usedRescueChannel = usedRescue
    )

    val elapsedTime = System.currentTimeMillis() - startTime
    return XDetectResult(score, isX, dbg, elapsedTime)
}

/**
 * 【保留】主通道：标准 Canny + Hough
 */
private fun runMainChannel(
    gray: Mat,
    s: Double,
    isSmallPatch: Boolean
): Quintuple<Mat, Mat, List<Line>, List<Line>, Boolean> {
    val grayProcessed = gray.clone()

    if (isSmallPatch) {
        Imgproc.equalizeHist(grayProcessed, grayProcessed)
    }

    val blur = Mat()
    Imgproc.GaussianBlur(grayProcessed, blur, Size(3.0, 3.0), 0.0)

    val edges = Mat()
    val (cannyLow, cannyHigh) = if (isSmallPatch) {
        20.0 to 60.0
    } else {
        50.0 to 150.0
    }
    Imgproc.Canny(blur, edges, cannyLow, cannyHigh)

    val minLen = if (isSmallPatch) {
        max(7.0, s * 0.14)
    } else {
        s * 0.25
    }
    val houghThreshold = if (isSmallPatch) 18 else 25

    val lines = Mat()
    Imgproc.HoughLinesP(
        edges,
        lines,
        1.0,
        Math.PI / 180,
        houghThreshold,
        minLen,
        10.0
    )

    val (group45, group135) = groupLines(lines)

    return Quintuple(edges, lines, group45, group135, false)
}

/**
 * 【保留】救援通道：CLAHE + 自适应Canny + 放宽Hough
 */
private fun runRescueChannel(
    gray: Mat,
    s: Double
): Quadruple<Mat, Mat, List<Line>, List<Line>> {
    // CLAHE 增强对比度
    val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
    val enhanced = Mat()
    clahe.apply(gray, enhanced)

    // Sobel 梯度计算
    val gradX = Mat()
    val gradY = Mat()
    Imgproc.Sobel(enhanced, gradX, CvType.CV_32F, 1, 0, 3)
    Imgproc.Sobel(enhanced, gradY, CvType.CV_32F, 0, 1, 3)

    val gradMag = Mat()
    Core.magnitude(gradX, gradY, gradMag)

    // 计算梯度分位数
    val gradValues = mutableListOf<Double>()
    for (y in 0 until gradMag.rows()) {
        for (x in 0 until gradMag.cols()) {
            gradValues.add(gradMag.get(y, x)[0])
        }
    }
    gradValues.sort()

    val percentile70 = gradValues[(gradValues.size * 0.70).toInt().coerceIn(0, gradValues.size - 1)]
    val cannyHigh = percentile70.coerceAtLeast(30.0)
    val cannyLow = (0.5 * cannyHigh).coerceAtLeast(10.0)

    // 自适应 Canny
    val edges = Mat()
    Imgproc.Canny(enhanced, edges, cannyLow, cannyHigh)

    // 放宽 Hough 参数
    val minLen = s * 0.18
    val houghThreshold = 15

    val lines = Mat()
    Imgproc.HoughLinesP(
        edges,
        lines,
        1.0,
        Math.PI / 180,
        houghThreshold,
        minLen,
        10.0
    )

    val (group45, group135) = groupLines(lines)

    return Quadruple(edges, lines, group45, group135)
}

/**
 * 判断是否需要触发救援通道
 */
private fun shouldTriggerRescue(lines: Mat, edges: Mat, w: Double, h: Double): Boolean {
    val linesTotal = lines.rows()
    val edgePixels = Core.countNonZero(edges)
    val edgeRatio = edgePixels.toDouble() / (w * h)

    return linesTotal == 0 || edgeRatio < 0.002
}

/**
 * 线段分组（45° 和 135°）
 */
private fun groupLines(lines: Mat): Pair<List<Line>, List<Line>> {
    fun ang0to180(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        var a = atan2(y2 - y1, x2 - x1) * 180 / Math.PI
        if (a < 0) a += 180.0
        return a
    }

    val group45 = mutableListOf<Line>()
    val group135 = mutableListOf<Line>()
    val angleTol = 15.0

    for (i in 0 until lines.rows()) {
        val v = lines.get(i, 0) ?: continue
        val x1 = v[0]; val y1 = v[1]; val x2 = v[2]; val y2 = v[3]
        val len = hypot(x2 - x1, y2 - y1)
        val ang = ang0to180(x1, y1, x2, y2)

        if (abs(ang - 45.0) <= angleTol) group45.add(Line(x1, y1, x2, y2, len, ang))
        if (abs(ang - 135.0) <= angleTol) group135.add(Line(x1, y1, x2, y2, len, ang))
    }

    return Pair(group45, group135)
}

/**
 * 构造空结果（未检测到有效候选）
 */
private fun emptyResult(
    startTime: Long,
    w: Int,
    h: Int,
    totalLines: Int,
    lines45: Int,
    lines135: Int,
    usedRescue: Boolean,
    geometricFilterStats: Map<String, Int>,
    best45: Line? = null,
    best135: Line? = null
): XDetectResult {
    val dbg = XDetectDebug(
        patchW = w,
        patchH = h,
        linesTotal = totalLines,
        lines45 = lines45,
        lines135 = lines135,
        bestPair45X1 = best45?.x1,
        bestPair45Y1 = best45?.y1,
        bestPair45X2 = best45?.x2,
        bestPair45Y2 = best45?.y2,
        bestPair135X1 = best135?.x1,
        bestPair135Y1 = best135?.y1,
        bestPair135X2 = best135?.x2,
        bestPair135Y2 = best135?.y2,
        intersectionX = null,
        intersectionY = null,
        centerDist = null,
        deltaAngle = null,
        armPlus45Cover = null,
        armMinus45Cover = null,
        armPlus135Cover = null,
        armMinus135Cover = null,
        armPlus45Run = null,
        armMinus45Run = null,
        armPlus135Run = null,
        armMinus135Run = null,
        armPlus45Tightness = null,
        armMinus45Tightness = null,
        armPlus135Tightness = null,
        armMinus135Tightness = null,
        armPassCount = 0,
        arm4Score = 0,
        centerScore = 0,
        angleScore = 0,
        containerScore = 0,
        contrastScore = 0,
        diagonalEnergyScore = 0,
        candidatesSize = 0,
        diagonalEnergyValid = false,
        diagonalEnergyRawRatio = null,
        diagonalEnergyInBandCount = null,
        diagonalEnergyOutBandCount = null,
        diagonalEnergyROIRadius = null,
        diagonalEnergyBandWidth = null,
        geometricFilterStats = geometricFilterStats,
        filterReasons = emptyList(),
        widthConsistencyScore = 0.0,
        meanWidth = 0.0,
        stdWidth = 0.0,
        fitScore = 0.0,
        fitP80Distance = 0.0,
        fitROIPointCount = 0,
        intersectionDist1 = 0.0,
        intersectionDist2 = 0.0,
        intersectionSupportScore = 0.0,
        centerDensity = 0.0,
        ringDensity = 0.0,
        buttonContextScore = 0.0,
        centerCrossNorm45 = 0.0,
        centerCrossNorm135 = 0.0,
        centerCrossEvidence = 0.0,
        centerCrossThreshold = 0.0,
        buttonSemanticGateEnabled = false,
        buttonContextPenalty = 1.0,
        buttonCenterPenalty = 1.0,
        centerLowPenaltyTriggered = false,
        edgeTotalInWindow = 0,
        edgeInDiagBands = 0,
        offBandRatio = 0.0,
        offBandPenalty = 1.0,
        offBandPenaltyEnabled = false,
        offBandEnableReason = "NONE",
        offBandMixA = 0.0,
        offBandMixB = 0.0,
        strongXBypassTriggered = false,
        badFitReject = false,
        visualStrongX = false,
        strongGeomBoost = false,
        weakArmRescueBoost = false,
        boostApplied = "NONE",
        boostFloor = 0,
        aspectOk = false,
        ringOk = false,
        scoreBeforeBoost = 0,
        scoreAfterBoost = 0,
        finalIsXThreshold = 55,
        buttonContextPenaltyBefore = 1.0,
        buttonContextPenaltyAfter = 1.0,
        finalScoreBeforeReject = 0.0,
        finalScoreAfterAll = 0.0,
        hasCrossing = false,
        crossingInBounds = false,
        usedRescueChannel = usedRescue
    )

    val elapsedTime = System.currentTimeMillis() - startTime
    return XDetectResult(0, false, dbg, elapsedTime)
}

private data class Line(
    val x1: Double, val y1: Double,
    val x2: Double, val y2: Double,
    val len: Double, val ang: Double
)

private data class DiagonalEnergyDebug(
    val isValid: Boolean,      // 特征是否有效
    val rawRatio: Double,       // 原始 ratio（可能无效）
    val roiRadius: Double,
    val bandWidth: Double,
    val inBandCount: Int,
    val outBandCount: Int
)

// 辅助数据类
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

// 辅助扩展函数
private fun Double.format(digits: Int) = "%.${digits}f".format(this)
