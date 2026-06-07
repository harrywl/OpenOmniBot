package cn.com.omnimind.assists.detection.scenarios.loading.models

/**
 * 加载状态检测配置
 */
data class LoadingConfig(
    /** 是否启用大白屏检测 */
    val enableWhiteScreenDetection: Boolean = true,
    /** 是否启用骨架屏检测 */
    val enableSkeletonScreenDetection: Boolean = true,
    /** 是否启用 loading 指示器检测 */
    val enableLoadingIndicatorDetection: Boolean = true,
    /** 是否启用旋转 loading 指示器检测（增强检测） */
    val enableRotatingIndicatorDetection: Boolean = true,
    /** 综合判断阈值：置信度达到此值才认为处于加载状态（默认 0.6） */
    val loadingThreshold: Float = 0.6f,
    /** 大白屏检测：白色占比阈值 */
    val whiteScreenThreshold: Double = 0.85,
    /** 大白屏检测：白色颜色阈值（灰度值） */
    val whiteColorThreshold: Int = 240,
    /** 骨架屏检测：边缘占比阈值 */
    val skeletonEdgeRatioThreshold: Double = 0.15,
    /** 骨架屏检测：灰度方差阈值 */
    val skeletonVarianceThreshold: Double = 500.0,
    /** 骨架屏检测：最小矩形数量 */
    val skeletonMinRectangles: Int = 3,
    /** Loading 指示器检测：最小圆半径 */
    val loadingMinRadius: Int = 10,
    /** Loading 指示器检测：最大圆半径 */
    val loadingMaxRadius: Int = 100,
    /** Loading 指示器检测：最小圆形数量 */
    val loadingMinCircles: Int = 1,
    /** 性能优化：降采样比例（用于快速检测） */
    val downscaleRatio: Float = 0.5f,
    /** 是否使用快速检测模式 */
    val useFastMode: Boolean = false
)

