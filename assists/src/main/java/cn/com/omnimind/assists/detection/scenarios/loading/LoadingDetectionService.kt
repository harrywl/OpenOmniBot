package cn.com.omnimind.assists.detection.scenarios.loading

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import cn.com.omnimind.assists.detection.OpenCVInitializer
import cn.com.omnimind.assists.detection.detectors.loading.LoadingIndicatorDetector
import cn.com.omnimind.assists.detection.detectors.loading.SkeletonScreenDetector
import cn.com.omnimind.assists.detection.detectors.loading.WhiteScreenDetector
import cn.com.omnimind.assists.detection.scenarios.loading.models.LoadingConfig
import cn.com.omnimind.assists.detection.scenarios.loading.models.LoadingDetectionResult
import cn.com.omnimind.baselib.util.OmniLog

/**
 * AccessibilityNode 数据结构（用于节点树检测）
 */
data class AccessibilityNode(
    val className: String,
    val text: String?,
    val contentDescription: String?,
    val isClickable: Boolean,
    val isVisibleToUser: Boolean,
    val isAccessibilityFocused: Boolean,
    val boundsInScreen: Rect,
    val children: List<AccessibilityNode>
)

/**
 * 加载状态检测服务
 * 综合使用多种检测器来判断页面是否处于加载状态
 */
object LoadingDetectionService {
    private const val TAG = "LoadingDetectionService"

    /**
     * 检测加载状态（仅使用 OpenCV 图像检测）
     * @param bitmap 截图
     * @param config 检测配置，默认使用标准配置
     * @param hasLoadingText 是否检测到加载相关文本（可选，用于特殊判断）
     * @return 检测结果
     */
    fun detectLoadingState(
        bitmap: Bitmap?,
        config: LoadingConfig = LoadingConfig(),
        hasLoadingText: Boolean = false
    ): LoadingDetectionResult {
        return detectLoadingStateInternal(bitmap, config, null, hasLoadingText)
    }

    /**
     * 检测加载状态（结合 OpenCV 图像检测和 AccessibilityNode 检测）
     * @param bitmap 截图
     * @param rootNode AccessibilityNode 根节点（可选，用于节点树检测）
     * @param config 检测配置，默认使用标准配置
     * @return 检测结果
     */
    fun detectLoadingState(
        bitmap: Bitmap?,
        rootNode: AccessibilityNode?,
        config: LoadingConfig = LoadingConfig()
    ): LoadingDetectionResult {
        // 从节点树中检测加载文本
        val hasLoadingText = rootNode?.let { findLoadingTexts(it).isNotEmpty() } ?: false
        return detectLoadingStateInternal(bitmap, config, rootNode, hasLoadingText)
    }

    /**
     * 检测加载状态（从 AccessibilityNodeInfo 构建节点树）
     * @param bitmap 截图
     * @param rootNodeInfo AccessibilityNodeInfo 根节点（可选，用于节点树检测）
     * @param config 检测配置，默认使用标准配置
     * @return 检测结果
     */
    fun detectLoadingState(
        bitmap: Bitmap?,
        rootNodeInfo: AccessibilityNodeInfo?,
        config: LoadingConfig = LoadingConfig()
    ): LoadingDetectionResult {
        val rootNode = rootNodeInfo?.let { buildNodeTree(it) }
        return detectLoadingState(bitmap, rootNode, config)
    }

    /**
     * 内部检测方法（统一处理 OpenCV 和节点检测）
     */
    private fun detectLoadingStateInternal(
        bitmap: Bitmap?,
        config: LoadingConfig,
        rootNode: AccessibilityNode?,
        hasLoadingText: Boolean
    ): LoadingDetectionResult {
        OpenCVInitializer.ensureInitialized()

        if (bitmap?.isRecycled == true) {
            OmniLog.w(TAG, "Bitmap is recycled")
            return LoadingDetectionResult(
                isLoading = false,
                isWhiteScreen = false,
                isSkeletonScreen = false,
                hasLoadingIndicator = false,
                confidence = 0f,
                details = "Bitmap is recycled"
            )
        }

        val details = mutableListOf<String>()
        var confidence = 0f

        // 0. 节点树检测（如果提供了 rootNode）
        var nodeCountScore = 0f
        var hasProgressIndicator = false
        if (rootNode != null) {
            // 节点数检测：节点越多得分越低，3 个≈0.6 分，12 个≈0.1 分
            val nodeCount = countNodes(rootNode)
            nodeCountScore = nodeCountScore(nodeCount)
            confidence += nodeCountScore
            details.add(
                "节点数: ${if (nodeCount > 12) "大于12" else nodeCount}, " +
                        "贡献分数: ${String.format("%.2f", nodeCountScore)}"
            )

            // 进度指示器检测
            val progressIndicators = findProgressIndicators(rootNode)
            hasProgressIndicator = progressIndicators.isNotEmpty()
            if (hasProgressIndicator) {
                details.add("发现进度指示器组件")
                // 进度指示器权重：0.25
                confidence += 0.25f
            }
        }

        // 特殊判断：直接判定为 loading 的情况
        var forceLoading = false
        var isWhiteScreen = false
        var isSkeletonScreen = false
        var hasLoadingIndicator = false

        val forceLoadingReasons = mutableListOf<String>()
        if (bitmap != null) {

            // 1. 检测大白屏
            var whiteScreenConfidence = 0f
            if (config.enableWhiteScreenDetection) {
                val whiteScreenResult = if (config.useFastMode) {
                    WhiteScreenDetector.detectFast(bitmap, config.downscaleRatio)
                } else {
                    WhiteScreenDetector.detect(
                        bitmap,
                        config.whiteScreenThreshold,
                        config.whiteColorThreshold
                    )
                }

                isWhiteScreen = whiteScreenResult.isWhiteScreen
                whiteScreenConfidence = whiteScreenResult.confidence

                if (isWhiteScreen) {
                    details.add(
                        "检测到大白屏(置信度: ${
                            String.format(
                                "%.2f",
                                whiteScreenConfidence
                            )
                        })"
                    )
                    // 纯色屏权重：0.35
                    confidence += whiteScreenConfidence * 0.35f
                }
            }

            // 2. 检测骨架屏
            var skeletonScreenConfidence = 0f
            if (config.enableSkeletonScreenDetection) {
                val skeletonResult = if (config.useFastMode) {
                    SkeletonScreenDetector.detectFast(bitmap, config.downscaleRatio)
                } else {
                    SkeletonScreenDetector.detect(
                        bitmap,
                        scale = config.downscaleRatio,
                        edgeRatioThreshold = config.skeletonEdgeRatioThreshold,
                        varianceThreshold = config.skeletonVarianceThreshold,
                        minRectangles = config.skeletonMinRectangles
                    )
                }

                isSkeletonScreen = skeletonResult.isSkeletonScreen
                skeletonScreenConfidence = skeletonResult.confidence

                if (isSkeletonScreen) {
                    details.add(
                        "检测到骨架屏(置信度: ${
                            String.format(
                                "%.2f",
                                skeletonScreenConfidence
                            )
                        }, " +
                                "矩形数: ${skeletonResult.rectangleCount})"
                    )
                    // 骨架屏权重：0.5（提高权重）
                    confidence += skeletonScreenConfidence * 0.5f
                }
            }

            // 3. 检测 loading 指示器
            var loadingIndicatorConfidence = 0f
            if (config.enableLoadingIndicatorDetection) {
                val indicatorResult = LoadingIndicatorDetector.detect(
                    bitmap,
                    minRadius = config.loadingMinRadius,
                    maxRadius = config.loadingMaxRadius,
                    minCircles = config.loadingMinCircles
                )

                hasLoadingIndicator = indicatorResult.hasLoadingIndicator
                loadingIndicatorConfidence = indicatorResult.confidence

                if (hasLoadingIndicator) {
                    details.add(
                        "检测到Loading指示器(置信度: ${
                            String.format(
                                "%.2f",
                                loadingIndicatorConfidence
                            )
                        }, " +
                                "圆形数: ${indicatorResult.circleCount}, 其他形状: ${indicatorResult.otherShapesCount})"
                    )
                    // 指示器权重：0.3
                    confidence += loadingIndicatorConfidence * 0.3f
                } else if (config.enableRotatingIndicatorDetection) {
                    // 如果标准检测没有发现，尝试增强检测
                    val rotatingResult = LoadingIndicatorDetector.detectRotating(
                        bitmap,
                        minRadius = config.loadingMinRadius,
                        maxRadius = config.loadingMaxRadius
                    )

                    if (rotatingResult.hasLoadingIndicator) {
                        hasLoadingIndicator = true
                        loadingIndicatorConfidence = rotatingResult.confidence
                        details.add(
                            "检测到旋转Loading指示器(置信度: ${
                                String.format(
                                    "%.2f",
                                    loadingIndicatorConfidence
                                )
                            }, " +
                                    "圆形数: ${rotatingResult.circleCount})"
                        )
                        confidence += loadingIndicatorConfidence * 0.3f
                    }
                }

            }

            // 1. 骨架屏可信度 > 50%
            if (isSkeletonScreen && skeletonScreenConfidence > 0.5f) {
                forceLoading = true
                forceLoadingReasons.add(
                    "骨架屏置信度 > 50% (${
                        String.format(
                            "%.2f",
                            skeletonScreenConfidence
                        )
                    })"
                )
            }
            // 3. 纯色和指示器同时出现
            if (isWhiteScreen && hasLoadingIndicator) {
                forceLoading = true
                forceLoadingReasons.add("纯色屏和指示器同时出现")
                details.add("纯色屏和指示器同时出现(强制判定为loading)")
            }
        }


        // 2. 检测到加载相关文本
        if (hasLoadingText) {
            forceLoading = true
            forceLoadingReasons.add("检测到加载相关文本")
            details.add("检测到加载相关文本")
        }



        // 综合判断
        // 如果满足特殊条件，直接判定为 loading
        // 否则使用评分阈值（默认 0.6）
        val threshold = config.loadingThreshold.coerceAtLeast(0.6f) // 确保阈值至少为 0.6
        val isLoading = if (forceLoading) {
            true
        } else {
            confidence >= threshold
        }

        confidence = confidence.coerceIn(0f, 1f)

        // 如果没有检测到任何加载特征，添加说明
        if (details.isEmpty()) {
            details.add("未检测到加载特征")
        }

        // 如果有强制判定原因，添加到详情中
        if (forceLoadingReasons.isNotEmpty()) {
            details.add("强制判定原因: ${forceLoadingReasons.joinToString(", ")}")
        }

        val result = LoadingDetectionResult(
            isLoading = isLoading,
            isWhiteScreen = isWhiteScreen,
            isSkeletonScreen = isSkeletonScreen,
            hasLoadingIndicator = hasLoadingIndicator,
            confidence = confidence,
            details = details.joinToString("; ")
        )

        OmniLog.d(TAG, "Loading detection result: $result")

        return result
    }

    /**
     * 快速检测（使用快速模式和更激进的降采样）
     * @param bitmap 截图
     * @return 检测结果
     */
    fun detectLoadingStateFast(bitmap: Bitmap): LoadingDetectionResult {
        val fastConfig = LoadingConfig(
            useFastMode = true,
            downscaleRatio = 0.3f
        )
        return detectLoadingState(bitmap, fastConfig)
    }

    /**
     * 检测结果（包含详细结果）
     * 用于调试展示，避免重复调用检测器
     */
    data class DetailedLoadingDetectionResult(
        val result: LoadingDetectionResult,
        val whiteScreenResult: WhiteScreenDetector.WhiteScreenResult?,
        val skeletonScreenResult: SkeletonScreenDetector.SkeletonScreenResult?,
        val loadingIndicatorResult: LoadingIndicatorDetector.LoadingIndicatorResult?
    )

    /**
     * 快速检测并返回详细结果（用于调试展示）
     * 只调用一次检测器，避免重复调用导致性能问题
     * @param bitmap 截图
     * @return 详细检测结果
     */
    fun detectLoadingStateFastWithDetails(bitmap: Bitmap): DetailedLoadingDetectionResult {
        OpenCVInitializer.ensureInitialized()

        if (bitmap.isRecycled) {
            OmniLog.w(TAG, "Bitmap is recycled")
            val emptyResult = LoadingDetectionResult(
                isLoading = false,
                isWhiteScreen = false,
                isSkeletonScreen = false,
                hasLoadingIndicator = false,
                confidence = 0f,
                details = "Bitmap is recycled"
            )
            return DetailedLoadingDetectionResult(
                result = emptyResult,
                whiteScreenResult = null,
                skeletonScreenResult = null,
                loadingIndicatorResult = null
            )
        }

        val fastConfig = LoadingConfig(
            useFastMode = true,
            downscaleRatio = 0.3f
        )

        // 只调用一次检测器，保存详细结果
        val whiteScreenResult = if (fastConfig.enableWhiteScreenDetection) {
            WhiteScreenDetector.detectFast(bitmap, fastConfig.downscaleRatio)
        } else {
            null
        }

        val skeletonScreenResult = if (fastConfig.enableSkeletonScreenDetection) {
            SkeletonScreenDetector.detectFast(bitmap, fastConfig.downscaleRatio)
        } else {
            null
        }

        val loadingIndicatorResult = if (fastConfig.enableLoadingIndicatorDetection) {
            LoadingIndicatorDetector.detect(
                bitmap,
                minRadius = fastConfig.loadingMinRadius,
                maxRadius = fastConfig.loadingMaxRadius,
                minCircles = fastConfig.loadingMinCircles
            )
        } else {
            null
        }

        // 使用检测结果计算综合结果
        val details = mutableListOf<String>()
        var confidence = 0f

        val isWhiteScreen = whiteScreenResult?.isWhiteScreen ?: false
        val isSkeletonScreen = skeletonScreenResult?.isSkeletonScreen ?: false
        val hasLoadingIndicator = loadingIndicatorResult?.hasLoadingIndicator ?: false

        var whiteScreenConfidence = 0f
        var skeletonScreenConfidence = 0f
        var loadingIndicatorConfidence = 0f

        if (isWhiteScreen) {
            whiteScreenConfidence = whiteScreenResult?.confidence ?: 0f
            details.add("检测到大白屏(置信度: ${String.format("%.2f", whiteScreenConfidence)})")
            // 纯色屏权重：0.35
            confidence += whiteScreenConfidence * 0.35f
        }

        if (isSkeletonScreen) {
            skeletonScreenConfidence = skeletonScreenResult?.confidence ?: 0f
            val rectangleCount = skeletonScreenResult?.rectangleCount ?: 0
            details.add(
                "检测到骨架屏(置信度: ${String.format("%.2f", skeletonScreenConfidence)}, " +
                        "矩形数: $rectangleCount)"
            )
            // 骨架屏权重：0.5
            confidence += skeletonScreenConfidence * 0.5f
        }

        if (hasLoadingIndicator) {
            loadingIndicatorConfidence = loadingIndicatorResult?.confidence ?: 0f
            val circleCount = loadingIndicatorResult?.circleCount ?: 0
            val otherShapesCount = loadingIndicatorResult?.otherShapesCount ?: 0
            details.add(
                "检测到Loading指示器(置信度: ${
                    String.format(
                        "%.2f",
                        loadingIndicatorConfidence
                    )
                }, " +
                        "圆形数: $circleCount, 其他形状: $otherShapesCount)"
            )
            // 指示器权重：0.3
            confidence += loadingIndicatorConfidence * 0.3f
        }

        // 特殊判断：直接判定为 loading 的情况
        var forceLoading = false
        val forceLoadingReasons = mutableListOf<String>()

        // 1. 骨架屏可信度 > 50%
        if (isSkeletonScreen && skeletonScreenConfidence > 0.5f) {
            forceLoading = true
            forceLoadingReasons.add(
                "骨架屏置信度 > 50% (${
                    String.format(
                        "%.2f",
                        skeletonScreenConfidence
                    )
                })"
            )
        }

        // 2. 检测到加载相关文本（此方法中无法检测，由调用方传入）
        // 注意：此方法不接收 hasLoadingText 参数，因为它是用于调试展示的

        // 3. 纯色和指示器同时出现
        if (isWhiteScreen && hasLoadingIndicator) {
            forceLoading = true
            forceLoadingReasons.add("纯色屏和指示器同时出现")
            details.add("纯色屏和指示器同时出现(强制判定为loading)")
        }

        if (details.isEmpty()) {
            details.add("未检测到加载特征")
        }

        // 如果有强制判定原因，添加到详情中
        if (forceLoadingReasons.isNotEmpty()) {
            details.add("强制判定原因: ${forceLoadingReasons.joinToString(", ")}")
        }

        // 综合判断
        val threshold = fastConfig.loadingThreshold.coerceAtLeast(0.6f) // 确保阈值至少为 0.6
        val isLoading = if (forceLoading) {
            true
        } else {
            confidence >= threshold
        }

        confidence = confidence.coerceIn(0f, 1f)

        val result = LoadingDetectionResult(
            isLoading = isLoading,
            isWhiteScreen = isWhiteScreen,
            isSkeletonScreen = isSkeletonScreen,
            hasLoadingIndicator = hasLoadingIndicator,
            confidence = confidence,
            details = details.joinToString("; ")
        )

        return DetailedLoadingDetectionResult(
            result = result,
            whiteScreenResult = whiteScreenResult,
            skeletonScreenResult = skeletonScreenResult,
            loadingIndicatorResult = loadingIndicatorResult
        )
    }

    // ========== AccessibilityNode 检测相关方法 ==========

    /**
     * 统计节点数量
     * @param node 当前节点
     * @param depth 递归深度，默认0
     * @return 返回节点数量
     */
    private fun countNodes(node: AccessibilityNode, depth: Int = 0): Int {
        // 递归深度限制：深度小于13时才继续递归
        if (depth >= 13) {
            return 13
        }

        var total = 1
        node.children.forEach { child ->
            total += countNodes(child, depth + 1)
            if (total >= 13) {
                return 13
            }
        }
        return total
    }

    /**
     * 线性插值节点分数：3 个节点≈0.6 分，12 个节点≈0.1 分，超界则裁剪。
     */
    private fun nodeCountScore(count: Int): Float {
        val minCount = 3f
        val maxCount = 12f
        val maxScore = 0.6f
        val minScore = 0.0f
        if (count <= minCount) return maxScore
        if (count >= maxCount) return minScore
        val slope = (minScore - maxScore) / (maxCount - minCount) // -0.5 / 9
        return maxScore + slope * (count - minCount)
    }

    /**
     * 查找进度指示器（限制最大查找数量，避免遍历过多节点）
     */
    private fun findProgressIndicators(node: AccessibilityNode, maxCount: Int = 5, currentCount: Int = 0): List<AccessibilityNode> {
        if (!node.isVisibleToUser || currentCount >= maxCount) return emptyList()

        val indicators = mutableListOf<AccessibilityNode>()

        if (isProgressIndicator(node)) {
            indicators.add(node)
            // 进度指示器通常只有1-2个，找到后可以提前终止
            if (indicators.size >= maxCount) {
                return indicators
            }
        }

        // 只遍历可见的子节点
        node.children.filter { it.isVisibleToUser }.forEach { child ->
            if (currentCount + indicators.size < maxCount) {
                indicators.addAll(findProgressIndicators(child, maxCount, currentCount + indicators.size))
            }
        }

        return indicators
    }

    /**
     * 判断是否是进度指示器
     */
    private fun isProgressIndicator(node: AccessibilityNode): Boolean {
        val className = node.className.lowercase()
        val contentDesc = node.contentDescription?.lowercase() ?: ""
        val text = node.text?.lowercase() ?: ""

        val progressClasses = listOf(
            "progressbar", "loadingindicator",
            "progress", "loading", "circularprogress"
        )

        val loadingKeywords = listOf(
            "loading", "progress", "正在加载", "加载中",
            "loading...", "progress", "progressing", "搜索中"
        )

        return progressClasses.any { className.contains(it) } ||
                loadingKeywords.any { contentDesc.contains(it) || text.contains(it) }
    }

    /**
     * 查找加载相关文本（限制最大查找数量，避免遍历过多节点）
     */
    private fun findLoadingTexts(node: AccessibilityNode, maxCount: Int = 10, currentCount: Int = 0): List<AccessibilityNode> {
        if (!node.isVisibleToUser || currentCount >= maxCount) return emptyList()

        val loadingNodes = mutableListOf<AccessibilityNode>()

        val text = node.text?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.lowercase() ?: ""

        val loadingKeywords = listOf(
            "loading", "正在加载", "加载中", "loading...",
            "progress", "正在处理", "processing",
            "wait", "请稍候", "请等待", "搜索中", "稍等片刻",
            "查询中", "正在查询"
        )

        if (loadingKeywords.any { text.contains(it) || contentDesc.contains(it) }) {
            loadingNodes.add(node)
            // 如果找到足够的加载文本，可以提前终止
            if (loadingNodes.size >= maxCount) {
                return loadingNodes
            }
        }

        // 只遍历可见的子节点
        node.children.filter { it.isVisibleToUser }.forEach { child ->
            if (currentCount + loadingNodes.size < maxCount) {
                loadingNodes.addAll(findLoadingTexts(child, maxCount, currentCount + loadingNodes.size))
            }
        }

        return loadingNodes
    }

    /**
     * 从 AccessibilityNodeInfo 构建 AccessibilityNode 树（优化版本：限制深度，只构建可见节点）
     * Loading 检测的关键元素通常不会在很深的层级中，限制深度可以大幅提升性能
     * 注意：与 countNodes 的深度限制（13层）保持一致，但实际构建时使用更严格的限制（6层）
     */
    private fun buildNodeTree(info: AccessibilityNodeInfo, maxDepth: Int = 6, currentDepth: Int = 0): AccessibilityNode {
        val bounds = Rect().also { info.getBoundsInScreen(it) }

        // 如果达到最大深度，不再构建子节点
        if (currentDepth >= maxDepth) {
            return AccessibilityNode(
                className = info.className?.toString() ?: "",
                text = info.text?.toString(),
                contentDescription = info.contentDescription?.toString(),
                isClickable = info.isClickable,
                isVisibleToUser = info.isVisibleToUser,
                isAccessibilityFocused = info.isAccessibilityFocused,
                boundsInScreen = bounds,
                children = emptyList()
            )
        }

        val children = mutableListOf<AccessibilityNode>()
        val childCount = info.childCount
        
        // 限制每个节点的最大子节点数量，避免构建过于庞大的子树
        val maxChildren = if (currentDepth < 3) 50 else 20  // 浅层可以更多，深层限制更严格
        
        for (i in 0 until minOf(childCount, maxChildren)) {
            val childInfo = try {
                info.getChild(i)
            } catch (e: Exception) {
                null
            } ?: continue

            try {
                // 只构建可见的节点，不可见节点通常不需要检测
                if (childInfo.isVisibleToUser) {
                    children.add(buildNodeTree(childInfo, maxDepth, currentDepth + 1))
                }
            } catch (e: Exception) {
                OmniLog.w(TAG, "构建子节点时出错: ${e.message}")
            } finally {
                // 避免节点引用泄漏
                childInfo.recycle()
            }
        }

        return AccessibilityNode(
            className = info.className?.toString() ?: "",
            text = info.text?.toString(),
            contentDescription = info.contentDescription?.toString(),
            isClickable = info.isClickable,
            isVisibleToUser = info.isVisibleToUser,
            isAccessibilityFocused = info.isAccessibilityFocused,
            boundsInScreen = bounds,
            children = children
        )
    }
}

