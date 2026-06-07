package cn.com.omnimind.assists.detection.detectors.button

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.max

/**
 * 候选节点数据类
 */
data class Candidate(
    val node: AccessibilityNodeInfo,
    val rect: Rect,
    val score: Int,
    val reasons: String
)

/**
 * 从 rootInActiveWindow 中找"可能是关闭/跳过入口"的候选节点（主要针对无文字、叶子可点击图标）。
 *
 * 你可以把返回的 candidates 逐个做截图 patch 二次校验（X 检测）：
 * - 先做 detectCloseX(patch)
 * - 再决定点哪个
 */
fun findCloseCandidates(
    root: AccessibilityNodeInfo?,
    screenW: Int,
    screenH: Int,
    maxCandidates: Int = 20
): List<Candidate> {
    if (root == null) return emptyList()

    val startTime = System.currentTimeMillis()
    val timeoutMs = 500L  // 耗时限制 500ms
    var isTimeout = false

    val results = ArrayList<Candidate>(64)

    fun hasClickAction(node: AccessibilityNodeInfo): Boolean {
        val has = node.actionList?.any { it.id == AccessibilityNodeInfo.ACTION_CLICK } == true
        return node.isClickable || has
    }

    fun safeText(s: CharSequence?): String = s?.toString()?.trim().orEmpty()

    fun isEmptySemantic(node: AccessibilityNodeInfo): Boolean {
        val t = safeText(node.text)
        val d = safeText(node.contentDescription)
        return t.isEmpty() && d.isEmpty()
    }

    /**
     * 检查子树中是否有语义信息（text/desc/id）
     * @param depthLimit 深度限制，避免递归太深
     */
    fun hasSemanticInSubtree(node: AccessibilityNodeInfo, depthLimit: Int = 3): Boolean {
        if (depthLimit <= 0) return false

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (!isEmptySemantic(child)) {
                    return true
                }
                if (hasSemanticInSubtree(child, depthLimit - 1)) {
                    return true
                }
            } finally {
                child.recycle()
            }
        }
        return false
    }

    fun rectArea(r: Rect): Int = max(0, r.width()) * max(0, r.height())

    fun scoreNode(node: AccessibilityNodeInfo, r: Rect): Candidate? {
        if (!node.isEnabled) return null
        if (Build.VERSION.SDK_INT >= 16 && !node.isVisibleToUser) return null
        if (!hasClickAction(node)) return null

        val area = rectArea(r)
        if (area <= 0) return null

        val screenArea = screenW * screenH
        val areaRatio = area.toDouble() / screenArea.toDouble()
        if (areaRatio > 0.12) return null

        val w = r.width()
        val h = r.height()

        if (w < 20 || h < 20) return null

        val cc = node.childCount
        val isLeaf = cc == 0
        val subtreeHasSem = if (!isLeaf) hasSemanticInSubtree(node, depthLimit = 3) else false

        if (!isLeaf && subtreeHasSem) {
            return null
        }

        var score = 0
        val reasons = StringBuilder()

        val selfEmptySem = isEmptySemantic(node)

        if (isLeaf) {
            if (selfEmptySem) {
                score += 25
                reasons.append("empty_sem;")
            } else {
                score -= 10
                reasons.append("has_sem;")
            }
        } else {
            if (selfEmptySem && !subtreeHasSem) {
                score += 5
                reasons.append("empty_sem_weak;")
            } else {
                score -= 40
                reasons.append("has_subtree_sem;")
            }
        }

        if (isLeaf) {
            score += 25
            reasons.append("leaf;")
        } else if (cc <= 1) {
            score += 10
            reasons.append("near_leaf;")
        } else {
            score -= 15
            reasons.append("many_children;")
        }

        val ratio = w.toDouble() / h.toDouble()
        if (ratio in 0.65..1.55) {
            score += 10
            reasons.append("squareish;")
        } else {
            score -= 5
            reasons.append("non_square;")
        }

        when {
            areaRatio < 0.0005 -> { score -= 10; reasons.append("too_small;") }
            areaRatio < 0.03   -> { score += 10; reasons.append("small_ok;") }
            else               -> { score -= 5;  reasons.append("mid_large;") }
        }

        val cls = node.className?.toString().orEmpty()
        if (cls.contains("ImageView", true) || cls.contains("Button", true)) {
            score += 3
            reasons.append("img_or_btn;")
        } else if (cls.contains("FrameLayout", true)) {
            score += 1
            reasons.append("framelayout;")
        }

        val rid = node.viewIdResourceName?.lowercase().orEmpty()
        if (rid.contains("close") || rid.contains("dismiss") || rid.contains("skip") || rid.contains("cancel")) {
            score += 80
            reasons.append("id_keyword;")
        }

        val t = safeText(node.text).lowercase()
        val d = safeText(node.contentDescription).lowercase()
        fun hitKeyword(s: String): Boolean {
            return s.contains("跳过") || s.contains("关闭") || s.contains("skip") || s.contains("close") ||
                   s.contains("dismiss") || s.contains("x") || s.contains("×")
        }
        if (hitKeyword(t) || hitKeyword(d)) {
            score += 80
            reasons.append("sem_keyword;")
        }

        if (node.isEditable || node.isScrollable) {
            score -= 30
            reasons.append("editable_or_scroll;")
        }

        if (score < 15) return null

        return Candidate(node, Rect(r), score, reasons.toString())
    }

    fun dfs(n: AccessibilityNodeInfo) {
        // 检查是否超时，超时则立即返回不再遍历
        if (System.currentTimeMillis() - startTime >= timeoutMs) {
            if (!isTimeout) {
                isTimeout = true
                android.util.Log.d("CloseCandidateFinder",
                    "findCloseCandidates超时(${System.currentTimeMillis() - startTime}ms)，已找到${results.size}个候选，停止遍历")
            }
            return
        }

        val r = Rect()
        n.getBoundsInScreen(r)

        scoreNode(n, r)?.let { results.add(it) }

        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            dfs(c)
            c.recycle()
        }
    }

    dfs(root)

    val totalElapsed = System.currentTimeMillis() - startTime
    if (!isTimeout) {
        android.util.Log.d("CloseCandidateFinder",
            "findCloseCandidates完成，耗时${totalElapsed}ms，找到${results.size}个候选")
    }

    // 去重：有时会出现同 bounds 的节点重复（或层叠）
    val dedup = results
        .sortedByDescending { it.score }
        .distinctBy { "${it.rect.left},${it.rect.top},${it.rect.right},${it.rect.bottom}" }

    return dedup.take(maxCandidates)
}

