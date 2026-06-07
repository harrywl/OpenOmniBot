package cn.com.omnimind.baselib.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import BaseApplication
import java.io.File
import java.io.FileOutputStream
import cn.com.omnimind.baselib.util.ImageUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR 文本块数据类
 */
data class OcrTextBlock(
    val text: String,
    val boundingBox: Rect?
)

object OcrUtil {
    private const val TAG = "OcrUtil"

    /**
     * 创建中文文本识别器
     * @return TextRecognizer 实例
     */
    private fun createChineseTextRecognizer(): TextRecognizer {
        val options = ChineseTextRecognizerOptions.Builder().build()
        return TextRecognition.getClient(options)
    }

    /**
     * 执行 OCR 识别并返回识别结果
     * @param bitmap 要识别的图片
     * @return OCR 识别结果文本
     * @throws Exception 如果识别失败
     */
    private suspend fun recognizeText(bitmap: Bitmap): String {
        val recognizer = createChineseTextRecognizer()
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        return suspendCancellableCoroutine { continuation ->
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val resultText = visionText.text
                    Log.d(TAG, "OCR识别结果: $resultText")
                    continuation.resume(resultText)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR识别失败: ${e.message}", e)
                    continuation.resumeWithException(e)
                }
                .addOnCompleteListener {
                    recognizer.close()
                }
        }
    }

    /**
     * 执行 OCR 识别并返回 VisionText 对象（包含文本块信息）
     * @param bitmap 要识别的图片
     * @return VisionText 对象，包含文本块和坐标信息
     * @throws Exception 如果识别失败
     */
    private suspend fun recognizeTextWithBlocks(bitmap: Bitmap): com.google.mlkit.vision.text.Text {
        val recognizer = createChineseTextRecognizer()
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        return suspendCancellableCoroutine { continuation ->
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    Log.d(TAG, "OCR识别成功，共找到 ${visionText.textBlocks.size} 个文本块")
                    continuation.resume(visionText)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR识别失败: ${e.message}", e)
                    continuation.resumeWithException(e)
                }
                .addOnCompleteListener {
                    recognizer.close()
                }
        }
    }

    /**
     * 执行 OCR 识别并返回文本块列表（公开方法供外部模块调用）
     * @param bitmap 要识别的图片
     * @return 文本块列表（封装后的数据类，不暴露 ML Kit 类型）
     * @throws Exception 如果识别失败
     */
    suspend fun recognizeTextBlocks(bitmap: Bitmap): List<OcrTextBlock> {
        val visionText = recognizeTextWithBlocks(bitmap)

        // 将 ML Kit 的 TextBlock 转换为我们的 OcrTextBlock
        return visionText.textBlocks.map { block ->
            OcrTextBlock(
                text = block.text,
                boundingBox = block.boundingBox
            )
        }
    }

    /**
     * 将 visionText 中所有文本块所在区域绘制到 bitmap 上，并保存到 app 缓存目录
     */
    private fun drawVisionTextRegionsAndSaveToCache(bitmap: Bitmap, visionText: com.google.mlkit.vision.text.Text) {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        for (block in visionText.textBlocks) {
            block.boundingBox?.let { rect ->
                canvas.drawRect(rect, paint)
                canvas.drawPoint((rect.left + rect.right) / 2f, (rect.top + rect.bottom) / 2f,paint)
            }
        }
        val cacheDir = BaseApplication.instance.cacheDir
        val file = File(cacheDir, "ocr_regions_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { fos ->
            mutableBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        Log.d(TAG, "OCR 区域调试图已保存: ${file.absolutePath}")
    }

    /**
     * 根据文本列表匹配 OCR 识别结果，返回匹配文本块的中心点列表
     * @param visionText OCR 识别结果
     * @param targetTexts 要匹配的文本列表
     * @return 匹配文本块的中心点列表
     */
    private fun findTextCenterPoints(
        visionText: com.google.mlkit.vision.text.Text,
        targetTexts: List<String>
    ): List<PointF> {
        val pointsList = mutableListOf<PointF>()

        targetTexts.forEachIndexed { index, targetText ->
            Log.d(TAG, "查找文本 ${index + 1}: '$targetText'")
            for (block in visionText.textBlocks) {
                val blockText = block.text
                Log.d(TAG, "检查文本块: $blockText")
                if (blockText.contains(targetText)) {
                    val blockRect = block.boundingBox
                    val centerPoint = blockRect?.let { rect ->
                        PointF((rect.left + rect.right) / 2f, (rect.top + rect.bottom) / 2f)
                    }
                    centerPoint?.let {
                        pointsList.add(it)
                        Log.d(TAG, "找到文本块 '$blockText' 的中心点: (${it.x}, ${it.y})")
                    }
                }
            }
        }

        Log.i(TAG, "OCR匹配完成，共找到 ${pointsList.size} 个中心点")
        return pointsList
    }

    /**
     * 完整的 OCR 识别流程：从 Base64 字符串到中心点列表
     * @param imageBase64 Base64 编码的图片字符串
     * @param targetTexts 要匹配的文本列表
     * @return 匹配文本块的中心点列表
     * @throws Exception 如果识别或匹配失败
     */
     suspend fun recognizeAndFindCenterPoints(
        imageBase64: String,
        targetTexts: List<String>
    ): List<PointF> {
        // 1. 解码图片
        val bitmap = ImageUtils.decodeBase64ToBitmap(imageBase64)

        // 2. 执行 OCR 识别
        val visionText = recognizeTextWithBlocks(bitmap)

        // 2.5 将 visionText 所有区域绘制到 bitmap 并保存到 app 缓存目录
//        drawVisionTextRegionsAndSaveToCache(bitmap, visionText)

        // 3. 匹配文本并获取中心点
        return findTextCenterPoints(visionText, targetTexts)
    }
}
