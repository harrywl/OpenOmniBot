package cn.com.omnimind.assists.detection.detectors.popup.models

import org.opencv.core.Mat
import org.opencv.core.Rect

// =============== 数据结构 ===============

data class RectFNorm(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun width() = right - left
    fun height() = bottom - top
}

enum class MaskAdType {
    NONE,
    SHEET,
    CENTER_POPUP,
    FULLSCREEN_OR_UNKNOWN
}

// =============== 新架构数据结构 ===============

// 1. 共享预处理结果
data class SharedPreprocessResult(
    val roi: Mat,              // ROI 区域（BGR）
    val roiRect: Rect,         // ROI 位置（原图坐标）
    val hsvV: Mat,             // V 通道（ROI）
    val hsvS: Mat,             // S 通道（ROI）
    val rowMeanV: DoubleArray, // 每行平均 V 值
    val fullHeight: Int,       // 原图高度
    val fullWidth: Int         // 原图宽度
)

// 2. Sheet 证据
data class SheetEvidence(
    val splitY: Int,           // 分割线 Y 坐标（ROI 坐标系）
    val topMeanV: Double,      // 上半部平均 V
    val bottomMeanV: Double,   // 下半部平均 V
    val delta: Double,         // 上下亮度差
    val coverBright: Float,    // 下半部亮区覆盖率
    val coverDark: Float,      // 上半部暗区覆盖率
    val bottomEdgeRatio: Float,// bbox 贴底比例
    val leftRightEdgeRatio: Float, // bbox 贴左右比例
    val sheetEnv: Float        // Sheet 环境因子
)

// 3. Sheet 检测结果
data class SheetDetectResult(
    val score: Float,          // 0~1
    val bbox: Rect?,           // ROI 坐标系
    val evidence: SheetEvidence?,
    val debugRowMeanPlot: Mat? = null  // rowMeanV 曲线 + bestY 竖线
)

// 4. Popup 证据
data class PopupEvidence(
    val ringOk: Boolean,
    val deltaV: Double,
    val ccFar: Float,
    val ccNear: Float,
    val areaRatio: Float,
    val distRatio: Float,
    val insideMeanV: Double,
    val ringMeanV: Double,
    val popupEnv: Float,        // Popup 环境因子

    // 新增形状特征
    val extent: Float,          // area / bboxArea (圆角矩形会低于1)
    val solidity: Float,        // area / convexHullArea (圆角一般高)
    val circularity: Float,     // 4π*area/(perimeter^2)
    val candQuality: Float,     // 候选综合质量分

    // 新增 ring 质量特征
    val ringDarkRatio: Float,   // ring区域暗像素占比（防误连通）

    // 新增打分项（证据透明化）
    val ringScore: Float,       // ring证据分 (0~1)
    val dvScore: Float,         // deltaV证据分 (0~1)
    val distScore: Float,       // 位置先验分 (0~1)
    val areaScore: Float,       // 尺寸先验分 (0~1)
    val shapeBonus: Float,      // 形状加分 (0~0.08)

    // 新增 reject 原因（若被拒绝）
    val rejectReason: String?   // null 表示未被 reject
)

// 5. Popup 检测结果
data class PopupDetectResult(
    val score: Float,          // 0~1
    val bbox: Rect?,           // ROI 坐标系
    val evidence: PopupEvidence?,
    val debugPopupBin: Mat? = null,      // Otsu 后的二值图
    val debugPopupMorph: Mat? = null,    // 开闭运算后的二值图
    val debugRingDarkMask: Mat? = null,  // outerRect 的 darkMask
    val debugRingNear: Mat? = null,      // darkNear
    val debugRingFar: Mat? = null        // darkFar
)

// 6. 候选结果
data class MaskCandidate(
    val type: MaskAdType,
    val score: Float,
    val bboxNorm: RectFNorm?,
    val evidence: Map<String, Any>
)

// 7. 最终结果
data class MaskDetectResult(
    val candidates: List<MaskCandidate>,
    val final: FinalDecision,
    val elapsedTimeMs: Long,
    val debug: Map<String, String> = emptyMap()
)

data class FinalDecision(
    val type: MaskAdType,
    val score: Float,
    val bboxNorm: RectFNorm?,
    val reason: String
)

data class MaskDetectConfig(
    // ROI：只裁系统栏那一小条，避免把 sheet 关键区域裁掉
    val cropTopRatio: Float = 0.06f,
    val cropBottomRatio: Float = 0.04f,

    // Sheet 检测参数
    val sheetMinDelta: Double = 30.0,           // 上下亮度差阈值
    val sheetMinBrightCover: Float = 0.35f,     // 下半部亮区最小覆盖率
    val sheetMinDarkCover: Float = 0.25f,       // 上半部暗区最小覆盖率
    val sheetEdgeThreshold: Float = 0.85f,      // 贴边阈值（左右+底部）

    // 中心弹窗连通域
    val minPopupAreaRatio: Float = 0.05f,
    val maxPopupAreaRatio: Float = 0.65f,
    val centerMaxDistRatio: Float = 0.22f,        // bbox中心到屏幕中心的最大距离（相对对角线）
    val minInsideRingDeltaV: Double = 14.0,       // inside vs ring 亮度差

    // 双圈暗连续性验证
    val outerExpandRatio: Float = 0.28f,          // outer扩张比例（相对bbox短边）
    val outerExpandMinPx: Int = 18,               // outer最小扩张像素
    val bufferExpandRatio: Float = 0.35f,         // buffer/outer比例（near/far分界）
    val bufferExpandMinPx: Int = 8,               // buffer最小扩张像素
    val darkThresholdT: Double = 10.0,            // 自适应暗阈值：ringMean + t
    val farRingCCLo: Float = 0.45f,               // farRing软评分下界
    val farRingCCHi: Float = 0.75f,               // farRing软评分上界
    val nearRingCCPenaltyThr: Float = 0.10f,      // nearRing极端惩罚阈值
    val nearRingCCPenalty: Float = 0.08f,         // nearRing极端惩罚值

    // 输出 debug 图
    val enableDebugImages: Boolean = true
)

