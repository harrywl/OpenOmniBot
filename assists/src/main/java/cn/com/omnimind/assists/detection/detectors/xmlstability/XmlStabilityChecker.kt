package cn.com.omnimind.assists.detection.detectors.xmlstability

import android.view.accessibility.AccessibilityNodeInfo
import cn.com.omnimind.baselib.util.OmniLog
import kotlin.math.abs

/**
 * XML 稳定性检测器（轻量级 BFS 采样）
 *
 * 核心思路：
 * 1. BFS 遍历（而非递归 DFS）
 * 2. 严格限制 getChild 调用次数（预算制）
 * 3. 轻采样：只统计关键指标，不构建完整树
 * 4. 通过变化率判断稳定性
 *
 * 性能优化：
 * - 重灾区容器（RecyclerView、ViewPager 等）限制子节点遍历数量
 * - 限制最大深度
 * - 限制总节点数
 */
class XmlStabilityChecker(
    private val getChildBudgetMax: Int = 50,      // 最大 getChild 调用次数
    private val maxNodes: Int = 400,              // 最大节点访问数（防止队列膨胀）
    private val maxDepth: Int = 10,               // BFS 最大深度
    private val heavyContainerChildLimit: Int = 2, // 重灾区容器的子节点遍历上限
    private val defaultChildLimit: Int = 3        // 默认子节点遍历上限
) {
    companion object {
        private const val TAG = "XmlStabilityChecker"

        // ========== 稳定性检测阈值配置 ==========
        // 正常采样时的阈值
        private const val NORMAL_DELTA_VISIBLE = 0.10  // 可见节点数相对变化率阈值（正常模式）
        private const val NORMAL_DELTA_ACTIONABLE = 0.15  // 可交互节点数相对变化率阈值（正常模式）
        private const val NORMAL_CLASS_DIFF = 0.08  // 节点类型直方图 L1 距离阈值（正常模式）
        private const val NORMAL_ACTIONABLE_CLASS_DIFF = 0.12  // 可交互节点类型直方图 L1 距离阈值（正常模式）

        // 截断采样时的更严格阈值（预算用完）
        private const val TRUNCATED_DELTA_VISIBLE = 0.06  // 可见节点数相对变化率阈值（截断模式，更严格）
        private const val TRUNCATED_DELTA_ACTIONABLE = 0.10  // 可交互节点数相对变化率阈值（截断模式，更严格）
        private const val TRUNCATED_CLASS_DIFF = 0.06  // 节点类型直方图 L1 距离阈值（截断模式，更严格）
        private const val TRUNCATED_ACTIONABLE_CLASS_DIFF = 0.10  // 可交互节点类型直方图 L1 距离阈值（截断模式，更严格）

        /**
         * 稳定性检测的阈值配置
         *
         * @param truncated true 表示发生采样截断（预算用完），使用更严格的阈值；false 使用正常阈值
         */
        internal fun getThresholds(truncated: Boolean): Thresholds {
            return if (truncated) {
                // 截断时更严格的阈值（防止采样不足时误判稳定）
                Thresholds(
                    deltaVisible = TRUNCATED_DELTA_VISIBLE,
                    deltaActionable = TRUNCATED_DELTA_ACTIONABLE,
                    classDiff = TRUNCATED_CLASS_DIFF,
                    actionableClassDiff = TRUNCATED_ACTIONABLE_CLASS_DIFF
                )
            } else {
                // 正常采样时的阈值
                Thresholds(
                    deltaVisible = NORMAL_DELTA_VISIBLE,
                    deltaActionable = NORMAL_DELTA_ACTIONABLE,
                    classDiff = NORMAL_CLASS_DIFF,
                    actionableClassDiff = NORMAL_ACTIONABLE_CLASS_DIFF
                )
            }
        }
    }

    private var lastSnapshot: Snapshot? = null

    /**
     * 采样快照
     */
    data class Snapshot(
        val visibleNodes: Int,
        val actionableNodes: Int,
        val classHist: Map<String, Int>,
        val actionableClassHist: Map<String, Int>,
        val getChildUsed: Int,
        val hitGetChildBudget: Boolean,
        val hasVideoHint: Boolean  // 是否检测到视频特征
    )

    /**
     * BFS 队列元素
     */
    private data class QueueItem(
        val node: AccessibilityNodeInfo,
        val depth: Int
    )

    /**
     * 更新并判断 XML 是否稳定
     *
     * @param root 根节点
     * @return true 表示稳定，false 表示不稳定
     */
    fun updateAndIsXmlStable(root: AccessibilityNodeInfo): Boolean {
        val startTime = System.currentTimeMillis()

        val currentSnapshot = sampleSnapshot(root)

        val previousSnapshot = lastSnapshot
        lastSnapshot = currentSnapshot

        // 第一次采样，没有参照对象，返回不稳定
        if (previousSnapshot == null) {
            val elapsed = System.currentTimeMillis() - startTime
            OmniLog.d(TAG, "首次采样: getChild=${currentSnapshot.getChildUsed}, 耗时=${elapsed}ms")
            return false
        }

        // 计算变化率
        val deltaVisible = ratioDiff(currentSnapshot.visibleNodes, previousSnapshot.visibleNodes)
        val deltaActionable = ratioDiff(currentSnapshot.actionableNodes, previousSnapshot.actionableNodes)
        val classDiff = l1HistogramDiff(currentSnapshot.classHist, previousSnapshot.classHist)
        val actDiff = l1HistogramDiff(currentSnapshot.actionableClassHist, previousSnapshot.actionableClassHist)

        // 是否发生截断（预算用完）
        val truncated = currentSnapshot.hitGetChildBudget || previousSnapshot.hitGetChildBudget

        // 使用统一管理的阈值
        val thresholds = getThresholds(truncated)
        val (dvThr, daThr, cdThr, adThr) = thresholds

        val isStable = deltaVisible <= dvThr &&
                deltaActionable <= daThr &&
                classDiff <= cdThr &&
                actDiff <= adThr

        val elapsed = System.currentTimeMillis() - startTime
        OmniLog.d(TAG,
            "XML稳定性检测: stable=$isStable, truncated=$truncated, " +
            "deltaVisible=${String.format("%.3f", deltaVisible)} (thr=${String.format("%.3f", dvThr)}), " +
            "deltaActionable=${String.format("%.3f", deltaActionable)} (thr=${String.format("%.3f", daThr)}), " +
            "classDiff=${String.format("%.3f", classDiff)} (thr=${String.format("%.3f", cdThr)}), " +
            "actDiff=${String.format("%.3f", actDiff)} (thr=${String.format("%.3f", adThr)}), " +
            "getChild=${currentSnapshot.getChildUsed}, 耗时=${elapsed}ms")

        return isStable
    }

    /**
     * 获取最后一次采样中检测到的视频特征
     * @return true 表示检测到视频特征，false 表示未检测到或未采样
     */
    fun getLastVideoHint(): Boolean {
        return lastSnapshot?.hasVideoHint ?: false
    }

    /**
     * 重置状态
     */
    fun reset() {
        lastSnapshot = null
    }

    /**
     * 采样快照（BFS + getChild 预算）
     */
    private fun sampleSnapshot(root: AccessibilityNodeInfo): Snapshot {
        var remainingGetChild = getChildBudgetMax
        var nodesVisited = 0

        // 直方图统计
        val classHist = HashMap<String, Int>(64)
        val actionableClassHist = HashMap<String, Int>(64)

        var visibleNodes = 0
        var actionableNodes = 0
        var hitBudget = false
        var hasVideoHint = false  // 视频特征检测

        // 视频关键词（预先转为小写）
        val videoKeywords = listOf(
            "播放", "暂停", "重播", "继续播放",
            "全屏", "退出全屏", "倍速", "静音", "音量",
            "play", "pause", "replay",
            "fullscreen", "exit fullscreen",
            "speed", "mute", "volume"
        ).map { it.lowercase() }

        // BFS 队列
        val queue: ArrayDeque<QueueItem> = ArrayDeque()
        queue.add(QueueItem(root, 0))

        while (queue.isNotEmpty() && nodesVisited < maxNodes) {
            val (node, depth) = queue.removeFirst()
            nodesVisited++

            // 统计节点信息（不调用 getChild）
            val cls = normalizeClassName(node.className?.toString())
            classHist[cls] = (classHist[cls] ?: 0) + 1

            val isVisible = node.isVisibleToUser
            if (isVisible) visibleNodes++

            val isActionable = node.isClickable || node.isScrollable ||
                              node.isFocusable || node.isLongClickable
            if (isActionable) {
                actionableNodes++
                actionableClassHist[cls] = (actionableClassHist[cls] ?: 0) + 1
            }

            // 顺便检测视频特征（复用已遍历的节点）
            if (!hasVideoHint) {
                val text = node.text?.toString()
                val desc = node.contentDescription?.toString()
                val className = node.className?.toString()
                val viewId = node.viewIdResourceName

                // 快速路径：如果有任何内容，才检查关键词
                if (text != null || desc != null || className != null || viewId != null) {
                    val nodeText = buildString {
                        text?.let { append(it).append(" ") }
                        desc?.let { append(it).append(" ") }
                        className?.let { append(it).append(" ") }
                        viewId?.let { append(it).append(" ") }
                    }.lowercase()

                    // 检查是否包含视频关键词
                    for (keyword in videoKeywords) {
                        if (nodeText.contains(keyword)) {
                            hasVideoHint = true
                            OmniLog.d(TAG, "在 XML 采样中检测到视频关键词='$keyword'")
                            break
                        }
                    }
                }
            }

            // BFS 向下扩展（这里才会调用 getChild）
            if (depth >= maxDepth) {
                node.recycle()
                continue
            }

            // 根据节点类型确定子节点遍历上限
            val childLimit = getChildLimit(node)
            val count = minOf(node.childCount, childLimit)

            for (i in 0 until count) {
                if (remainingGetChild == 0) {
                    hitBudget = true
                    break
                }
                remainingGetChild--

                var child: AccessibilityNodeInfo? = null
                try {
                    child = node.getChild(i)
                    if (child != null) {
                        queue.add(QueueItem(child, depth + 1))
                        // 注意：child 交由队列后续处理，这里不 recycle
                    }
                } catch (t: Throwable) {
                    // 忽略异常，回收节点
                    child?.recycle()
                }
            }

            node.recycle()

            if (hitBudget) break
        }

        // 清理队列中剩余未处理的节点，避免泄漏
        while (queue.isNotEmpty()) {
            queue.removeFirst().node.recycle()
        }

        return Snapshot(
            visibleNodes = visibleNodes,
            actionableNodes = actionableNodes,
            classHist = classHist,
            actionableClassHist = actionableClassHist,
            getChildUsed = getChildBudgetMax - remainingGetChild,
            hitGetChildBudget = hitBudget,
            hasVideoHint = hasVideoHint
        )
    }

    /**
     * 根据节点类型确定子节点遍历上限
     */
    private fun getChildLimit(node: AccessibilityNodeInfo): Int {
        val className = node.className?.toString() ?: return defaultChildLimit
        return if (isHeavyContainer(className)) {
            heavyContainerChildLimit
        } else {
            defaultChildLimit
        }
    }

    /**
     * 判断是否为重灾区容器（会产生大量子节点的容器）
     */
    private fun isHeavyContainer(className: String): Boolean {
        return className == "androidx.recyclerview.widget.RecyclerView" ||
                className == "androidx.viewpager.widget.ViewPager" ||
                className == "androidx.viewpager2.widget.ViewPager2" ||
                className == "android.widget.HorizontalScrollView" ||
                className == "android.widget.ListView" ||
                className == "android.widget.GridView" ||
                className == "android.widget.ScrollView"
    }

    /**
     * 归一化类名（减少直方图的 key 数量）
     */
    private fun normalizeClassName(className: String?): String {
        if (className == null) return "null"
        return when {
            className.endsWith("Layout") || className.endsWith("ViewGroup") -> "Layout"
            className.contains("RecyclerView") -> "RecyclerView"
            className.contains("ViewPager2") -> "ViewPager2"
            className.contains("ViewPager") -> "ViewPager"
            className.contains("WebView") -> "WebView"
            className.endsWith("TextView") -> "TextView"
            className.endsWith("Button") -> "Button"
            className.endsWith("ImageView") -> "ImageView"
            className.endsWith("EditText") -> "EditText"
            className.endsWith("CheckBox") -> "CheckBox"
            className.endsWith("RadioButton") -> "RadioButton"
            className.endsWith("Switch") -> "Switch"
            else -> className
        }
    }

    /**
     * 计算两个数的相对差异（0.0 ~ 1.0）
     */
    private fun ratioDiff(a: Int, b: Int): Double {
        val max = maxOf(a, b)
        if (max == 0) return 0.0
        return abs(a - b).toDouble() / max.toDouble()
    }

    /**
     * 计算两个直方图的 L1 距离（归一化）
     */
    private fun l1HistogramDiff(a: Map<String, Int>, b: Map<String, Int>): Double {
        var sumA = 0
        for (v in a.values) sumA += v
        var sumB = 0
        for (v in b.values) sumB += v
        val denom = maxOf(sumA, sumB)
        if (denom == 0) return 0.0

        // 遍历 key 并集
        var diff = 0
        val keys = HashSet<String>(a.size + b.size)
        keys.addAll(a.keys)
        keys.addAll(b.keys)
        for (k in keys) {
            diff += abs((a[k] ?: 0) - (b[k] ?: 0))
        }
        return diff.toDouble() / denom.toDouble()
    }

    /**
     * 阈值配置
     */
    internal data class Thresholds(
        val deltaVisible: Double,
        val deltaActionable: Double,
        val classDiff: Double,
        val actionableClassDiff: Double
    )
}
