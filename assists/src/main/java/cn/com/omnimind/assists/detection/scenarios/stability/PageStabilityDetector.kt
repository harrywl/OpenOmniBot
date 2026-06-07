package cn.com.omnimind.assists.detection.scenarios.stability

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import cn.com.omnimind.assists.controller.accessibility.AccessibilityController
import cn.com.omnimind.assists.detection.detectors.visualstability.VisualStability
import cn.com.omnimind.assists.detection.detectors.xmlstability.XmlStabilityChecker
import cn.com.omnimind.baselib.util.OmniLog
import kotlinx.coroutines.delay

/**
 * 页面稳定性检测器（简化版：以 XML 为主，视觉为辅）
 *
 * 核心算法：
 * 1. 视觉稳定：通过 OpenCV 帧差分检测整屏变化（不做动态区域识别）
 * 2. XML 稳定：通过 XML 结构相似度检测页面结构变化
 * 3. 融合判定：
 *    - 正常模式（未超时）：xmlStable && visualStable
 *    - XML-only 模式（超时后）：仅 xmlStable（连续 N 次）
 * 4. 视频特征检测：检测到视频时，缩短视觉等待时间（1.5s）
 * 5. 硬超时：强制返回结果
 */
class PageStabilityDetector(
    private val cfg: StabilityConfig = StabilityConfig()
) {
    companion object {
        /**
         * 快速等待页面稳定（静态工具方法）
         * 自动获取 AssistsService，自动处理调试数据保存
         *
         * @param config 稳定性配置（可选）
         * @return 稳定性检测结果
         */
        suspend fun awaitStability(
            config: StabilityConfig = StabilityConfig()
        ): PageStabilityResult {
            // 低版本系统，固定延迟 500ms 后返回成功结果
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                delay(500)
                return PageStabilityResult(
                    isStable = true,
                    xmlSim = 1.0,
                    diffRatioWhole = 0.0,
                    dynamicAreaRatio = 0.0,
                    stableHits = 0,
                    reasons = "API level too low, skipped stability check"
                )
            }

            val service = cn.com.omnimind.accessibility.service.AssistsService.instance
                ?: throw IllegalStateException("AssistsService not available")

            val detector = PageStabilityDetector(config)
            val start = System.currentTimeMillis()

            return detector.waitUntilStable(service).also {
                val elapsed = System.currentTimeMillis() - start
                OmniLog.d("PageStability", "页面稳定检测完成: ${it.reasons}, 耗时=${elapsed}ms")
            }
        }
    }

    private val visual = VisualStability(cfg)
    private val xmlChecker = XmlStabilityChecker(
        getChildBudgetMax = 50,
        maxNodes = 400,
        maxDepth = 10,
        heavyContainerChildLimit = 2,
        defaultChildLimit = 3
    )

    private var lastWindowChangeAt: Long = 0L
    private var stableHits: Int = 0
    private var xmlStableHits: Int = 0
    private var xmlOnlyMode: Boolean = false

    // 视频特征检测缓存（避免重复遍历）
    private var cachedVideoHint: Boolean? = null
    private var actualMaxVisWaitMs: Long = cfg.maxWaitMs

    /**
     * 窗口变化事件回调（如页面跳转、弹窗出现等）
     * 触发冷却期，重置稳定计数
     */
    fun onWindowChangedEvent() {
        lastWindowChangeAt = System.currentTimeMillis()
        stableHits = 0
        xmlStableHits = 0
        xmlOnlyMode = false
        cachedVideoHint = null  // 重置视频特征缓存
        xmlChecker.reset()
    }

    /**
     * 完全重置所有状态
     */
    fun resetAll() {
        stableHits = 0
        xmlStableHits = 0
        lastWindowChangeAt = 0L
        xmlOnlyMode = false
        cachedVideoHint = null
        actualMaxVisWaitMs = cfg.maxWaitMs
        visual.reset()
        xmlChecker.reset()
    }

    /**
     * 获取实际的根节点（优先获取 TYPE_APPLICATION 类型的窗口）
     */
    private fun getActualRootNode(service: cn.com.omnimind.accessibility.service.AssistsService): AccessibilityNodeInfo? {
        val windows = service.windows ?: return null
        for (window in windows) {
            if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
                val root = window.root
                if (root != null) {
                    return root
                }
            }
        }
        return null
    }


    /**
     * 更新一帧数据并判断稳定性
     *
     * @param bitmap 当前截图（XML-only 模式下可为 null）
     * @param rootNode 无障碍根节点（用于 XML 稳定性检测和视频特征检测）
     * @param elapsedMs 距离开始检测的耗时（毫秒）
     * @return 稳定性检测结果
     */
    fun update(bitmap: Bitmap?, rootNode: AccessibilityNodeInfo?, elapsedMs: Long): PageStabilityResult {
        val updateStart = System.currentTimeMillis()
        val now = System.currentTimeMillis()
        val inCooldown = (now - lastWindowChangeAt) < cfg.cooldownMs

        // XML 稳定性检测（使用轻量级 BFS 采样）
        val xmlStart = System.currentTimeMillis()
        val xmlStable = if (rootNode != null) {
            xmlChecker.updateAndIsXmlStable(rootNode)
        } else {
            false
        }
        val xmlElapsed = System.currentTimeMillis() - xmlStart

        // 首次调用时，从 XML 采样结果中获取视频特征（避免重复遍历）
        var videoHintElapsed = 0L
        if (cachedVideoHint == null && rootNode != null) {
            val videoHintStart = System.currentTimeMillis()
            cachedVideoHint = xmlChecker.getLastVideoHint()
            videoHintElapsed = System.currentTimeMillis() - videoHintStart
            // 根据视频特征动态调整 maxVisWaitMs
            actualMaxVisWaitMs = if (cachedVideoHint == true) {
                cfg.videoHintMaxVisWaitMs  // 检测到视频：1500ms
            } else {
                cfg.maxWaitMs  // 未检测到视频：3000ms
            }
        }

        val xmlVideoHint = cachedVideoHint ?: false

        // 视觉稳定性检测（只做整屏 diff）
        // XML-only 模式下 bitmap 为 null，跳过视觉检测
        val visualStart = System.currentTimeMillis()
        val diffRatioWhole = if (bitmap != null) {
            visual.updateAndMeasure(bitmap)
        } else {
            -1.0  // XML-only 模式，不计算视觉
        }
        val visualElapsed = System.currentTimeMillis() - visualStart

        // 首帧检测：diffRatioWhole = -1.0 表示无数据或 XML-only 模式
        val isFirstFrame = diffRatioWhole < 0.0 && bitmap != null
        if (isFirstFrame) {
            val firstFrameReasons = buildString {
                append("first_frame;mode=normal;")
                append("xml_stable=").append(xmlStable).append(";")
                if (xmlVideoHint) append("video_hint;")
            }
            return PageStabilityResult(
                isStable = false,
                xmlSim = if (xmlStable) 1.0 else 0.0,  // 兼容旧字段
                diffRatioWhole = 0.0,  // 首帧显示为 0
                dynamicAreaRatio = 0.0,  // 不再使用，固定为 0
                stableHits = 0,
                reasons = firstFrameReasons
            )
        }

        // 超时判断：根据动态 maxVisWaitMs 进入 XML-only 模式
        val shouldSwitchXmlOnly = elapsedMs >= actualMaxVisWaitMs && !xmlOnlyMode
        if (shouldSwitchXmlOnly) {
            xmlOnlyMode = true
            xmlStableHits = 0  // 重置 XML 稳定计数
        }

        // 硬超时判断
        val hardTimeout = elapsedMs >= cfg.hardTimeoutMs

        val visualStable = diffRatioWhole <= cfg.visualStableThr

        // 融合逻辑（核心）
        val stable = when {
            // 冷却期内：一律不稳定
            inCooldown -> false

            // 硬超时：强制返回
            hardTimeout -> true

            // XML-only 模式：只看 XML（连续 N 次）
            xmlOnlyMode -> {
                if (xmlStable) xmlStableHits++ else xmlStableHits = 0
                xmlStableHits >= cfg.xmlStableHitsRequired
            }

            // 正常模式：XML + 视觉双信号都要稳定
            else -> {
                xmlStable && visualStable
            }
        }

        // 更新稳定计数
        val logicStart = System.currentTimeMillis()
        if (stable) stableHits++ else stableHits = 0
        val finalStable = stableHits >= cfg.stableHitsRequired

        // 构建调试信息
        val buildReasonsStart = System.currentTimeMillis()
        val reasons = buildReasons(
            inCooldown, xmlStable, visualStable, diffRatioWhole,
            xmlOnlyMode, stableHits, xmlStableHits, hardTimeout,
            xmlVideoHint, shouldSwitchXmlOnly
        )
        val buildReasonsElapsed = System.currentTimeMillis() - buildReasonsStart
        val logicElapsed = System.currentTimeMillis() - logicStart

        val updateTotal = System.currentTimeMillis() - updateStart

        // 输出详细耗时日志
        OmniLog.d("PageStability",
            "update耗时分解：total=${updateTotal}ms, videoHint=${videoHintElapsed}ms, " +
            "xml=${xmlElapsed}ms, visual=${visualElapsed}ms, logic=${logicElapsed}ms (buildReasons=${buildReasonsElapsed}ms)")

        return PageStabilityResult(
            isStable = finalStable,
            xmlSim = if (xmlStable) 1.0 else 0.0,  // 兼容旧字段：stable = 1.0, unstable = 0.0
            diffRatioWhole = diffRatioWhole,  // 整屏差异占比
            dynamicAreaRatio = 0.0,  // 不再使用，固定为 0
            stableHits = stableHits,
            reasons = reasons
        )
    }

    /**
     * 构建调试信息字符串
     */
    private fun buildReasons(
        inCooldown: Boolean,
        xmlStable: Boolean,
        visualStable: Boolean,
        diffRatioWhole: Double,
        xmlOnlyMode: Boolean,
        stableHits: Int,
        xmlStableHits: Int,
        hardTimeout: Boolean,
        xmlVideoHint: Boolean,
        shouldSwitchXmlOnly: Boolean
    ): String = buildString {
        if (shouldSwitchXmlOnly) append("switch_xml_only;")
        if (inCooldown) append("cooldown;")
        append(if (xmlStable) "xml_ok;" else "xml_bad;")

        // XML-only 模式下跳过视觉检测
        if (xmlOnlyMode && diffRatioWhole < 0.0) {
            append("vis_skip;diff=N/A;vis_ignored;")
        } else if (xmlOnlyMode) {
            // XML-only 模式下，即使有 diff 值，也标记为 vis_ignored
            append(if (visualStable) "vis_ok;" else "vis_bad;")
            append("diff=").append(String.format("%.4f", diffRatioWhole)).append(";")
            append("vis_ignored;")
        } else {
            append(if (visualStable) "vis_ok;" else "vis_bad;")
            append("diff=").append(String.format("%.4f", diffRatioWhole)).append(";")
        }

        append("mode=").append(if (xmlOnlyMode) "xml_only" else "normal").append(";")
        append("hits=").append(stableHits).append(";")
        if (xmlOnlyMode) {
            append("xml_hits=").append(xmlStableHits).append(";")
        }
        if (hardTimeout) {
            append("hard_timeout;")
        }
        if (xmlVideoHint) {
            append("video_hint;")
        }
    }

    /**
     * 等待页面稳定（高层 API）
     *
     * 完整的检测循环，包括截图、XML 检测、超时控制等
     *
     * @param service AssistsService 实例
     * @param onDebugSave 调试数据保存回调 (result, stepIndex, bitmap, xml)
     * @return 最终的稳定性检测结果
     */
    suspend fun waitUntilStable(
        service: cn.com.omnimind.accessibility.service.AssistsService,
        onDebugSave: ((PageStabilityResult, Int, Bitmap?, String?) -> Unit)? = null
    ): PageStabilityResult {
        // 确保 OpenCV 已初始化
        cn.com.omnimind.assists.detection.OpenCVInitializer.ensureInitialized()

        val start = System.currentTimeMillis()
        var lastResult: PageStabilityResult? = null
        var checkCount = 0

        kotlinx.coroutines.delay(100)

        while (System.currentTimeMillis() - start < cfg.hardTimeoutMs) {
            val loopStartTime = System.currentTimeMillis()
            try {
                checkCount++
                val elapsedMs = System.currentTimeMillis() - start

                // 获取根节点（用于 XML 稳定性检测和视频特征检测）
                val rootNodeStartTime = System.currentTimeMillis()
                var rootNode = getActualRootNode(service)
                if (rootNode == null) {
                    rootNode = service.rootInActiveWindow
                }
                val rootNodeElapsed = System.currentTimeMillis() - rootNodeStartTime

                // 根据上一次的检测结果计算实际的超时阈值
                // 第一次检测时 lastResult 为 null，使用默认的 maxWaitMs
                val hasVideoHint = lastResult?.reasons?.contains("video_hint") ?: false
                val actualMaxVisWaitMs = if (hasVideoHint) cfg.videoHintMaxVisWaitMs else cfg.maxWaitMs
                val isXmlOnlyMode = elapsedMs >= actualMaxVisWaitMs

                // 根据模式决定是否截图
                val screenshotStartTime = System.currentTimeMillis()
                val bitmap = if (isXmlOnlyMode) {
                    null
                } else {
                    var image = AccessibilityController.captureScreenshotImage(isBitmap = true, isFilterOverlay = true)
                    if (image.isSuccess){
                        image.imageBitmap
                    }else{
                        null
                    }
                }
                val screenshotElapsed = System.currentTimeMillis() - screenshotStartTime

                // 正常模式下如果截图失败，跳过本轮
                if (!isXmlOnlyMode && bitmap == null) {
                    val loopElapsed = System.currentTimeMillis() - loopStartTime
                    val remainingDelay = (cfg.intervalMs - loopElapsed).coerceAtLeast(0L)
                    OmniLog.d("PageStability", "第${checkCount}轮：截图失败，跳过，本轮耗时=${loopElapsed}ms，等待=${remainingDelay}ms")
                    kotlinx.coroutines.delay(remainingDelay)
                    continue
                }

                // 更新检测器（第一次调用时会自动检测视频特征）
                val updateStartTime = System.currentTimeMillis()
                val result = update(bitmap, rootNode, elapsedMs)
                val updateElapsed = System.currentTimeMillis() - updateStartTime
                lastResult = result

                // 调试数据保存回调（XML 数据由 XmlStabilityChecker 内部处理，这里传 null）
                val debugStartTime = System.currentTimeMillis()
                onDebugSave?.invoke(result, checkCount, bitmap, null)
                val debugElapsed = System.currentTimeMillis() - debugStartTime

                bitmap?.recycle()

                val loopElapsed = System.currentTimeMillis() - loopStartTime

                // 输出调试信息（包含详细耗时）
                OmniLog.d("PageStability",
                    "第${checkCount}轮：rootNode=${rootNodeElapsed}ms, 截图=${screenshotElapsed}ms, " +
                    "update=${updateElapsed}ms, debug=${debugElapsed}ms, 本轮总计=${loopElapsed}ms")
                OmniLog.d("PageStability", "检测结果：$result")

                // 如果稳定，返回结果
                if (result.isStable) {
                    return result
                }

                // 根据本轮耗时动态调整等待时间
                val remainingDelay = (cfg.intervalMs - loopElapsed).coerceAtLeast(0L)
                OmniLog.d("PageStability", "本轮耗时=${loopElapsed}ms，等待=${remainingDelay}ms")

                if (remainingDelay > 0) {
                    kotlinx.coroutines.delay(remainingDelay)
                }
            } catch (e: Exception) {
                val loopElapsed = System.currentTimeMillis() - loopStartTime
                val remainingDelay = (cfg.intervalMs - loopElapsed).coerceAtLeast(0L)
                OmniLog.e("PageStability", "检测出错：${e.message}，本轮耗时=${loopElapsed}ms，等待=${remainingDelay}ms", e)
                kotlinx.coroutines.delay(remainingDelay)
                // 协程取消是正常流程，需要重新抛出
                if (e is java.util.concurrent.CancellationException || e is kotlinx.coroutines.CancellationException) {
                    throw e
                }
            }
        }

        // 超时返回最后一次结果，如果没有则返回失败结果
        return lastResult ?: PageStabilityResult(
            isStable = false,
            xmlSim = 0.0,
            diffRatioWhole = 1.0,
            dynamicAreaRatio = 0.0,
            stableHits = 0,
            reasons = "timeout_no_frame;"
        )
    }
}
