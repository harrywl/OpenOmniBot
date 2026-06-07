package cn.com.omnimind.assists.detection.detectors.visualstability

import android.graphics.Bitmap
import cn.com.omnimind.assists.detection.scenarios.stability.StabilityConfig
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * 视觉稳定性检测（简化版：只做整屏差分，不做动态区域识别）
 *
 * 核心思路：
 * 1. 连续帧差分得到变化区域
 * 2. 计算整屏差异占比 diffRatioWhole
 * 3. 不再识别轮播/视频等动态区域
 */
class VisualStability(
    private val cfg: StabilityConfig
) {
    private var prevGray: Mat? = null
    private var lastSize: Size? = null

    /**
     * 重置所有状态
     */
    fun reset() {
        prevGray?.release()
        prevGray = null
        lastSize = null
    }

    /**
     * 更新一帧并测量稳定性
     *
     * @param bitmap 当前帧截图
     * @return 整屏差异占比 [0,1]，首帧返回 -1.0 表示无数据
     */
    fun updateAndMeasure(bitmap: Bitmap): Double {
        val gray = bitmapToSmallGray(bitmap)
        val currentSize = gray.size()

        // 检测尺寸变化（屏幕旋转、键盘弹出等场景）
        if (lastSize != null && (lastSize!!.width != currentSize.width || lastSize!!.height != currentSize.height)) {
            // 尺寸变化，重置所有状态
            reset()
        }
        lastSize = currentSize

        val prev = prevGray
        if (prev == null) {
            prevGray = gray
            return -1.0  // 首帧，无法判断稳定性
        }

        // 整屏差分：absdiff + threshold
        val abs = Mat()
        Core.absdiff(prev, gray, abs)

        val diffMask = Mat()
        Imgproc.threshold(abs, diffMask, cfg.pixelDiffThr, 1.0, Imgproc.THRESH_BINARY)

        // 轻微形态学去噪（去除孤立噪点）
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.morphologyEx(diffMask, diffMask, Imgproc.MORPH_OPEN, kernel)

        // 计算整屏差异占比
        val diffRatioWhole = ratioOfOnes(diffMask)

        // cleanup
        abs.release()
        diffMask.release()
        kernel.release()
        prev.release()
        prevGray = gray

        return diffRatioWhole
    }

    /**
     * 将 Bitmap 转换为降采样的灰度图（性能优化）
     */
    private fun bitmapToSmallGray(bitmap: Bitmap): Mat {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        // resize 到配置的宽度（保持宽高比）
        val w = cfg.downscaleW
        val h = (bitmap.height * (w.toDouble() / bitmap.width)).toInt().coerceAtLeast(1)
        val resized = Mat()
        Imgproc.resize(src, resized, Size(w.toDouble(), h.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)

        // 转灰度 + 高斯模糊（减少噪声）
        val gray = Mat()
        Imgproc.cvtColor(resized, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)

        src.release()
        resized.release()
        return gray
    }

    /**
     * 计算 mask 中值为 1 的像素占比
     */
    private fun ratioOfOnes(mask01: Mat): Double {
        // mask is 0/1
        val sum = Core.sumElems(mask01).`val`[0]
        val total = mask01.rows().toDouble() * mask01.cols().toDouble()
        return (sum / total).coerceIn(0.0, 1.0)
    }
}
