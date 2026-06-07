package cn.com.omnimind.assists.util

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import cn.com.omnimind.assists.controller.accessibility.AccessibilityController
import cn.com.omnimind.assists.detection.scenarios.loading.AccessibilityNode
import cn.com.omnimind.assists.detection.scenarios.loading.LoadingDetectionService
import cn.com.omnimind.assists.detection.scenarios.loading.models.LoadingConfig
import cn.com.omnimind.baselib.util.OmniLog

data class LoadingDetectionResult(
    val isLoading: Boolean, val confidence: Float, val reasons: List<String>
)

object LoadingCheckUtil {
    /**
     * 将 AssistsService 的 rootInActiveWindow 转换为内部节点并执行加载检测
     */
    suspend fun detectLoadingState(rootNodeInfo: AccessibilityNodeInfo?): LoadingDetectionResult? {
        val rootNode = rootNodeInfo?.let { buildNodeTree(it) } ?: return null
        return LoadingStateDetector().detectLoadingState(rootNode)
    }

    /**
     * 构建节点树（优化版本：限制深度，只构建可见节点）
     * Loading 检测的关键元素通常不会在很深的层级中，限制深度可以大幅提升性能
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
                OmniLog.w("LoadingCheckUtil", "构建子节点时出错: ${e.message}")
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

class LoadingStateDetector {

    suspend fun detectLoadingState(rootNode: AccessibilityNode): LoadingDetectionResult {
        OmniLog.i("LoadingStateDetector", "detectLoadingState ${rootNode}")
        val reasons = mutableListOf<String>()
        var score = 0f
        var isLoading = false

        try {
            val image = AccessibilityController.captureScreenshotImage(
                isBitmap = false,
                isFile = false,
                isFilterOverlay = true,
                isCheckSingleColor = false,
                isCheckMostlyLightBackground = false,
                isCheckSideRegionMostlySingleColor = false
            )
           val result = LoadingDetectionService.detectLoadingState(image.imageBitmap, rootNode, LoadingConfig())
            isLoading=result.isLoading
            reasons.add(result.details)
            score=result.confidence
        } catch (_: Exception) {
            // ignore image check failure
        }

        return LoadingDetectionResult(
            isLoading = isLoading, confidence = minOf(score, 1.0f), reasons = reasons
        )
    }



}
