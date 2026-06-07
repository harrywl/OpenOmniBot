package cn.com.omnimind.assists.detection.detectors.structure

import android.graphics.Bitmap
import android.util.Log
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

object ScreenElementAnalyzer {

    suspend fun analyze(bgr: Mat, allowedIconTypes: Set<String>? = null, excludeText: Boolean = false): ScreenAnalysisResult {
        val w = bgr.cols()
        val h = bgr.rows()

        val gray = Mat()
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)

        // edges 用于形状候选
        val edges = Mat()
        Imgproc.Canny(gray, edges, 60.0, 160.0)
        Imgproc.dilate(edges, edges, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val candidates = ArrayList<Candidate>()
        for (c in contours) {
            val r = Imgproc.boundingRect(c)
            val area = r.area().toDouble()
            if (area < (w*h) * 0.0006) continue          // 太小噪声
            if (area > (w*h) * 0.20) continue            // 太大不是控件

            val tags = spatialTags(r, w, h)

            // 1) icon 候选：近方/近圆、尺寸适中
            val wh = r.width.toDouble() / (r.height + 1e-6)
            val minSide = min(r.width, r.height).toDouble()
            val maxSide = max(r.width, r.height).toDouble()

            val iconSizeOk =
                minSide in (0.035*w)..(0.20*w) &&        // 经验范围，可调
                maxSide in (0.035*w)..(0.24*w)

            val iconShapeOk = wh in 0.65..1.85

            if (iconSizeOk && iconShapeOk) {
                candidates.add(
                    Candidate(
                        rect = r,
                        kind = Candidate.Kind.ICON,
                        score = 0.55,
                        tags = tags,
                        reasons = listOf("icon_wh=${wh.f2()}", "area=${area.f0()}")
                    )
                )
            }
        }
        
        // Filter Candidates with OCR (Full Screen)
        val filteredCandidates = if (excludeText && candidates.isNotEmpty()) {
            var validCandidates = candidates.toList()
            val bitmap = matToBitmap(bgr)
            
            if (bitmap != null) {
                try {
                    val blocks = cn.com.omnimind.baselib.ocr.OcrUtil.recognizeTextBlocks(bitmap)
                    
                    // Filter out candidates that overlap with ANY text block
                    validCandidates = candidates.filter { cand ->
                        val candRect = cand.rect
                        var isText = false
                        for (block in blocks) {
                            val blockRect = block.boundingBox ?: continue
                            if (isExcludeText(block.text) && isOverlap(candRect, blockRect)) {
                                isText = true
                                break
                            }
                        }
                        !isText
                    }
                    Log.d("ScreenElementAnalyzer", "OCR Filtering: ${candidates.size} -> ${validCandidates.size} candidates")
                    
                } catch (e: Exception) {
                    Log.e("ScreenElementAnalyzer", "Full Screen OCR failed", e)
                }
            }
            validCandidates
        } else {
            candidates
        }
        
        // Match Icons (传入屏幕尺寸以启用 gridRegion 过滤优化)
        val matchResult = cn.com.omnimind.assists.detection.detectors.icon.IconTemplateMatcher.match(
            filteredCandidates, bgr, allowedIconTypes, screenW = w, screenH = h
        )
        val finalHits = matchResult.hits
        val iconDebugs = matchResult.debugInfos

        gray.release()
        edges.release()

        return ScreenAnalysisResult(
            candidates = filteredCandidates,
            matchedIcons = finalHits,
            iconDebugs = iconDebugs
        )
    }

    private fun isOverlap(cvRect: Rect, androidRect: android.graphics.Rect): Boolean {
        // Convert cvRect to coords
        val l1 = cvRect.x
        val t1 = cvRect.y
        val r1 = cvRect.x + cvRect.width
        val b1 = cvRect.y + cvRect.height
        
        val l2 = androidRect.left
        val t2 = androidRect.top
        val r2 = androidRect.right
        val b2 = androidRect.bottom

        // Check if disjoint
        if (l1 >= r2 || l2 >= r1) return false
        if (t1 >= b2 || t2 >= b1) return false
        
        // Calculate intersection area
        val interL = max(l1, l2)
        val interT = max(t1, t2)
        val interR = min(r1, r2)
        val interB = min(b1, b2)
        
        val w = max(0, interR - interL)
        val h = max(0, interB - interT)
        val interArea = w * h
        
        // If intersection is significant relative to the candidate
        // e.g. > 30% of candidate area covered by text
        val candArea = cvRect.area()
        return if (candArea > 0) (interArea.toDouble() / candArea) > 0.3 else false
    }

    private val OCR_EXEMPT_WHITELIST = setOf("Q")

    private fun isExcludeText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        
        // 1. Check Whitelist (Exemptions) - If in whitelist, do NOT exclude
        if (OCR_EXEMPT_WHITELIST.contains(trimmed)) {
            return false
        }

        var hasContent = false
        // 2. Check if text consists ONLY of Chinese, English, Numbers, or Whitespace
        for (c in text) {
            if (c.isWhitespace()) continue
            
            val isCnEnNum = c.isLetterOrDigit() || (c.code in 0x4e00..0x9fa5)
            if (!isCnEnNum) {
                // Found a symbol -> Likely an icon -> Do NOT exclude
                return false
            }
            hasContent = true
        }
        return hasContent
    }

    private fun matToBitmap(mat: Mat): Bitmap? {
        val bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(mat, bmp)
        return bmp
    }

    private fun clampRect(r: Rect, w: Int, h: Int): Rect {
        val x = r.x.coerceIn(0, w - 1)
        val y = r.y.coerceIn(0, h - 1)
        val width = r.width.coerceAtMost(w - x)
        val height = r.height.coerceAtMost(h - y)
        return Rect(x, y, width, height)
    }

    // --- Helpers ---

    private fun spatialTags(r: Rect, w: Int, h: Int): Set<Candidate.Tag> {
        val cx = (r.x + r.width / 2.0) / w
        val cy = (r.y + r.height / 2.0) / h
        val tags = HashSet<Candidate.Tag>()
        if (cy < 0.18) tags.add(Candidate.Tag.TOP)
        if (cy > 0.78) tags.add(Candidate.Tag.BOTTOM)
        if (cx < 0.30) tags.add(Candidate.Tag.LEFT)
        if (cx > 0.70) tags.add(Candidate.Tag.RIGHT)
        if (cx in 0.35..0.65) tags.add(Candidate.Tag.CENTER)
        return tags
    }

    private fun Double.f2(): String = String.format("%.2f", this)
    private fun Double.f0(): String = String.format("%.0f", this)
    private fun Rect.area(): Int = width * height
}
