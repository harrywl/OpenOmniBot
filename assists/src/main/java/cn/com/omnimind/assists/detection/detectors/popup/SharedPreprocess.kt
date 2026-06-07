package cn.com.omnimind.assists.detection.detectors.popup

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import cn.com.omnimind.assists.detection.detectors.popup.models.*

// =============== SharedPreprocess ===============

object SharedPreprocess {
    fun process(
        bitmap: Bitmap,
        cfg: MaskDetectConfig,
        statusBarHeightPx: Int = 0,
        navigationBarHeightPx: Int = 0
    ): SharedPreprocessResult {
        // Bitmap -> Mat (BGR)
        val bgr = Mat()
        Utils.bitmapToMat(bitmap, bgr) // RGBA
        val bgr2 = Mat()
        Imgproc.cvtColor(bgr, bgr2, Imgproc.COLOR_RGBA2BGR)

        val H = bgr2.rows()
        val W = bgr2.cols()

        // ROI 裁剪：结合状态栏高度和 cropTopRatio
        val statusBarTop = statusBarHeightPx.coerceIn(0, H / 2)
        val ratioTop = (H * cfg.cropTopRatio).toInt()
        val top = maxOf(statusBarTop, ratioTop).coerceIn(0, H - 1)

        // 底部裁剪：结合导航栏高度和 cropBottomRatio
        val navigationBarBottom = navigationBarHeightPx.coerceIn(0, H / 2)
        val ratioBottom = (H * cfg.cropBottomRatio).toInt()
        val bottomCrop = maxOf(navigationBarBottom, ratioBottom)
        val bottom = (H - bottomCrop).coerceIn(top + 1, H)

        val roiRect = Rect(0, top, W, bottom - top)
        val roi = bgr2.submat(roiRect)

        // BGR -> HSV
        val hsv = Mat()
        Imgproc.cvtColor(roi, hsv, Imgproc.COLOR_BGR2HSV)
        val hsvChannels = ArrayList<Mat>(3)
        Core.split(hsv, hsvChannels)
        val hsvV = hsvChannels[2].clone()  // V 通道
        val hsvS = hsvChannels[1].clone()  // S 通道

        // 计算每行平均 V 值
        val rows = hsvV.rows()
        val rowMeanV = DoubleArray(rows)
        for (y in 0 until rows) {
            val rowMat = hsvV.row(y)
            rowMeanV[y] = Core.mean(rowMat).`val`[0]
            rowMat.release()
        }

        // 释放临时资源
        for (ch in hsvChannels) ch.release()
        hsv.release()
        bgr.release()
        bgr2.release()

        return SharedPreprocessResult(
            roi = roi,
            roiRect = roiRect,
            hsvV = hsvV,
            hsvS = hsvS,
            rowMeanV = rowMeanV,
            fullHeight = H,
            fullWidth = W
        )
    }
}

