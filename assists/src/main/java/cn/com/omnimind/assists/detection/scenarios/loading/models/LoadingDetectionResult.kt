package cn.com.omnimind.assists.detection.scenarios.loading.models

/**
 * 加载状态检测结果
 */
data class LoadingDetectionResult(
    /** 是否处于加载状态 */
    val isLoading: Boolean,
    /** 是否是大白屏 */
    val isWhiteScreen: Boolean,
    /** 是否是骨架屏 */
    val isSkeletonScreen: Boolean,
    /** 是否有 loading 指示器 */
    val hasLoadingIndicator: Boolean,
    /** 综合置信度 [0,1] */
    val confidence: Float,
    /** 详细信息 */
    val details: String
) {
    override fun toString(): String {
        return """
            LoadingDetectionResult(
                isLoading=$isLoading,
                isWhiteScreen=$isWhiteScreen,
                isSkeletonScreen=$isSkeletonScreen,
                hasLoadingIndicator=$hasLoadingIndicator,
                confidence=%.2f,
                details=$details
            )
        """.trimIndent().format(confidence)
    }
}

