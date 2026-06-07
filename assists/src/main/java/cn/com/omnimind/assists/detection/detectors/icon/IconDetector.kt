package cn.com.omnimind.assists.detection.detectors.icon

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import cn.com.omnimind.assists.detection.detectors.structure.Candidate
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import java.io.IOException

object IconTemplateMatcher {

    private const val TAG = "IconTemplateMatcher"
    private const val TEMPLATE_SIZE = 64
    
    // Config: MinScore and Gap
    private const val MIN_SCORE_THRESHOLD = 0.75
    private const val HIGH_CONF_THRESHOLD = 0.88
    private const val GAP_ABS_MIN = 0.05
    private const val GAP_RATIO_MIN = 0.08

    // Signature Pre-filter Config
    private const val SIGNATURE_FILTER_TOP_K = 10
    private const val SIGNATURE_HAMMING_THRESHOLD = 16

    // Badge Detection Config - 两阶段架构
    // === Gate 阶段（宽松，快速筛选）===
    private const val BADGE_GATE_ROI_SIZE = 12          // 角落 ROI 边长（原始 patch 上）
    private const val BADGE_GATE_R_MIN = 160            // Gate 红色像素 R 通道最小值（宽松）
    private const val BADGE_GATE_DELTA_MIN = 50         // Gate R - max(G,B) 最小差值
    private const val BADGE_GATE_MIN_RED_PX = 12        // Gate 最小红色像素数阈值
    private const val BADGE_GATE_MIN_RATIO = 0.06       // Gate 最小红色占比阈值（6%）
    private const val BADGE_GATE_CLUSTER_PX = 8         // 聚集度模式最小像素数
    private const val BADGE_GATE_CLUSTER_RATIO = 0.6    // 聚集度模式行/列占比上限
    
    // === Confirm 阶段（连通域 + 几何约束）===
    private const val BADGE_CONFIRM_R_MIN = 160         // Confirm 红色像素 R 通道最小值
    private const val BADGE_CONFIRM_DELTA_MIN = 50      // Confirm R - max(G,B) 最小差值
    private const val BADGE_AREA_RATIO_MIN = 0.005      // 连通域面积占比最小值（相对 patch）
    private const val BADGE_AREA_RATIO_MAX = 0.20       // 连通域面积占比最大值
    private const val BADGE_ASPECT_RATIO_MIN = 0.6      // 宽高比最小值
    private const val BADGE_ASPECT_RATIO_MAX = 1.8      // 宽高比最大值
    private const val BADGE_CIRCULARITY_MIN = 0.25      // 圆度最小值
    private const val BADGE_FILL_RATIO_MIN = 0.35       // 连通域内红色像素占比最小值
    private const val BADGE_EDGE_MARGIN = 2             // 判定"贴边"的边界距离
    
    // === Mask 生成 ===
    private const val BADGE_MASK_CORNER_RATIO = 0.35    // 64x64 上角落区域占比
    private const val BADGE_RETRY_SCORE_MARGIN = 0.08   // 二次重试的分数边界
    
    // === 前景检测（背景剔除）===
    private const val BG_DELTA = 16                     // 背景差值阈值
    private const val FG_MIN_AREA_RATIO = 0.02          // 前景最小面积占比
    private const val FG_MAX_AREA_RATIO = 0.98          // 前景最大面积占比（放宽到98%）
    private const val FG_MIN_SIZE_PX = 6                // 前景最小边长（像素）
    private const val FG_MARGIN_RATIO = 0.10            // 前景 bbox 扩张比例
    private const val FG_CORNER_SIZE = 5                // 四角采样块大小
    private const val FG_EDGE_CANNY_LOW = 60.0          // Canny 边缘回退低阈值
    private const val FG_EDGE_CANNY_HIGH = 120.0        // Canny 边缘回退高阈值

    data class Template(
        val iconId: String,
        val variant: String,
        val matGray: Mat,
        val matEdge: Mat,
        val matGrayMasked: Mat,
        val matEdgeMasked: Mat,
        // Signature fields for cheap filter
        val phash64: Long = 0L,
        val edgeDensity: Float = 0f,
        val centerEnergy: Float = 0f,
        val huMoments: DoubleArray? = null,
        // 九宫格区域限制，为空表示全屏匹配
        val gridRegion: List<String> = emptyList(),
        // Package name for filtering - null means from assets (matches any package)
        val packageName: String? = null,
        // Config hash for cache invalidation - identifies URL + signature changes
        val configHash: Int = 0
    )

    private var templates: List<Template> = emptyList()
    private var isInitialized = false
    // Cache the mask
    private var innerMask: Mat? = null

    fun init(context: Context) {
        if (isInitialized) return
        innerMask = createInnerMask(TEMPLATE_SIZE)
        templates = loadTemplates(context)
        isInitialized = true
    }
    
    fun getAvailableIconTypes(): List<String> {
        return templates.map { it.iconId }.distinct()
    }
    
    /**
     * 获取指定模板的九宫格区域限制
     * @param templateId 模板ID
     * @return 区域列表，空列表表示全屏匹配
     */
    fun getGridRegion(templateId: String): List<String> {
        return templates.find { it.iconId == templateId }?.gridRegion ?: emptyList()
    }
    
    /**
     * 计算候选区域所在的九宫格区域
     * 边界允许 10% 的容差，使候选可能属于多个区域
     * 
     * @param rect 候选框坐标
     * @param screenW 屏幕宽度
     * @param screenH 屏幕高度
     * @return 候选所属区域集合（如 ["BR", "MR"]）
     */
    private fun computeCandidateGridRegion(rect: Rect, screenW: Int, screenH: Int): Set<String> {
        val centerX = (rect.x + rect.width / 2.0) / screenW
        val centerY = (rect.y + rect.height / 2.0) / screenH
        
        // 10% 边界容差
        val margin = 0.10
        val regions = mutableSetOf<String>()
        
        // 水平位置判断
        val isLeft = centerX < (1.0 / 3.0 + margin)
        val isMiddleH = centerX > (1.0 / 3.0 - margin) && centerX < (2.0 / 3.0 + margin)
        val isRight = centerX > (2.0 / 3.0 - margin)
        
        // 垂直位置判断
        val isTop = centerY < (1.0 / 3.0 + margin)
        val isMiddleV = centerY > (1.0 / 3.0 - margin) && centerY < (2.0 / 3.0 + margin)
        val isBottom = centerY > (2.0 / 3.0 - margin)
        
        // 组合生成区域
        if (isTop && isLeft) regions.add("TL")
        if (isTop && isMiddleH) regions.add("TC")
        if (isTop && isRight) regions.add("TR")
        if (isMiddleV && isLeft) regions.add("ML")
        if (isMiddleV && isMiddleH) regions.add("MC")
        if (isMiddleV && isRight) regions.add("MR")
        if (isBottom && isLeft) regions.add("BL")
        if (isBottom && isMiddleH) regions.add("BC")
        if (isBottom && isRight) regions.add("BR")
        
        return regions
    }

    fun reload(context: Context) {
        templates.forEach { 
            it.matGray.release()
            it.matEdge.release()
            it.matGrayMasked.release()
            it.matEdgeMasked.release()
        }
        innerMask?.release()
        innerMask = createInnerMask(TEMPLATE_SIZE)
        templates = loadTemplates(context)
    }

    data class MatchDebugInfo(
        val bestType: String?,
        val bestScore: Double,
        val secondType: String?,
        val secondScore: Double,
        val gap: Double,
        val gapRatio: Double,
        val failReason: String?
    )

    data class IconMatchingResult(
        val hits: List<IconMatchResult>,
        val debugInfos: Map<Rect, MatchDebugInfo>
    )

    /**
     * Main Entry Point
     * @param candidates 候选列表
     * @param bgr BGR 格式图像
     * @param allowedTypes 允许的模板类型（可选）
     * @param screenW 屏幕宽度，用于 gridRegion 过滤（可选，传入时启用区域过滤）
     * @param screenH 屏幕高度，用于 gridRegion 过滤（可选，传入时启用区域过滤）
     */
    fun match(
        candidates: List<Candidate>,
        bgr: Mat,
        allowedTypes: Set<String>? = null,
        screenW: Int? = null,
        screenH: Int? = null
    ): IconMatchingResult {
        if (!isInitialized || templates.isEmpty()) return IconMatchingResult(emptyList(), emptyMap())

        val hits = ArrayList<IconMatchResult>()
        val debugInfos = HashMap<Rect, MatchDebugInfo>()
        
        val mask = innerMask ?: createInnerMask(TEMPLATE_SIZE).also { innerMask = it }
        
        // 实际屏幕尺寸（用于 gridRegion 过滤）
        val effectiveScreenW = screenW ?: bgr.cols()
        val effectiveScreenH = screenH ?: bgr.rows()
        val enableGridFilter = screenW != null && screenH != null
        
        // 统计信息
        var totalCandidates = 0
        var totalTemplateMatches = 0
        var gridFilteredCount = 0

        for (cand in candidates) {
            if (cand.kind != Candidate.Kind.ICON) continue
            totalCandidates++

            // 1. Crop candidate patch
            val safeRect = clampRect(cand.rect, bgr.cols(), bgr.rows())
            val patch = bgr.submat(safeRect)

            if (patch.empty()) {
               patch.release()
               continue
            }

            // 2. 【Badge Detection】两阶段检测 (Gate + Confirm)
            val hasBadge = hasRedBadgeGate(patch) >= 0
            
            // 3. Normalize patch (灰度版本)
            val normGray = normalizeIconPatch(patch, TEMPLATE_SIZE)
            
            // 4. 【Badge Mask】若命中红点，生成 badgeMask 并计算 effectiveMask
            var badgeMask: Mat? = null
            val effectiveMask: Mat
            if (hasBadge) {
                val normBgr = normalizeIconPatchBgr(patch, TEMPLATE_SIZE)
                badgeMask = buildBadgeMask64(normBgr)
                normBgr.release()
                
                // effectiveMask = innerMask & ~badgeMask
                effectiveMask = Mat()
                val invertedBadge = Mat()
                Core.bitwise_not(badgeMask, invertedBadge)
                Core.bitwise_and(mask, invertedBadge, effectiveMask)
                invertedBadge.release()
            } else {
                effectiveMask = mask // 直接使用 innerMask，无需额外分配
            }
            
            // 5. Masking (使用 effectiveMask)
            val normGrayMasked = Mat()
            Core.bitwise_and(normGray, normGray, normGrayMasked, effectiveMask)
            
            val normEdgeMasked = Mat()
            Imgproc.Canny(normGrayMasked, normEdgeMasked, 50.0, 150.0)
            
            // 6. Compute candidate signature for cheap filter
            val candidatePhash = computePhash64(normGrayMasked)
            
            // 6.5 计算候选所在区域（用于 gridRegion 过滤）
            val candRegions = if (enableGridFilter) {
                computeCandidateGridRegion(cand.rect, effectiveScreenW, effectiveScreenH)
            } else {
                emptySet()
            }
            
            // 7. Pre-filter templates using pHash (cheap filter) + gridRegion
            val candidateTemplates = run {
                // 第一层：按 allowedTypes 过滤
                var filtered = if (allowedTypes != null) {
                    templates.filter { allowedTypes.contains(it.iconId) }
                } else {
                    templates
                }
                
                // 第二层：按 gridRegion 过滤（如果启用）
                if (enableGridFilter && candRegions.isNotEmpty()) {
                    val beforeSize = filtered.size
                    filtered = filtered.filter { tmpl ->
                        // 模板没有设置 gridRegion 则全局匹配；否则检查是否有交集
                        tmpl.gridRegion.isEmpty() || tmpl.gridRegion.any { it in candRegions }
                    }
                    gridFilteredCount += (beforeSize - filtered.size)
                }
                
                // 第三层：按 pHash 过滤（如果模板数量超过阈值）
                if (filtered.size > SIGNATURE_FILTER_TOP_K) {
                    val sorted = filtered
                        .map { tmpl -> Pair(tmpl, hammingDistance(candidatePhash, tmpl.phash64)) }
                        .sortedBy { it.second }
                    
                    val topK = sorted.take(SIGNATURE_FILTER_TOP_K)
                    
                    if (topK.isEmpty() || topK.first().second > SIGNATURE_HAMMING_THRESHOLD) {
                        filtered // Fallback to all
                    } else {
                        topK.map { it.first }
                    }
                } else {
                    filtered
                }
            }
            
            totalTemplateMatches += candidateTemplates.size
            
            // 8. Match against filtered templates
            var bestScore = -1.0
            var secondBestScore = -1.0
            var bestTemplate: Template? = null
            var bestType = "gray"
            var secondBestType: String? = null

            for (tmpl in candidateTemplates) {
                // Composite Score
                val edgeScore = multiScaleMatch(normEdgeMasked, tmpl.matEdgeMasked)
                val grayScore = multiScaleMatch(normGrayMasked, tmpl.matGrayMasked)
                
                val finalScore = 0.7 * edgeScore + 0.3 * grayScore
                
                if (finalScore > bestScore) {
                    secondBestScore = bestScore
                    secondBestType = if (bestTemplate != null) bestTemplate.iconId else null
                    
                    bestScore = finalScore
                    bestTemplate = tmpl
                    bestType = "composite" 
                } else if (finalScore > secondBestScore) {
                    secondBestScore = finalScore
                    secondBestType = tmpl.iconId
                }
            }

            // 9. Decision Rule
            val gapAbs = bestScore - secondBestScore
            val gapRatio = if (bestScore > 0.001) gapAbs / bestScore else 0.0
            
            var isHit = false
            var failReason: String? = null
            
            if (bestTemplate == null) {
                failReason = "No Templates"
            } else if (bestScore >= HIGH_CONF_THRESHOLD) {
                isHit = true
                failReason = "HighConf (Score>=${HIGH_CONF_THRESHOLD})" // Debug info, actually a pass
            } else {
                // Strict check
                if (bestScore < MIN_SCORE_THRESHOLD) {
                    failReason = "Low Score (<$MIN_SCORE_THRESHOLD)"
                } else if (gapAbs < GAP_ABS_MIN) {
                    failReason = "GapAbs Low (<$GAP_ABS_MIN)"
                } else if (gapRatio < GAP_RATIO_MIN) {
                    failReason = "GapRatio Low (<$GAP_RATIO_MIN)"
                } else {
                    isHit = true
                }
            }
            
            if (isHit) failReason = null // Clear for success cases

            val debugInfo = MatchDebugInfo(
                bestType = bestTemplate?.iconId,
                bestScore = bestScore,
                secondType = secondBestType,
                secondScore = secondBestScore,
                gap = gapAbs,
                gapRatio = gapRatio,
                failReason = failReason
            )
            debugInfos[cand.rect] = debugInfo

            if (isHit && bestTemplate != null) {
                hits.add(IconMatchResult(
                    iconId = bestTemplate.iconId,
                    score = bestScore,
                    rect = cand.rect,
                    templateType = bestType
                ))
            }

            // Cleanup
            patch.release()
            normGray.release()
            normGrayMasked.release()
            normEdgeMasked.release()
            badgeMask?.release()
            if (hasBadge) effectiveMask.release() // 仅当新分配时才释放
        }
        
        // 日志：gridRegion 过滤效果
        if (enableGridFilter) {
            Log.d(TAG, "[🔍 GridFilter] 候选=$totalCandidates, 模板匹配总次数=$totalTemplateMatches, " +
                    "gridRegion过滤掉=$gridFilteredCount, 平均每候选匹配=${if(totalCandidates > 0) totalTemplateMatches / totalCandidates else 0}个模板")
        }
        
        return IconMatchingResult(hits, debugInfos)
    }

    /**
     * 对单个候选匹配指定模板
     * 
     * @param candidate 候选区域
     * @param bgr BGR 格式图像
     * @param templateId 模板 ID（如 "wechat_emoji"）
     * @param packageName 当前应用包名，用于校验icon模板归属
     * @return 匹配分数 0.0 ~ 1.0
     */
    fun matchSingleTemplate(
        candidate: Candidate,
        bgr: Mat,
        templateId: String,
        packageName: String? = null,
        enableBadgeRemoval: Boolean = true
    ): Double {
        if (!isInitialized) return 0.0
        
        val targetTemplates = templates.filter { tmpl ->
            tmpl.iconId == templateId && 
            (tmpl.packageName == null || packageName == null || tmpl.packageName == packageName)
        }
        if (targetTemplates.isEmpty()) {
            android.util.Log.v(TAG, "No matching template for id=$templateId with packageName=$packageName")
            return 0.0
        }
        
        val innerMaskMat = innerMask ?: createInnerMask(TEMPLATE_SIZE).also { innerMask = it }
        
        // 裁剪候选区域
        val safeRect = clampRect(candidate.rect, bgr.cols(), bgr.rows())
        val patch = bgr.submat(safeRect)
        if (patch.empty()) {
            patch.release()
            return 0.0
        }
        
        // ===== 综合红点消除方案 =====
        val badgeCorner = if (enableBadgeRemoval) hasRedBadgeGate(patch) else -1
        
        var normGray: Mat
        var effectiveMask: Mat = innerMaskMat
        var needReleaseEffectiveMask = false
        
        if (badgeCorner >= 0 && enableBadgeRemoval) {
            // 生成 badgeMask（在原始 patch 尺寸上）
            val badgeMask = buildBadgeMaskOnPatch(patch)
            
            // Step 1: 判断外扩型 vs 嵌入型
            val isExpandType = isBadgeTouchingBorder(badgeMask, margin = 2)
            
            if (isExpandType) {
                // Step 2A: 外扩型 - 找主体 bbox 再等比缩放
                val contentPatch = extractContentWithoutBadge(patch, badgeMask)
                normGray = normalizeIconPatch(contentPatch, TEMPLATE_SIZE)
                contentPatch.release()
                badgeMask.release()
            } else {
                // Step 2B: 嵌入型 - 正常 normalize，用 mask 排除遮挡区域
                normGray = normalizeIconPatch(patch, TEMPLATE_SIZE)
                
                // 把 badgeMask 缩放到 64x64
                val badgeMask64 = Mat()
                Imgproc.resize(badgeMask, badgeMask64, Size(TEMPLATE_SIZE.toDouble(), TEMPLATE_SIZE.toDouble()), 0.0, 0.0, Imgproc.INTER_NEAREST)
                badgeMask.release()
                
                // 膨胀一点确保遮挡更彻底
                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
                Imgproc.dilate(badgeMask64, badgeMask64, kernel)
                kernel.release()
                
                // effectiveMask = innerMask & ~badgeMask64
                val invertedBadge = Mat()
                Core.bitwise_not(badgeMask64, invertedBadge)
                effectiveMask = Mat()
                Core.bitwise_and(innerMaskMat, invertedBadge, effectiveMask)
                invertedBadge.release()
                badgeMask64.release()
                needReleaseEffectiveMask = true
                
                // Step 3: 保险丝 - 如果可见区域太小，回退
                val visibleRatio = Core.countNonZero(effectiveMask) / (TEMPLATE_SIZE * TEMPLATE_SIZE).toDouble()
                if (visibleRatio < 0.18) {
                    effectiveMask.release()
                    effectiveMask = innerMaskMat
                    needReleaseEffectiveMask = false
                }
            }
        } else {
            // 无红点，正常处理
            normGray = normalizeIconPatch(patch, TEMPLATE_SIZE)
        }
        
        patch.release()
        
        // 应用 effectiveMask 并计算边缘
        val normGrayMasked = Mat()
        Core.bitwise_and(normGray, normGray, normGrayMasked, effectiveMask)
        val normEdgeMasked = Mat()
        Imgproc.Canny(normGrayMasked, normEdgeMasked, 50.0, 150.0)
        
        // 对该 templateId 的所有变体取最高分
        var bestScore = 0.0
        for (tmpl in targetTemplates) {
            // 如果使用了自定义 effectiveMask（嵌入型），模板也要用同样的 mask
            val templGray: Mat
            val templEdge: Mat
            
            if (needReleaseEffectiveMask) {
                // 对模板也应用 effectiveMask
                templGray = Mat()
                Core.bitwise_and(tmpl.matGrayMasked, tmpl.matGrayMasked, templGray, effectiveMask)
                templEdge = Mat()
                Core.bitwise_and(tmpl.matEdgeMasked, tmpl.matEdgeMasked, templEdge, effectiveMask)
            } else {
                templGray = tmpl.matGrayMasked
                templEdge = tmpl.matEdgeMasked
            }
            
            val edgeScore = multiScaleMatch(normEdgeMasked, templEdge)
            val grayScore = multiScaleMatch(normGrayMasked, templGray)
            val finalScore = 0.7 * edgeScore + 0.3 * grayScore
            if (finalScore > bestScore) {
                bestScore = finalScore
            }
            
            if (needReleaseEffectiveMask) {
                templGray.release()
                templEdge.release()
            }
        }
        
        // 释放资源
        normGray.release()
        normGrayMasked.release()
        normEdgeMasked.release()
        if (needReleaseEffectiveMask) effectiveMask.release()
        
        return bestScore
    }
    
    /**
     * 在原始 patch 尺寸上构建红点 mask
     */
    private fun buildBadgeMaskOnPatch(bgrPatch: Mat): Mat {
        val mask = Mat.zeros(bgrPatch.rows(), bgrPatch.cols(), CvType.CV_8UC1)
        val channels = bgrPatch.channels()
        if (channels < 3) return mask
        
        val pixel = ByteArray(channels)
        val whitePixel = byteArrayOf(255.toByte())
        
        for (y in 0 until bgrPatch.rows()) {
            for (x in 0 until bgrPatch.cols()) {
                bgrPatch.get(y, x, pixel)
                val b = pixel[0].toInt() and 0xFF
                val g = pixel[1].toInt() and 0xFF
                val r = pixel[2].toInt() and 0xFF
                
                if (r >= BADGE_CONFIRM_R_MIN && (r - max(g, b)) >= BADGE_CONFIRM_DELTA_MIN) {
                    mask.put(y, x, whitePixel)
                }
            }
        }
        
        // 形态学处理
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
        kernel.release()
        
        return mask
    }
    
    /**
     * 判断红点是否触碰边界（外扩型）
     */
    private fun isBadgeTouchingBorder(badgeMask: Mat, margin: Int = 2): Boolean {
        if (badgeMask.empty() || Core.countNonZero(badgeMask) == 0) return false
        
        val points = ArrayList<Point>()
        Core.findNonZero(badgeMask, Mat().also { 
            Core.findNonZero(badgeMask, it)
            if (!it.empty()) {
                for (i in 0 until it.rows()) {
                    val pt = it.get(i, 0)
                    points.add(Point(pt[0], pt[1]))
                }
            }
        })
        
        if (points.isEmpty()) return false
        
        val badgeRect = Imgproc.boundingRect(MatOfPoint(*points.toTypedArray()))
        val w = badgeMask.cols()
        val h = badgeMask.rows()
        
        return badgeRect.x <= margin || 
               badgeRect.y <= margin || 
               badgeRect.x + badgeRect.width >= w - margin || 
               badgeRect.y + badgeRect.height >= h - margin
    }
    
    /**
     * 外扩型：提取不含红点的主体区域
     */
    private fun extractContentWithoutBadge(patch: Mat, badgeMask: Mat): Mat {
        // contentMask = NOT(badgeMask)
        val contentMask = Mat()
        Core.bitwise_not(badgeMask, contentMask)
        
        // 转灰度做边缘检测
        val gray = Mat()
        if (patch.channels() >= 3) {
            Imgproc.cvtColor(patch, gray, Imgproc.COLOR_BGR2GRAY)
        } else {
            patch.copyTo(gray)
        }
        
        // 边缘检测
        val edges = Mat()
        Imgproc.Canny(gray, edges, 50.0, 150.0)
        gray.release()
        
        // 只保留 contentMask 内的边缘
        Core.bitwise_and(edges, contentMask, edges)
        contentMask.release()
        
        // 找轮廓，选面积最大且靠中心的
        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        edges.release()
        
        var bestRect: Rect? = null
        var maxScore = -1.0
        val cx = patch.cols() / 2.0
        val cy = patch.rows() / 2.0
        
        for (c in contours) {
            val r = Imgproc.boundingRect(c)
            if (r.width < 10 || r.height < 10) continue
            
            val area = r.width * r.height.toDouble()
            val rcx = r.x + r.width / 2.0
            val rcy = r.y + r.height / 2.0
            val dist = kotlin.math.sqrt((rcx - cx) * (rcx - cx) + (rcy - cy) * (rcy - cy))
            val centrality = 1.0 / (1.0 + dist)
            val score = area * centrality
            
            if (score > maxScore) {
                maxScore = score
                bestRect = r
            }
        }
        
        return if (bestRect != null && bestRect.width >= 10 && bestRect.height >= 10) {
            val safeRect = clampRect(bestRect, patch.cols(), patch.rows())
            patch.submat(safeRect).clone()
        } else {
            patch.clone()
        }
    }
    
    /**
     * 等比缩放 + padding（letterbox）到目标尺寸
     * 保持原始长宽比，不会变形
     */
    private fun letterboxNormalize(src: Mat, outSize: Int): Mat {
        // 转灰度
        val gray = Mat()
        if (src.channels() >= 3) {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        } else {
            src.copyTo(gray)
        }
        
        // 计算等比缩放比例
        val aspect = gray.cols().toDouble() / gray.rows()
        val targetContentSize = (outSize * 0.80).toInt()
        
        var newW = targetContentSize
        var newH = targetContentSize
        
        if (aspect > 1.0) {
            newH = (newW / aspect).toInt()
        } else {
            newW = (newH * aspect).toInt()
        }
        
        newW = max(1, newW)
        newH = max(1, newH)
        
        // 缩放
        val resized = Mat()
        Imgproc.resize(gray, resized, Size(newW.toDouble(), newH.toDouble()))
        gray.release()
        
        // 居中填充
        val outMat = Mat.zeros(outSize, outSize, CvType.CV_8UC1)
        val xOff = (outSize - newW) / 2
        val yOff = (outSize - newH) / 2
        
        val roi = outMat.submat(Rect(xOff, yOff, newW, newH))
        resized.copyTo(roi)
        resized.release()
        
        return outMat
    }
    
    /**
     * 等比缩放 + padding（letterbox）到目标尺寸，保持 BGR 格式
     * 用于调试图像生成
     */
    private fun letterboxNormalizeBgr(src: Mat, outSize: Int): Mat {
        val aspect = src.cols().toDouble() / src.rows()
        val targetContentSize = (outSize * 0.80).toInt()
        
        var newW = targetContentSize
        var newH = targetContentSize
        
        if (aspect > 1.0) {
            newH = (newW / aspect).toInt()
        } else {
            newW = (newH * aspect).toInt()
        }
        
        newW = max(1, newW)
        newH = max(1, newH)
        
        val resized = Mat()
        Imgproc.resize(src, resized, Size(newW.toDouble(), newH.toDouble()))
        
        val outMat = Mat.zeros(outSize, outSize, src.type())
        val xOff = (outSize - newW) / 2
        val yOff = (outSize - newH) / 2
        
        val roi = outMat.submat(Rect(xOff, yOff, newW, newH))
        resized.copyTo(roi)
        resized.release()
        
        return outMat
    }

    private fun multiScaleMatch(img: Mat, templ: Mat): Double {
        // Scales: 0.85, 0.90, ..., 1.15
        val scales = doubleArrayOf(0.85, 0.90, 0.95, 1.0, 1.0, 1.05, 1.10, 1.15)
        var maxVal = -1.0

        for (s in scales) {
            // Resize template or image? Better to resize the smaller one (template) to match the image patch?
            // Actually, both are normalized to 64x64. So 's' represents slight scale mismatch.
            // Let's resize the template to `s * 64`
            
            val tW = (templ.cols() * s).toInt()
            val tH = (templ.rows() * s).toInt()
            
            if (tW > img.cols() || tH > img.rows()) continue 
            // Avoid resizing template larger than image, or handle padding.
            // Since both are 64x64, s > 1.0 means template > image. matchTemplate needs image >= template.
            // We can resize the IMAGE instead if s < 1.0, or resize Template if s < 1.0.
            
            // Simpler approach: Resize the `templ` to various sizes around 64.
            // BUT matchTemplate requires: image size >= template size. 
            // If we upscale template to 1.15 (73 px), it won't fit in 64px image.
            
            // FIX: Resize the `templ` to a new size, and if it exceeds `img`, we skip or pad `img`.
            // Given performance constraints, let's keep it simple: 
            // Resize `templ` down (s < 1.0) is safe.
            // Resize `templ` up (s > 1.0) requires `img` to be larger.
            
            // Alternative: Scale the normalized patch `img` ?
            // Let's scale `img` by `s`.
            val newW = (img.cols() * s).toInt()
            val newH = (img.rows() * s).toInt()
            
            // If simplified: just resize `templ` to s*64.
            // If s > 1.0, we can't search.
            // Let's assume the user meant "scale search" where we look for the template at different sizes.
            // To support s > 1.0, we need to pad the image or shrink the template (inverse).
            
            // Let's implement resizing `templ` for s <= 1.0, and resizing `img` for s > 1.0?
            // Standard approach: Resize the QUERY image (img).
            // If s=1.0, query=64.
            // If s=1.1, query=70 (now template=64 fits inside).
            // If s=0.9, query=57 (template=64 fails).
            
            // Correct approach for `matchTemplate(img, templ)`: `img` must be larger than `templ`.
            // We want to verify if `img` contains `templ` at scale `s`.
            // So we resize `templ` to `64 * s`.
            // If `64 * s` > `64`, it fails.
            
            // Let's adjust logic:
            // Resize `templ` to `targetSize`.
            // If `targetSize > 64`, we skip (or pad image). 
            // The prompt asks for `scale ∈ [0.85 .. 1.15]`.
            // This implies the icon in the patch might be smaller or larger than the template.
            
            // Let's resize `templ` using `s`. 
            // If `s > 1.0`, the template is larger than 64. 
            // To make this work, we should probably pad the `img` (normGray) slightly during normalization 
            // OR normalize to a larger size (e.g. 80) but place content in 64, 
            // then crop?
            
            // The constraint: `normalizeIconPatch` output is 64x64.
            // If we scale template to 1.15 * 64 = 73, it won't fit.
            
            // Workaround: We resize the `templ` to `s * 64`.
            // If `s > 1.0`, we scale `templ` UP. We must scale `img` UP as well? No.
            
            // Let's interpret "Scale" as: "The feature in the image is scaled by s relative to template".
            // So if feature is 1.1x larger, we should scale Template by 1.1x? No, Template should match feature.
            
            // Let's allow `templ` resizing.
            // To handle s > 1.0: We need `img` to accommodate.
            // We can pad `img` with borders before matching if needed.
            
            val scaledTempl = Mat()
            val finalW = (templ.cols() * s).toInt()
            val finalH = (templ.rows() * s).toInt()
            Imgproc.resize(templ, scaledTempl, Size(finalW.toDouble(), finalH.toDouble()))
            
            val workImg = Mat()
            if (finalW > img.cols() || finalH > img.rows()) {
                // Pad image to fit larger template
                val padW = max(0, finalW - img.cols())
                val padH = max(0, finalH - img.rows())
                Core.copyMakeBorder(img, workImg, 0, padH, 0, padW, Core.BORDER_REPLICATE)
            } else {
                img.copyTo(workImg)
            }

            // Match
            val result = Mat()
            Imgproc.matchTemplate(workImg, scaledTempl, result, Imgproc.TM_CCOEFF_NORMED)
            val mmr = Core.minMaxLoc(result)
            
            if (mmr.maxVal > maxVal) {
                maxVal = mmr.maxVal
            }
            
            scaledTempl.release()
            workImg.release()
            result.release()
        }
        return maxVal
    }
    
    /**
     * 计算前景 bounding box（背景剔除方案）
     * 优先使用四角背景估计 + 差值阈值化
     * @return 前景 Rect，如果检测失败返回 null
     */
    private fun computeForegroundRect(gray: Mat): Rect? {
        val h = gray.rows()
        val w = gray.cols()
        if (h < FG_CORNER_SIZE * 2 || w < FG_CORNER_SIZE * 2) return null
        
        // 1. 估计背景灰度：取四角小块的均值
        val cornerSize = FG_CORNER_SIZE
        val corners = listOf(
            gray.submat(0, cornerSize, 0, cornerSize),                  // TL
            gray.submat(0, cornerSize, w - cornerSize, w),              // TR
            gray.submat(h - cornerSize, h, 0, cornerSize),              // BL
            gray.submat(h - cornerSize, h, w - cornerSize, w)           // BR
        )
        
        var sum = 0.0
        var count = 0
        for (corner in corners) {
            sum += Core.mean(corner).`val`[0]
            count++
            corner.release()
        }
        val bgGray = (sum / count).toInt()
        
        // 2. diff = abs(gray - bg)
        val bgMat = Mat(h, w, CvType.CV_8UC1, Scalar(bgGray.toDouble()))
        val diff = Mat()
        Core.absdiff(gray, bgMat, diff)
        bgMat.release()
        
        // 3. mask0 = diff > delta
        val mask0 = Mat()
        Imgproc.threshold(diff, mask0, BG_DELTA.toDouble(), 255.0, Imgproc.THRESH_BINARY)
        diff.release()
        
        // 4. morphology close + dilate
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
        Imgproc.morphologyEx(mask0, mask0, Imgproc.MORPH_CLOSE, kernel)
        Imgproc.dilate(mask0, mask0, kernel)
        kernel.release()
        
        // 5. 找 nonZero bbox
        val points = Mat()
        Core.findNonZero(mask0, points)
        mask0.release()
        
        if (points.empty()) {
            points.release()
            return null
        }
        
        val bbox = Imgproc.boundingRect(points)
        points.release()
        
        // 6. 校验面积比例和最小尺寸
        val totalArea = w * h
        val bboxArea = bbox.width * bbox.height
        val areaRatio = bboxArea.toDouble() / totalArea
        
        if (areaRatio < FG_MIN_AREA_RATIO || areaRatio > FG_MAX_AREA_RATIO) {
            return null
        }
        if (bbox.width < FG_MIN_SIZE_PX || bbox.height < FG_MIN_SIZE_PX) {
            return null
        }
        
        return bbox
    }
    
    /**
     * 边缘检测回退方案：Canny + dilate + bbox
     * @return 边缘 Rect，如果检测失败返回 null
     */
    private fun computeEdgeBbox(gray: Mat): Rect? {
        val h = gray.rows()
        val w = gray.cols()
        
        // 1. Canny 边缘
        val edges = Mat()
        Imgproc.Canny(gray, edges, FG_EDGE_CANNY_LOW, FG_EDGE_CANNY_HIGH)
        
        // 2. dilate 让细线连通
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(edges, edges, kernel)
        kernel.release()
        
        // 3. 找 nonZero bbox
        val points = Mat()
        Core.findNonZero(edges, points)
        edges.release()
        
        if (points.empty()) {
            points.release()
            return null
        }
        
        val bbox = Imgproc.boundingRect(points)
        points.release()
        
        // 4. 校验
        val totalArea = w * h
        val bboxArea = bbox.width * bbox.height
        val areaRatio = bboxArea.toDouble() / totalArea
        
        // 边缘回退不检查面积上限，只检查下限（因为边缘检测是回退方案，应该更宽松）
        if (areaRatio < FG_MIN_AREA_RATIO) {
            return null
        }
        if (bbox.width < FG_MIN_SIZE_PX || bbox.height < FG_MIN_SIZE_PX) {
            return null
        }
        
        return bbox
    }
    
    /**
     * 旧的轮廓检测方案（最终回退）
     * @return 最大轮廓 Rect，如果检测失败返回 null
     */
    private fun computeContourBbox(gray: Mat): Rect? {
        // Blur + AdaptiveThreshold
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(3.0, 3.0), 0.0)
        
        val bin = Mat()
        Imgproc.adaptiveThreshold(blurred, bin, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 11, 2.0)
        blurred.release()
        
        // Morphology open
        val k = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.morphologyEx(bin, bin, Imgproc.MORPH_OPEN, k)
        k.release()
        
        // Find contours
        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(bin, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        bin.release()
        
        // Pick max area
        var bestRect: Rect? = null
        var maxArea = -1.0
        
        for (c in contours) {
            val r = Imgproc.boundingRect(c)
            val area = r.width * r.height.toDouble()
            if (r.width < 4 || r.height < 4) continue
            if (area > maxArea) {
                maxArea = area
                bestRect = r
            }
        }
        
        return bestRect
    }
    
    /**
     * 对 rect 做 margin 扩张并 clamp 到图像边界
     */
    private fun expandRectWithMargin(rect: Rect, w: Int, h: Int, marginRatio: Double = FG_MARGIN_RATIO): Rect {
        val marginX = (rect.width * marginRatio).toInt()
        val marginY = (rect.height * marginRatio).toInt()
        
        val newX = max(0, rect.x - marginX)
        val newY = max(0, rect.y - marginY)
        val newW = min(w - newX, rect.width + 2 * marginX)
        val newH = min(h - newY, rect.height + 2 * marginY)
        
        return Rect(newX, newY, newW, newH)
    }
    
    /**
     * 1️⃣ 模板标准化函数
     * 简单方案：直接 letterbox 缩放到目标尺寸，不做前景检测
     * 保证模板和候选的处理一致性
     */
    fun normalizeIconPatch(src: Mat, outSize: Int = 64): Mat {
        // 1. Convert to Gray
        val gray = Mat()
        if (src.channels() >= 3) {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        } else {
            src.copyTo(gray)
        }
        
        val h = gray.rows()
        val w = gray.cols()
        
        // 2. 计算 letterbox 尺寸（保持宽高比，缩放到 80% 的目标尺寸）
        val targetContentSize = (outSize * 0.80).toInt()
        val aspect = w.toDouble() / h
        
        var newW = targetContentSize
        var newH = targetContentSize
        
        if (aspect > 1.0) {
            // 宽图
            newH = (newW / aspect).toInt()
        } else {
            // 高图或正方形
            newW = (newH * aspect).toInt()
        }
        
        newW = max(1, newW)
        newH = max(1, newH)
        
        // 3. Resize
        val resized = Mat()
        Imgproc.resize(gray, resized, Size(newW.toDouble(), newH.toDouble()))
        gray.release()
        
        // 4. Center Padding
        val outMat = Mat.zeros(outSize, outSize, CvType.CV_8UC1)
        val xOff = (outSize - newW) / 2
        val yOff = (outSize - newH) / 2
        
        val roi = outMat.submat(Rect(xOff, yOff, newW, newH))
        resized.copyTo(roi)
        resized.release()
        
        return outMat
    }

    private fun loadTemplates(context: Context): List<Template> {
        val list = ArrayList<Template>()
        val mask = innerMask ?: createInnerMask(TEMPLATE_SIZE).also { innerMask = it }
        
        try {
            val am = context.assets
            // Structure: icons/icon_id/variant.png
            val iconDirs = am.list("icons") ?: return emptyList()
            
            for (iconId in iconDirs) {
                val files = am.list("icons/$iconId") ?: continue
                
                // 读取该icon目录下的signature.json
                val signatureFile = loadSignatureFile(am, "icons/$iconId/signature.json")
                
                for (file in files) { // e.g., light.png, dark.png
                     if (!file.endsWith(".png")) continue
                     
                     val variant = file.substringBeforeLast(".")
                     val path = "icons/$iconId/$file"
                     
                     val stream = am.open(path)
                     val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                     stream.close()
                     
                     if (bmp != null) {
                         val mat = Mat()
                         Utils.bitmapToMat(bmp, mat)
                         
                         // BGR conv
                         if (mat.channels() == 4) Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2BGR)
                         
                         // Normalize
                         val normGray = normalizeIconPatch(mat, TEMPLATE_SIZE)
                         val normEdge = Mat()
                         Imgproc.Canny(normGray, normEdge, 50.0, 150.0)
                         
                         // Masked Versions
                         val normGrayMasked = Mat()
                         Core.bitwise_and(normGray, normGray, normGrayMasked, mask)
                         
                         val normEdgeMasked = Mat()
                         Imgproc.Canny(normGrayMasked, normEdgeMasked, 50.0, 150.0)
                         
                         // 从签名文件获取签名（如果不存在则使用默认值）
                         val sig = signatureFile.variants[variant]
                         
                         list.add(Template(
                             iconId = iconId,
                             variant = variant,
                             matGray = normGray,
                             matEdge = normEdge,
                             matGrayMasked = normGrayMasked,
                             matEdgeMasked = normEdgeMasked,
                             phash64 = sig?.phash64 ?: 0L,
                             edgeDensity = sig?.edgeDensity ?: 0f,
                             centerEnergy = sig?.centerEnergy ?: 0f,
                             huMoments = sig?.huMoments ?: DoubleArray(7),
                             gridRegion = signatureFile.gridRegion
                         ))
                         mat.release()
                     }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return list
    }
    
    /**
     * 从签名JSON文件加载签名
     * 文件格式：
     * {
     *   "variants": [
     *     { "name": "light", "phash64": "0x...", "edgeDensity": 0.2, "centerEnergy": 0.6, "hu": [...] },
     *     { "name": "dark", ... }
     *   ],
     *   "gridRegion": ["TL", "TC"]  // 九宫格区域限制
     * }
     */
    private fun loadSignatureFile(am: android.content.res.AssetManager, path: String): SignatureFileData {
        val variants = HashMap<String, VariantSignature>()
        var gridRegion: List<String> = emptyList()
        
        try {
            val stream = am.open(path)
            val json = stream.bufferedReader().use { it.readText() }
            stream.close()
            
            val jsonObj = org.json.JSONObject(json)
            
            // 读取 gridRegion（与 variants 同级）
            val gridRegionArray = jsonObj.optJSONArray("gridRegion")
            if (gridRegionArray != null) {
                gridRegion = List(gridRegionArray.length()) { gridRegionArray.getString(it) }
            }
            
            // 读取 variants 数组
            val variantsArray = jsonObj.optJSONArray("variants") ?: return SignatureFileData(variants, gridRegion)
            
            for (i in 0 until variantsArray.length()) {
                val sigObj = variantsArray.getJSONObject(i)
                
                // name 字段存放文件名（不含扩展名），如 "light" 对应 light.png
                val name = sigObj.optString("name", "") 
                if (name.isEmpty()) continue
                
                // 解析phash64（支持hex字符串或数字）
                val phashStr = sigObj.optString("phash64", "0")
                val phash64 = if (phashStr.startsWith("0x") || phashStr.startsWith("0X")) {
                    java.lang.Long.parseUnsignedLong(phashStr.substring(2), 16)
                } else {
                    phashStr.toLongOrNull() ?: 0L
                }
                
                val edgeDensity = sigObj.optDouble("edgeDensity", 0.0).toFloat()
                val centerEnergy = sigObj.optDouble("centerEnergy", 0.0).toFloat()
                
                val huArray = sigObj.optJSONArray("hu")
                val huMoments = if (huArray != null) {
                    DoubleArray(7) { idx -> huArray.optDouble(idx, 0.0) }
                } else {
                    DoubleArray(7)
                }
                
                variants[name] = VariantSignature(phash64, edgeDensity, centerEnergy, huMoments)
            }
            
        } catch (e: Exception) {
            // 签名文件不存在或解析失败，返回空数据
        }
        return SignatureFileData(variants, gridRegion)
    }
    
    private data class SignatureFileData(
        val variants: Map<String, VariantSignature>,
        val gridRegion: List<String>
    )
    
    private data class VariantSignature(
        val phash64: Long,
        val edgeDensity: Float,
        val centerEnergy: Float,
        val huMoments: DoubleArray
    )

    private fun createInnerMask(size: Int): Mat {
        val mask = Mat.zeros(size, size, CvType.CV_8UC1)
        val center = Point(size / 2.0, size / 2.0)
        // Radius: 0.35 * size => 22.4 for 64
        val radius = (size * 0.35).toInt()
        Imgproc.circle(mask, center, radius, Scalar(255.0), -1) // -1 for filled
        return mask
    }

    // ==================== Badge Detection Functions ====================

    /**
     * 红点检测调试结果（两阶段架构）
     */
    data class BadgeDebugResult(
        val hasBadge: Boolean,
        val hitCorner: String?,                      // 命中的角落 (TL/TR/BL/BR)
        val badgeType: String?,                      // 红点类型: "expand"(外扩) / "embedded"(嵌入) / null
        val gatePhase: GatePhaseResult?,             // Gate 阶段详情
        val confirmPhase: ConfirmPhaseResult?,       // Confirm 阶段详情
        val effectiveRoiSize: Int,                   // 实际使用的 ROI 尺寸
        val thresholds: Map<String, Any>,            // 使用的阈值参数
        val badgeMaskBase64: String?,                // badgeMask 可视化
        val normalizedImageBase64: String?,          // 归一化后的图像
        val overlayImageBase64: String?,             // 叠加可视化（归一化图 + 半透明红色mask）
        val processedImageBase64: String?,           // 处理后的图像（外扩:主体提取后letterbox / 嵌入:mask后的图）
        val elapsedTimeMs: Long
    )
    
    data class GatePhaseResult(
        val redCount: Int,                           // 整图红色像素数
        val redRatio: Double,                        // 整图红色像素占比
        val centroidX: Double,                       // 红色重心 X
        val centroidY: Double,                       // 红色重心 Y
        val imageWidth: Int,                         // 图像宽度
        val imageHeight: Int,                        // 图像高度
        val passedCornerIdx: Int                     // 通过 Gate 的角落索引 (-1 表示未通过)
    )
    
    data class ConfirmPhaseResult(
        val passed: Boolean,                         // 是否通过 Confirm
        val area: Double,                            // 连通域面积
        val areaRatio: Double,                       // 面积占比
        val aspectRatio: Double,                     // 宽高比
        val circularity: Double,                     // 圆度
        val fillRatio: Double,                       // 红色填充率
        val touchesEdge: Boolean                     // 是否贴边
    )

    /**
     * 公开的红点检测调试方法（两阶段架构）
     * @param bgrMat BGR 格式的图像 Mat
     * @return 详细的调试结果
     */
    fun debugDetectRedBadge(bgrMat: Mat): BadgeDebugResult {
        val startTime = System.currentTimeMillis()
        val cornerNames = arrayOf("TL", "TR", "BL", "BR")
        
        val defaultThresholds = mapOf(
            "BADGE_GATE_R_MIN" to BADGE_GATE_R_MIN,
            "BADGE_GATE_DELTA_MIN" to BADGE_GATE_DELTA_MIN,
            "BADGE_GATE_MIN_RED_PX" to BADGE_GATE_MIN_RED_PX,
            "BADGE_GATE_MIN_RATIO" to BADGE_GATE_MIN_RATIO,
            "BADGE_GATE_CLUSTER_PX" to BADGE_GATE_CLUSTER_PX,
            "BADGE_GATE_CLUSTER_RATIO" to BADGE_GATE_CLUSTER_RATIO,
            "BADGE_GATE_ROI_SIZE" to BADGE_GATE_ROI_SIZE,
            "BADGE_AREA_RATIO_MIN" to BADGE_AREA_RATIO_MIN,
            "BADGE_AREA_RATIO_MAX" to BADGE_AREA_RATIO_MAX,
            "BADGE_ASPECT_RATIO_MIN" to BADGE_ASPECT_RATIO_MIN,
            "BADGE_ASPECT_RATIO_MAX" to BADGE_ASPECT_RATIO_MAX,
            "BADGE_CIRCULARITY_MIN" to BADGE_CIRCULARITY_MIN,
            "BADGE_FILL_RATIO_MIN" to BADGE_FILL_RATIO_MIN
        )
        
        if (bgrMat.empty()) {
            return BadgeDebugResult(
                hasBadge = false, hitCorner = null, badgeType = null,
                gatePhase = null, confirmPhase = null,
                effectiveRoiSize = 0, thresholds = defaultThresholds,
                badgeMaskBase64 = null, normalizedImageBase64 = null, overlayImageBase64 = null,
                processedImageBase64 = null,
                elapsedTimeMs = System.currentTimeMillis() - startTime
            )
        }
        
        val channels = bgrMat.channels()
        if (channels != 3 && channels != 4) {
            return BadgeDebugResult(
                hasBadge = false, hitCorner = null, badgeType = null,
                gatePhase = null, confirmPhase = null,
                effectiveRoiSize = 0, thresholds = defaultThresholds + ("error" to "unsupported_channels_$channels"),
                badgeMaskBase64 = null, normalizedImageBase64 = null, overlayImageBase64 = null,
                processedImageBase64 = null,
                elapsedTimeMs = System.currentTimeMillis() - startTime
            )
        }
        
        val w = bgrMat.cols()
        val h = bgrMat.rows()
        val roiSize = BADGE_GATE_ROI_SIZE
        val effectiveRoiSize = min(roiSize, min(w, h) / 2)
        
        // ===== Gate 阶段：整图检测 =====
        // 1. 对整图生成红色 mask
        val channels3 = ArrayList<Mat>()
        Core.split(bgrMat, channels3)
        val bChannel = channels3[0]
        val gChannel = channels3[1]
        val rChannel = channels3[2]
        
        val rMask = Mat()
        Imgproc.threshold(rChannel, rMask, (BADGE_GATE_R_MIN - 1).toDouble(), 255.0, Imgproc.THRESH_BINARY)
        
        val diffRG = Mat()
        Core.subtract(rChannel, gChannel, diffRG)
        val rgMask = Mat()
        Imgproc.threshold(diffRG, rgMask, (BADGE_GATE_DELTA_MIN - 1).toDouble(), 255.0, Imgproc.THRESH_BINARY)
        diffRG.release()
        
        val diffRB = Mat()
        Core.subtract(rChannel, bChannel, diffRB)
        val rbMask = Mat()
        Imgproc.threshold(diffRB, rbMask, (BADGE_GATE_DELTA_MIN - 1).toDouble(), 255.0, Imgproc.THRESH_BINARY)
        diffRB.release()
        
        bChannel.release()
        gChannel.release()
        rChannel.release()
        
        val mask = Mat()
        Core.bitwise_and(rMask, rgMask, mask)
        Core.bitwise_and(mask, rbMask, mask)
        rMask.release()
        rgMask.release()
        rbMask.release()
        
        // 2. 计算红色像素数和重心
        val redCount = Core.countNonZero(mask)
        val totalPixels = w * h
        val redRatio = redCount.toDouble() / totalPixels
        
        var cx = 0.0
        var cy = 0.0
        var gatePassedIdx = -1
        
        if (redCount >= BADGE_GATE_CLUSTER_PX) {
            val points = Mat()
            Core.findNonZero(mask, points)
            if (!points.empty()) {
                var sumX = 0.0
                var sumY = 0.0
                val pointCount = points.rows()
                for (i in 0 until pointCount) {
                    val pt = points.get(i, 0)
                    sumX += pt[0]
                    sumY += pt[1]
                }
                cx = sumX / pointCount
                cy = sumY / pointCount
                
                // 3. 根据重心位置判断角落
                val midX = w / 2.0
                val midY = h / 2.0
                
                val cornerIdx = when {
                    cx < midX && cy < midY -> 0  // TL
                    cx >= midX && cy < midY -> 1 // TR
                    cx < midX && cy >= midY -> 2 // BL
                    else -> 3                     // BR
                }
                
                // 4. 检查重心是否在角落边缘（距离边缘 40% 以内）
                val edgeRatioX = if (cx < midX) cx / w else (w - cx) / w
                val edgeRatioY = if (cy < midY) cy / h else (h - cy) / h
                
                if (edgeRatioX <= 0.4 && edgeRatioY <= 0.4) {
                    // 5. 检查放行条件
                    val minCountThreshold = max(10, (totalPixels * 0.005).toInt())
                    if (redRatio >= 0.005 || redCount >= minCountThreshold || redCount >= BADGE_GATE_MIN_RED_PX) {
                        gatePassedIdx = cornerIdx
                    }
                }
            }
            points.release()
        }
        mask.release()
        
        val gatePhase = GatePhaseResult(
            redCount = redCount,
            redRatio = redRatio,
            centroidX = cx,
            centroidY = cy,
            imageWidth = w,
            imageHeight = h,
            passedCornerIdx = gatePassedIdx
        )
        
        // Gate 未通过
        if (gatePassedIdx < 0) {
            return BadgeDebugResult(
                hasBadge = false, hitCorner = null, badgeType = null,
                gatePhase = gatePhase, confirmPhase = null,
                effectiveRoiSize = effectiveRoiSize, thresholds = defaultThresholds,
                badgeMaskBase64 = null, normalizedImageBase64 = null, overlayImageBase64 = null,
                processedImageBase64 = null,
                elapsedTimeMs = System.currentTimeMillis() - startTime
            )
        }
        
        // ===== Confirm 阶段 =====
        val confirmResult = confirmRedBadge(bgrMat, gatePassedIdx, roiSize)
        val confirmPhase = ConfirmPhaseResult(
            passed = confirmResult.confirmed,
            area = confirmResult.area,
            areaRatio = confirmResult.areaRatio,
            aspectRatio = confirmResult.aspectRatio,
            circularity = confirmResult.circularity,
            fillRatio = confirmResult.fillRatio,
            touchesEdge = confirmResult.touchesEdge
        )
        
        val hasBadge = confirmResult.confirmed
        val hitCorner = if (hasBadge) cornerNames[gatePassedIdx] else null
        
        // 生成调试图像
        var badgeMaskBase64: String? = null
        var normalizedImageBase64: String? = null
        var overlayImageBase64: String? = null
        var processedImageBase64: String? = null
        var badgeType: String? = null
        
        if (hasBadge && confirmResult.contourMask != null) {
            confirmResult.contourMask.release()
            
            try {
                // 1. 在原始尺寸上构建 badgeMask
                val badgeMask = buildBadgeMaskOnPatch(bgrMat)
                
                // 2. 判断红点类型
                val isExpandType = isBadgeTouchingBorder(badgeMask, margin = 2)
                badgeType = if (isExpandType) "expand" else "embedded"
                
                // 3. 生成 normalizedBgr（原始 letterbox，不做红点处理）
                val normBgr = letterboxNormalizeBgr(bgrMat, TEMPLATE_SIZE)
                
                // 4. Badge Mask 缩放到 64x64
                val badgeMask64 = Mat()
                Imgproc.resize(badgeMask, badgeMask64, Size(TEMPLATE_SIZE.toDouble(), TEMPLATE_SIZE.toDouble()), 0.0, 0.0, Imgproc.INTER_NEAREST)
                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
                Imgproc.dilate(badgeMask64, badgeMask64, kernel)
                kernel.release()
                
                // 5. 归一化图像 (BGR 转 RGBA)
                val normRgba = Mat()
                Imgproc.cvtColor(normBgr, normRgba, Imgproc.COLOR_BGR2RGBA)
                val normBitmap = Bitmap.createBitmap(TEMPLATE_SIZE, TEMPLATE_SIZE, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(normRgba, normBitmap)
                normalizedImageBase64 = bitmapToBase64Png(normBitmap)
                normBitmap.recycle()
                
                // 6. Badge Mask (灰度转 RGBA)
                val maskRgba = Mat()
                Imgproc.cvtColor(badgeMask64, maskRgba, Imgproc.COLOR_GRAY2RGBA)
                val maskBitmap = Bitmap.createBitmap(TEMPLATE_SIZE, TEMPLATE_SIZE, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(maskRgba, maskBitmap)
                badgeMaskBase64 = bitmapToBase64Png(maskBitmap)
                maskBitmap.recycle()
                maskRgba.release()
                
                // 7. 叠加可视化：归一化图 + 半透明遮挡
                val overlayRgba = normRgba.clone()
                val overlayPixel = ByteArray(4)
                val maskPixel = ByteArray(1)
                for (y in 0 until TEMPLATE_SIZE) {
                    for (x in 0 until TEMPLATE_SIZE) {
                        badgeMask64.get(y, x, maskPixel)
                        if ((maskPixel[0].toInt() and 0xFF) > 128) {
                            overlayRgba.get(y, x, overlayPixel)
                            val rr = overlayPixel[0].toInt() and 0xFF
                            val gg = overlayPixel[1].toInt() and 0xFF
                            val bb = overlayPixel[2].toInt() and 0xFF
                            overlayPixel[0] = ((rr * 0.3 + 80 * 0.7).toInt().coerceIn(0, 255)).toByte()
                            overlayPixel[1] = ((gg * 0.3 + 80 * 0.7).toInt().coerceIn(0, 255)).toByte()
                            overlayPixel[2] = ((bb * 0.3 + 80 * 0.7).toInt().coerceIn(0, 255)).toByte()
                            overlayRgba.put(y, x, overlayPixel)
                        }
                    }
                }
                val overlayBitmap = Bitmap.createBitmap(TEMPLATE_SIZE, TEMPLATE_SIZE, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(overlayRgba, overlayBitmap)
                overlayImageBase64 = bitmapToBase64Png(overlayBitmap)
                overlayBitmap.recycle()
                overlayRgba.release()
                
                // 8. 【关键】生成处理后的图像
                if (isExpandType) {
                    // 外扩型：提取主体 + letterbox
                    val contentPatch = extractContentWithoutBadge(bgrMat, badgeMask)
                    val processedBgr = letterboxNormalizeBgr(contentPatch, TEMPLATE_SIZE)
                    contentPatch.release()
                    val processedRgba = Mat()
                    Imgproc.cvtColor(processedBgr, processedRgba, Imgproc.COLOR_BGR2RGBA)
                    val processedBitmap = Bitmap.createBitmap(TEMPLATE_SIZE, TEMPLATE_SIZE, Bitmap.Config.ARGB_8888)
                    Utils.matToBitmap(processedRgba, processedBitmap)
                    processedImageBase64 = bitmapToBase64Png(processedBitmap)
                    processedBitmap.recycle()
                    processedRgba.release()
                    processedBgr.release()
                } else {
                    // 嵌入型：显示 effectiveMask 应用后的效果
                    val innerMaskMat = innerMask ?: createInnerMask(TEMPLATE_SIZE).also { innerMask = it }
                    val invertedBadge = Mat()
                    Core.bitwise_not(badgeMask64, invertedBadge)
                    val effectiveMask = Mat()
                    Core.bitwise_and(innerMaskMat, invertedBadge, effectiveMask)
                    invertedBadge.release()
                    
                    // 把 effectiveMask 外的区域涂黑
                    val maskedRgba = normRgba.clone()
                    val blackPixel = byteArrayOf(0, 0, 0, 255.toByte())
                    for (y in 0 until TEMPLATE_SIZE) {
                        for (x in 0 until TEMPLATE_SIZE) {
                            effectiveMask.get(y, x, maskPixel)
                            if ((maskPixel[0].toInt() and 0xFF) < 128) {
                                maskedRgba.put(y, x, blackPixel)
                            }
                        }
                    }
                    val processedBitmap = Bitmap.createBitmap(TEMPLATE_SIZE, TEMPLATE_SIZE, Bitmap.Config.ARGB_8888)
                    Utils.matToBitmap(maskedRgba, processedBitmap)
                    processedImageBase64 = bitmapToBase64Png(processedBitmap)
                    processedBitmap.recycle()
                    maskedRgba.release()
                    effectiveMask.release()
                }
                
                normRgba.release()
                normBgr.release()
                badgeMask64.release()
                badgeMask.release()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to generate debug images", e)
            }
        }
        
        return BadgeDebugResult(
            hasBadge = hasBadge,
            hitCorner = hitCorner,
            badgeType = badgeType,
            gatePhase = gatePhase,
            confirmPhase = confirmPhase,
            effectiveRoiSize = effectiveRoiSize,
            thresholds = defaultThresholds,
            badgeMaskBase64 = badgeMaskBase64,
            normalizedImageBase64 = normalizedImageBase64,
            overlayImageBase64 = overlayImageBase64,
            processedImageBase64 = processedImageBase64,
            elapsedTimeMs = System.currentTimeMillis() - startTime
        )
    }
    
    private fun bitmapToBase64Png(bitmap: Bitmap): String {
        val baos = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        return "data:image/png;base64," + android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
    }

    /**
     * Gate 红点检测（快速筛选）
     * 整图检测方案：对整张图生成红色 mask，根据重心位置判断角落
     * 
     * 流程：
     * 1. 对整图生成红色 mask
     * 2. 计算红色像素重心 (cx, cy)
     * 3. 根据重心位置判断在哪个角落（TL/TR/BL/BR）
     * 4. 检查放行条件
     * 
     * @return 通过的角落索引 (0=TL, 1=TR, 2=BL, 3=BR)，未通过返回 -1
     */
    private fun hasRedBadgeGate(bgrPatch: Mat, roiSize: Int = BADGE_GATE_ROI_SIZE): Int {
        if (bgrPatch.empty()) return -1
        
        val channels = bgrPatch.channels()
        if (channels != 3 && channels != 4) return -1
        
        val w = bgrPatch.cols()
        val h = bgrPatch.rows()
        if (w < 8 || h < 8) return -1
        
        // 1. 对整图生成红色 mask
        val channels3 = ArrayList<Mat>()
        Core.split(bgrPatch, channels3)
        val bChannel = channels3[0]
        val gChannel = channels3[1]
        val rChannel = channels3[2]
        
        // R >= BADGE_GATE_R_MIN
        val rMask = Mat()
        Imgproc.threshold(rChannel, rMask, (BADGE_GATE_R_MIN - 1).toDouble(), 255.0, Imgproc.THRESH_BINARY)
        
        // R - G >= BADGE_GATE_DELTA_MIN
        val diffRG = Mat()
        Core.subtract(rChannel, gChannel, diffRG)
        val rgMask = Mat()
        Imgproc.threshold(diffRG, rgMask, (BADGE_GATE_DELTA_MIN - 1).toDouble(), 255.0, Imgproc.THRESH_BINARY)
        diffRG.release()
        
        // R - B >= BADGE_GATE_DELTA_MIN
        val diffRB = Mat()
        Core.subtract(rChannel, bChannel, diffRB)
        val rbMask = Mat()
        Imgproc.threshold(diffRB, rbMask, (BADGE_GATE_DELTA_MIN - 1).toDouble(), 255.0, Imgproc.THRESH_BINARY)
        diffRB.release()
        
        bChannel.release()
        gChannel.release()
        rChannel.release()
        
        // 组合条件
        val mask = Mat()
        Core.bitwise_and(rMask, rgMask, mask)
        Core.bitwise_and(mask, rbMask, mask)
        rMask.release()
        rgMask.release()
        rbMask.release()
        
        // 2. 计算红色像素数和重心
        val redCount = Core.countNonZero(mask)
        if (redCount < BADGE_GATE_CLUSTER_PX) {
            mask.release()
            return -1
        }
        
        val points = Mat()
        Core.findNonZero(mask, points)
        if (points.empty()) {
            points.release()
            mask.release()
            return -1
        }
        
        // 计算重心
        var sumX = 0.0
        var sumY = 0.0
        val pointCount = points.rows()
        for (i in 0 until pointCount) {
            val pt = points.get(i, 0)
            sumX += pt[0]
            sumY += pt[1]
        }
        val cx = sumX / pointCount
        val cy = sumY / pointCount
        points.release()
        mask.release()
        
        // 3. 根据重心位置判断在哪个角落
        // 把图像分成四个象限，重心在哪个象限就是哪个角落
        val midX = w / 2.0
        val midY = h / 2.0
        
        val cornerIdx = when {
            cx < midX && cy < midY -> 0  // TL: 左上
            cx >= midX && cy < midY -> 1 // TR: 右上
            cx < midX && cy >= midY -> 2 // BL: 左下
            else -> 3                     // BR: 右下
        }
        
        // 4. 检查重心是否真的在对应角落边缘（不是在中心区域）
        // 定义边缘区域：距离角落 40% 范围内
        val edgeRatioX = if (cx < midX) cx / w else (w - cx) / w
        val edgeRatioY = if (cy < midY) cy / h else (h - cy) / h
        
        // 重心应该在角落附近（距离边缘 40% 以内）
        if (edgeRatioX > 0.4 || edgeRatioY > 0.4) {
            return -1
        }
        
        // 5. 检查放行条件
        val totalPixels = w * h
        val ratio = redCount.toDouble() / totalPixels
        val minCountThreshold = max(10, (totalPixels * 0.005).toInt()) // 整图用 0.5%
        
        // 放行条件（满足其一即可）
        if (ratio >= 0.005 || redCount >= minCountThreshold || redCount >= BADGE_GATE_MIN_RED_PX) {
            return cornerIdx
        }
        
        return -1
    }
    
    /**
     * 数据类：Confirm 阶段结果
     */
    data class ConfirmResult(
        val confirmed: Boolean,           // 是否确认存在红点
        val contourMask: Mat?,            // 确认的连通域 mask（同 ROI 尺寸）
        val area: Double = 0.0,           // 连通域面积
        val areaRatio: Double = 0.0,      // 面积占比
        val aspectRatio: Double = 0.0,    // 宽高比
        val circularity: Double = 0.0,    // 圆度
        val fillRatio: Double = 0.0,      // 红色填充率
        val touchesEdge: Boolean = false  // 是否贴边
    )
    
    /**
     * Confirm 阶段：对指定角落 ROI 进行连通域分析，确认是否存在红点
     * @param bgrPatch 原始 BGR patch
     * @param cornerIdx 角落索引（0=TL, 1=TR, 2=BL, 3=BR）
     * @param roiSize ROI 边长
     * @return ConfirmResult 包含确认结果和连通域 mask
     */
    private fun confirmRedBadge(bgrPatch: Mat, cornerIdx: Int, roiSize: Int = BADGE_GATE_ROI_SIZE): ConfirmResult {
        val emptyResult = ConfirmResult(false, null)
        
        val channels = bgrPatch.channels()
        if (channels != 3 && channels != 4) return emptyResult
        
        val w = bgrPatch.cols()
        val h = bgrPatch.rows()
        val effectiveRoiSize = min(roiSize, min(w, h) / 2)
        if (effectiveRoiSize < 4) return emptyResult
        
        // 定义角落 ROI
        val corner = when (cornerIdx) {
            0 -> Rect(0, 0, effectiveRoiSize, effectiveRoiSize)                         // TL
            1 -> Rect(w - effectiveRoiSize, 0, effectiveRoiSize, effectiveRoiSize)      // TR
            2 -> Rect(0, h - effectiveRoiSize, effectiveRoiSize, effectiveRoiSize)      // BL
            3 -> Rect(w - effectiveRoiSize, h - effectiveRoiSize, effectiveRoiSize, effectiveRoiSize) // BR
            else -> return emptyResult
        }
        
        // 1. 在角落 ROI 内生成红色 mask
        val roiMask = Mat.zeros(effectiveRoiSize, effectiveRoiSize, CvType.CV_8UC1)
        val pixel = ByteArray(channels)
        val whitePixel = byteArrayOf(255.toByte())  // CV_8UC1 需要 byte 数组
        
        for (y in 0 until effectiveRoiSize) {
            for (x in 0 until effectiveRoiSize) {
                bgrPatch.get(corner.y + y, corner.x + x, pixel)
                val b = pixel[0].toInt() and 0xFF
                val g = pixel[1].toInt() and 0xFF
                val r = pixel[2].toInt() and 0xFF
                
                if (r >= BADGE_CONFIRM_R_MIN && (r - max(g, b)) >= BADGE_CONFIRM_DELTA_MIN) {
                    roiMask.put(y, x, whitePixel)
                }
            }
        }
        
        // 2. 形态学处理：close 填补白字造成的洞
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val closedMask = Mat()
        Imgproc.morphologyEx(roiMask, closedMask, Imgproc.MORPH_CLOSE, kernel)
        kernel.release()
        roiMask.release()
        
        // 3. findContours 提取连通域
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(closedMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()
        
        android.util.Log.d(TAG, "confirmRedBadge: contours found = ${contours.size}")
        
        if (contours.isEmpty()) {
            closedMask.release()
            return ConfirmResult(false, null, -1.0, 0.0, 0.0, 0.0, 0.0, false) // area=-1 表示无连通域
        }
        
        val roiArea = effectiveRoiSize * effectiveRoiSize.toDouble()
        var bestResult: ConfirmResult = emptyResult
        
        // 4. 对每个连通域应用过滤条件
        for ((i, contour) in contours.withIndex()) {
            val area = Imgproc.contourArea(contour)
            val areaRatio = area / roiArea
            
            android.util.Log.d(TAG, "Contour[$i]: area=$area, areaRatio=$areaRatio, roiArea=$roiArea")
            
            // 面积占比过滤 - 注意：这里改为相对于 ROI，因为红点应该占 ROI 的一定比例
            if (areaRatio < BADGE_AREA_RATIO_MIN || areaRatio > BADGE_AREA_RATIO_MAX) {
                android.util.Log.d(TAG, "  -> FILTERED by area: $areaRatio not in [${BADGE_AREA_RATIO_MIN}, ${BADGE_AREA_RATIO_MAX}]")
                continue
            }
            
            val bbox = Imgproc.boundingRect(contour)
            android.util.Log.d(TAG, "  bbox: x=${bbox.x}, y=${bbox.y}, w=${bbox.width}, h=${bbox.height}")
            
            // 计算 touchesEdge（仅用于调试返回，不作为过滤条件）
            // 因为 Gate 阶段已经在特定角落检测到红色像素，连通域存在于该角落 ROI 就足够了
            val touchesEdge = when (cornerIdx) {
                0 -> bbox.x <= BADGE_EDGE_MARGIN || bbox.y <= BADGE_EDGE_MARGIN  // TL: 贴左或贴上
                1 -> (bbox.x + bbox.width) >= effectiveRoiSize - BADGE_EDGE_MARGIN || bbox.y <= BADGE_EDGE_MARGIN  // TR: 贴右或贴上
                2 -> bbox.x <= BADGE_EDGE_MARGIN || (bbox.y + bbox.height) >= effectiveRoiSize - BADGE_EDGE_MARGIN  // BL: 贴左或贴下
                3 -> (bbox.x + bbox.width) >= effectiveRoiSize - BADGE_EDGE_MARGIN || (bbox.y + bbox.height) >= effectiveRoiSize - BADGE_EDGE_MARGIN  // BR: 贴右或贴下
                else -> false
            }
            android.util.Log.d(TAG, "  touchesEdge=$touchesEdge (not filtering)")
            // 不再使用 touchesEdge 作为过滤条件
            
            // 形状过滤：宽高比或圆度
            val aspectRatio = if (bbox.height > 0) bbox.width.toDouble() / bbox.height else 0.0
            val perimeter = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
            val circularity = if (perimeter > 0) 4 * Math.PI * area / (perimeter * perimeter) else 0.0
            android.util.Log.d(TAG, "  shape: aspectRatio=$aspectRatio, circularity=$circularity")
            
            val shapeOk = (aspectRatio in BADGE_ASPECT_RATIO_MIN..BADGE_ASPECT_RATIO_MAX) || 
                          (circularity >= BADGE_CIRCULARITY_MIN)
            if (!shapeOk) {
                android.util.Log.d(TAG, "  -> FILTERED by shape")
                continue
            }
            
            // 红色一致性：连通域内红色像素占 bbox 的比例
            val bboxArea = bbox.width * bbox.height
            var redInBbox = 0
            for (y in bbox.y until bbox.y + bbox.height) {
                for (x in bbox.x until bbox.x + bbox.width) {
                    if (y < effectiveRoiSize && x < effectiveRoiSize) {
                        bgrPatch.get(corner.y + y, corner.x + x, pixel)
                        val b = pixel[0].toInt() and 0xFF
                        val g = pixel[1].toInt() and 0xFF
                        val r = pixel[2].toInt() and 0xFF
                        if (r >= BADGE_CONFIRM_R_MIN && (r - max(g, b)) >= BADGE_CONFIRM_DELTA_MIN) {
                            redInBbox++
                        }
                    }
                }
            }
            val fillRatio = if (bboxArea > 0) redInBbox.toDouble() / bboxArea else 0.0
            android.util.Log.d(TAG, "  fill: redInBbox=$redInBbox, bboxArea=$bboxArea, fillRatio=$fillRatio")
            if (fillRatio < BADGE_FILL_RATIO_MIN) {
                android.util.Log.d(TAG, "  -> FILTERED by fill: $fillRatio < $BADGE_FILL_RATIO_MIN")
                continue
            }
            
            android.util.Log.d(TAG, "  -> PASSED all filters!")
            
            // 通过所有过滤条件，生成 contour mask
            val contourMask = Mat.zeros(effectiveRoiSize, effectiveRoiSize, CvType.CV_8UC1)
            Imgproc.drawContours(contourMask, listOf(contour), 0, Scalar(255.0), -1)
            
            // 可选：dilate 一次让遮挡更彻底
            val dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(contourMask, contourMask, dilateKernel)
            dilateKernel.release()
            
            bestResult = ConfirmResult(
                confirmed = true,
                contourMask = contourMask,
                area = area,
                areaRatio = areaRatio,
                aspectRatio = aspectRatio,
                circularity = circularity,
                fillRatio = fillRatio,
                touchesEdge = touchesEdge
            )
            break  // 只取第一个符合条件的连通域
        }
        
        // 清理
        closedMask.release()
        contours.forEach { it.release() }
        
        return bestResult
    }
    
    /**
     * 两阶段红点检测：Gate + Confirm
     * @param bgrPatch 原始 BGR patch
     * @return Pair<Boolean, Mat?> - (是否存在红点, badgeMask)
     */
    private fun detectRedBadgeTwoPhase(bgrPatch: Mat): Pair<Boolean, Mat?> {
        // Gate 阶段
        val gateCornerIdx = hasRedBadgeGate(bgrPatch)
        if (gateCornerIdx < 0) {
            return Pair(false, null)
        }
        
        // Confirm 阶段
        val confirmResult = confirmRedBadge(bgrPatch, gateCornerIdx)
        if (!confirmResult.confirmed || confirmResult.contourMask == null) {
            return Pair(false, null)
        }
        
        // 将 ROI mask 映射回 patch 尺寸的 mask
        val patchMask = buildPatchMaskFromRoiMask(
            bgrPatch, confirmResult.contourMask, gateCornerIdx, BADGE_GATE_ROI_SIZE
        )
        confirmResult.contourMask.release()
        
        return Pair(true, patchMask)
    }
    
    /**
     * 将角落 ROI 的 mask 映射回 patch 尺寸的 mask
     */
    private fun buildPatchMaskFromRoiMask(bgrPatch: Mat, roiMask: Mat, cornerIdx: Int, roiSize: Int): Mat {
        val w = bgrPatch.cols()
        val h = bgrPatch.rows()
        val effectiveRoiSize = min(roiSize, min(w, h) / 2)
        
        val patchMask = Mat.zeros(h, w, CvType.CV_8UC1)
        
        val corner = when (cornerIdx) {
            0 -> Rect(0, 0, effectiveRoiSize, effectiveRoiSize)
            1 -> Rect(w - effectiveRoiSize, 0, effectiveRoiSize, effectiveRoiSize)
            2 -> Rect(0, h - effectiveRoiSize, effectiveRoiSize, effectiveRoiSize)
            3 -> Rect(w - effectiveRoiSize, h - effectiveRoiSize, effectiveRoiSize, effectiveRoiSize)
            else -> return patchMask
        }
        
        // 将 roiMask 复制到 patchMask 的对应位置
        val targetRoi = patchMask.submat(corner)
        roiMask.copyTo(targetRoi)
        
        return patchMask
    }

    /**
     * 在 normalize 后的 BGR 图像上构建 badgeMask
     * @param normBgr 64x64 BGR Mat
     * @return CV_8UC1 mask，255=红点区域（需排除），0=正常区域
     */
    private fun buildBadgeMask64(normBgr: Mat): Mat {
        val size = normBgr.cols()
        val mask = Mat.zeros(size, size, CvType.CV_8UC1)
        
        val channels = normBgr.channels()
        // 支持 3 通道 BGR 和 4 通道 BGRA
        if (normBgr.empty() || (channels != 3 && channels != 4)) {
            return mask
        }
        
        val cornerSize = (size * BADGE_MASK_CORNER_RATIO).toInt() // 约 22 像素
        
        // 四角区域定义
        val corners = arrayOf(
            Rect(0, 0, cornerSize, cornerSize),                           // 左上
            Rect(size - cornerSize, 0, cornerSize, cornerSize),            // 右上
            Rect(0, size - cornerSize, cornerSize, cornerSize),            // 左下
            Rect(size - cornerSize, size - cornerSize, cornerSize, cornerSize) // 右下
        )
        
        // 使用 ByteArray 读取像素（CV_8U 类型需要 byte 数组）
        val pixel = ByteArray(channels)
        val whitePixel = byteArrayOf(255.toByte())  // CV_8UC1 需要 byte 数组写入
        
        for (corner in corners) {
            for (y in corner.y until corner.y + corner.height) {
                for (x in corner.x until corner.x + corner.width) {
                    normBgr.get(y, x, pixel)
                    // BGR/BGRA 格式：索引 0=B, 1=G, 2=R
                    // byte 转 unsigned int (0-255)
                    val b = pixel[0].toInt() and 0xFF
                    val g = pixel[1].toInt() and 0xFF
                    val r = pixel[2].toInt() and 0xFF
                    
                    // BGR 红色判定（使用 Confirm 阶段阈值）
                    if (r >= BADGE_CONFIRM_R_MIN && (r - max(g, b)) >= BADGE_CONFIRM_DELTA_MIN) {
                        mask.put(y, x, whitePixel)  // 修复：使用 byte 数组写入
                    }
                }
            }
        }
        
        // 轻量形态学：dilate 填补白字空洞
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        Imgproc.dilate(mask, mask, kernel)
        kernel.release()
        
        return mask
    }

    /**
     * 对 patch 做与 normalizeIconPatch 相同的裁剪/缩放，但保留 BGR 格式
     * 用于构建 badgeMask
     */
    private fun normalizeIconPatchBgr(src: Mat, outSize: Int = 64): Mat {
        if (src.empty()) {
            return Mat.zeros(outSize, outSize, CvType.CV_8UC3)
        }
        
        // 确保是 BGR 格式
        val bgr = Mat()
        when (src.channels()) {
            4 -> Imgproc.cvtColor(src, bgr, Imgproc.COLOR_RGBA2BGR)
            3 -> src.copyTo(bgr)
            1 -> Imgproc.cvtColor(src, bgr, Imgproc.COLOR_GRAY2BGR)
            else -> {
                src.copyTo(bgr)
            }
        }
        
        // 1. 转灰度用于轮廓检测
        val gray = Mat()
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)
        
        // 2. 二值化找轮廓
        val bin = Mat()
        Imgproc.adaptiveThreshold(gray, bin, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 11, 2.0)
        
        val k = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.morphologyEx(bin, bin, Imgproc.MORPH_OPEN, k)
        k.release()
        
        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(bin, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        
        // 3. 找最大轮廓
        var bestRect: Rect? = null
        var maxArea = -1.0
        
        for (c in contours) {
            val r = Imgproc.boundingRect(c)
            val area = r.width * r.height.toDouble()
            if (r.width < 4 || r.height < 4) continue
            if (area > maxArea) {
                maxArea = area
                bestRect = r
            }
        }
        
        val content: Mat = if (bestRect != null) bgr.submat(bestRect) else bgr
        
        // 4. 缩放到 80% 目标尺寸
        val targetContentSize = (outSize * 0.80).toInt()
        val aspect = content.cols().toDouble() / content.rows()
        
        var newW = targetContentSize
        var newH = targetContentSize
        
        if (aspect > 1.0) {
            newH = (newW / aspect).toInt()
        } else {
            newW = (newH * aspect).toInt()
        }
        
        val contentResized = Mat()
        Imgproc.resize(content, contentResized, Size(newW.toDouble(), newH.toDouble()))
        
        // 5. 居中填充
        val outMat = Mat.zeros(outSize, outSize, CvType.CV_8UC3)
        val xOff = (outSize - newW) / 2
        val yOff = (outSize - newH) / 2
        
        val roi = outMat.submat(Rect(xOff, yOff, newW, newH))
        contentResized.copyTo(roi)
        
        // 清理
        if (content !== bgr) content.release()
        bgr.release()
        gray.release()
        bin.release()
        contentResized.release()
        
        return outMat
    }

    private fun clampRect(r: Rect, w: Int, h: Int): Rect {
        val x = r.x.coerceIn(0, w - 1)
        val y = r.y.coerceIn(0, h - 1)
        val rw = r.width.coerceIn(1, w - x)
        val rh = r.height.coerceIn(1, h - y)
        return Rect(x, y, rw, rh)
    }

    // ==================== Signature Computation Functions ====================

    /**
     * Compute 64-bit perceptual hash using DCT algorithm.
     * 
     * Algorithm steps:
     * 1. Resize to 32x32 (captures enough detail while being fast)
     * 2. Apply DCT (Discrete Cosine Transform)
     * 3. Extract top-left 8x8 low-frequency components
     * 4. Compute median of 8x8 region (excluding DC component at [0,0])
     * 5. Generate 64-bit hash: each pixel > median = 1, else = 0
     * 
     * Why pHash:
     * - Invariant to brightness changes (DCT removes DC component)
     * - Tolerant to small deformations (only uses low frequencies)
     * - Fast computation (O(n log n) DCT + 64 comparisons)
     * - Hamming distance is O(1) with XOR + popcount
     */
    fun computePhash64(grayMasked: Mat): Long {
        // 1. Resize to 32x32
        val resized = Mat()
        Imgproc.resize(grayMasked, resized, Size(32.0, 32.0))
        
        // 2. Convert to float for DCT
        val floatMat = Mat()
        resized.convertTo(floatMat, CvType.CV_32F)
        
        // 3. Apply DCT
        val dctMat = Mat()
        Core.dct(floatMat, dctMat)
        
        // 4. Extract 8x8 low-frequency region
        val lowFreq = dctMat.submat(Rect(0, 0, 8, 8))
        
        // 5. Collect values (skip DC component at [0,0])
        val values = ArrayList<Float>(63)
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                if (row == 0 && col == 0) continue // Skip DC
                values.add(lowFreq.get(row, col)[0].toFloat())
            }
        }
        
        // 6. Compute median
        values.sort()
        val median = if (values.size % 2 == 0) {
            (values[values.size / 2 - 1] + values[values.size / 2]) / 2f
        } else {
            values[values.size / 2]
        }
        
        // 7. Generate 64-bit hash
        var hash = 0L
        var bitIndex = 0
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val value = lowFreq.get(row, col)[0].toFloat()
                if (value > median) {
                    hash = hash or (1L shl bitIndex)
                }
                bitIndex++
            }
        }
        
        // Cleanup
        resized.release()
        floatMat.release()
        dctMat.release()
        // lowFreq is a submat, don't release separately
        
        return hash
    }

    /**
     * Compute Hamming distance between two 64-bit hashes.
     * Uses XOR + population count.
     */
    fun hammingDistance(a: Long, b: Long): Int {
        return java.lang.Long.bitCount(a xor b)
    }

    /**
     * Compute edge density: ratio of non-zero pixels in edge mask to total mask pixels.
     */
    fun computeEdgeDensity(edgeMasked: Mat, mask: Mat): Float {
        val edgePixels = Core.countNonZero(edgeMasked)
        val maskPixels = Core.countNonZero(mask)
        return if (maskPixels > 0) edgePixels.toFloat() / maskPixels else 0f
    }

    /**
     * Compute center energy: ratio of edge energy in inner mask region.
     */
    fun computeCenterEnergy(edgeMasked: Mat, innerMask: Mat): Float {
        // Count non-zero in inner mask region
        val innerEdge = Mat()
        Core.bitwise_and(edgeMasked, edgeMasked, innerEdge, innerMask)
        val innerPixels = Core.countNonZero(innerEdge)
        val totalPixels = Core.countNonZero(edgeMasked)
        innerEdge.release()
        return if (totalPixels > 0) innerPixels.toFloat() / totalPixels else 0f
    }

    /**
     * Compute 7 Hu Moments for shape matching.
     * Hu Moments are invariant to translation, scale, and rotation.
     */
    fun computeHuMoments(binary: Mat): DoubleArray {
        val moments = Imgproc.moments(binary)
        val huMoments = Mat()
        Imgproc.HuMoments(moments, huMoments)
        
        val result = DoubleArray(7)
        for (i in 0 until 7) {
            result[i] = huMoments.get(i, 0)[0]
        }
        huMoments.release()
        return result
    }

    /**
     * Format phash64 as hex string with 0x prefix.
     */
    fun phashToHex(phash: Long): String {
        return "0x${phash.toULong().toString(16).padStart(16, '0')}"
    }

    /**
     * Parse hex string to phash64.
     */
    fun hexToPhash(hex: String): Long {
        val cleanHex = hex.removePrefix("0x").removePrefix("0X")
        return cleanHex.toULong(16).toLong()
    }
}
