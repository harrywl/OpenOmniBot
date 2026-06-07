package cn.com.omnimind.assists.detection.scenarios.stability

/**
 * 页面稳定性检测结果
 */
data class PageStabilityResult(
    /** 是否稳定 */
    val isStable: Boolean,
    /** XML 相似度 [0,1] */
    val xmlSim: Double,
    /** 整屏视觉差异占比 [0,1] */
    val diffRatioWhole: Double,
    /** 动态区域占比 [0,1]（已废弃，固定为 0） */
    val dynamicAreaRatio: Double,
    /** 连续稳定次数 */
    val stableHits: Int,
    /** 调试信息（用于面板展示） */
    val reasons: String
) {
    override fun toString(): String {
        val diffStr = if (diffRatioWhole < 0.0) "N/A" else String.format("%.4f", diffRatioWhole)
        return """
            PageStabilityResult(
                isStable=$isStable,
                xmlSim=%.4f,
                diffWhole=$diffStr,
                dynamicArea=%.3f,
                stableHits=$stableHits,
                reasons=$reasons
            )
        """.trimIndent().format(xmlSim, dynamicAreaRatio)
    }
}

/**
 * 稳定性检测配置（简化版：以 XML 为主，视觉为辅）
 */
data class StabilityConfig(
    /** 降采样宽度（性能优化） */
    val downscaleW: Int = 360,
    /** 采样间隔（毫秒） */
    val intervalMs: Long = 333,
    /** 窗口变化后的冷却时间（毫秒） */
    val cooldownMs: Long = 260,
    /** 需要连续稳定的次数（正常模式：xml + vis） */
    val stableHitsRequired: Int = 1,
    /** 软超时：进入 XML-only 模式的时间阈值（毫秒） - 非视频页面 */
    val maxWaitMs: Long = 3000,
    /** 硬超时：强制返回的最大等待时间（毫秒） */
    val hardTimeoutMs: Long = 5000,

    /** XML 稳定阈值 */
    val xmlStableThr: Double = 0.8,
    /** XML-only 模式下需要连续稳定的次数 */
    val xmlStableHitsRequired: Int = 1,

    /** 像素差异阈值（灰度值差异） */
    val pixelDiffThr: Double = 22.0,
    /** 视觉稳定阈值（整屏 diff 占比） */
    val visualStableThr: Double = 0.1,

    /** 视频特征检测：是否启用 */
    val videoHintEnabled: Boolean = true,
    /** 视频特征检测：检测到视频时，视觉等待最大时间（毫秒） */
    val videoHintMaxVisWaitMs: Long = 1500,
    /** 视频特征检测：节点数过少时直接认为无视频 */
    val videoHintMinNodes: Int = 30,
    /** 视频特征检测：遍历节点上限（防性能问题） */
    val videoHintMaxTraverseNodes: Int = 800
)
