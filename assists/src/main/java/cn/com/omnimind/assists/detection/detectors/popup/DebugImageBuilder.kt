package cn.com.omnimind.assists.detection.detectors.popup

import android.graphics.Bitmap
import android.util.Base64
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import cn.com.omnimind.assists.detection.detectors.popup.models.*

// =============== Debug 输出 ===============

internal fun buildDebugImages(
    original: Bitmap,
    preproc: SharedPreprocessResult,
    sheet: SheetDetectResult,
    popup: PopupDetectResult
): Map<String, String> {
    val debug = mutableMapOf<String, String>()

    // V 通道可视化
    debug["v_channel"] = matToBase64Png(preproc.hsvV)

    // 绘制原图叠加
    val bgr = Mat()
    val tmp = Mat()
    Utils.bitmapToMat(original, tmp)
    Imgproc.cvtColor(tmp, bgr, Imgproc.COLOR_RGBA2BGR)
    tmp.release()

    // ROI 框
    Imgproc.rectangle(
        bgr,
        Point(preproc.roiRect.x.toDouble(), preproc.roiRect.y.toDouble()),
        Point((preproc.roiRect.x + preproc.roiRect.width).toDouble(), (preproc.roiRect.y + preproc.roiRect.height).toDouble()),
        Scalar(0.0, 255.0, 255.0), 2
    )

    // Sheet 分割线
    sheet.evidence?.let { ev ->
        val yy = (preproc.roiRect.y + ev.splitY).toDouble()
        Imgproc.line(bgr, Point(0.0, yy), Point(bgr.cols().toDouble(), yy), Scalar(0.0, 255.0, 0.0), 2)
    }

    // Popup bbox
    popup.bbox?.let { r ->
        val x1 = r.x
        val y1 = preproc.roiRect.y + r.y
        val x2 = r.x + r.width
        val y2 = preproc.roiRect.y + r.y + r.height
        Imgproc.rectangle(bgr, Point(x1.toDouble(), y1.toDouble()), Point(x2.toDouble(), y2.toDouble()),
            Scalar(0.0, 0.0, 255.0), 3)
    }

    debug["overlay"] = matToBase64Png(bgr)
    bgr.release()

    // Sheet debug images
    sheet.debugRowMeanPlot?.let { debug["sheet_row_mean_plot"] = matToBase64Png(it) }

    // Popup debug images
    popup.debugPopupBin?.let { debug["popup_bin"] = matToBase64Png(it) }
    popup.debugPopupMorph?.let { debug["popup_morph"] = matToBase64Png(it) }
    popup.debugRingDarkMask?.let { debug["ring_dark_mask"] = matToBase64Png(it) }
    popup.debugRingNear?.let { debug["ring_near"] = matToBase64Png(it) }
    popup.debugRingFar?.let { debug["ring_far"] = matToBase64Png(it) }

    return debug
}

internal fun matToBase64Png(mat: Mat): String {
    val rgba = Mat()
    when (mat.channels()) {
        1 -> Imgproc.cvtColor(mat, rgba, Imgproc.COLOR_GRAY2RGBA)
        3 -> Imgproc.cvtColor(mat, rgba, Imgproc.COLOR_BGR2RGBA)
        4 -> mat.copyTo(rgba)
        else -> Imgproc.cvtColor(mat, rgba, Imgproc.COLOR_GRAY2RGBA)
    }

    val bmp = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(rgba, bmp)
    rgba.release()

    val baos = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
    val bytes = baos.toByteArray()

    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

