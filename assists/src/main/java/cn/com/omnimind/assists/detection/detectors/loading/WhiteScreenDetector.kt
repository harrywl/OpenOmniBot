package cn.com.omnimind.assists.detection.detectors.loading

import android.graphics.Bitmap
import cn.com.omnimind.assists.detection.OpenCVInitializer
import cn.com.omnimind.baselib.util.OmniLog
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * 纯色屏检测器（原白屏检测器）
 * 通过计算颜色方差和主要颜色占比来检测纯色背景（可以是任何颜色）
 */
object WhiteScreenDetector {
    private const val TAG = "WhiteScreenDetector"
    
    // 纯色屏检测阈值
    private const val DEFAULT_SOLID_COLOR_THRESHOLD = 0.85 // 85%以上是纯色
    private const val DEFAULT_COLOR_VARIANCE_THRESHOLD = 500.0 // 颜色方差阈值（方差小说明是纯色）
    private const val DEFAULT_COLOR_TOLERANCE = 8 // 颜色容差（RGB值在容差范围内认为是同一颜色，进一步减小以提高精度）
    
    // 兼容旧版本的阈值（保留用于向后兼容）
    private const val DEFAULT_WHITE_SCREEN_THRESHOLD = 0.85
    private const val DEFAULT_WHITE_COLOR_THRESHOLD = 240
    
    /**
     * 检测结果
     */
    data class WhiteScreenResult(
        /** 是否是大白屏 */
        val isWhiteScreen: Boolean,
        /** 白色像素占比 [0,1] */
        val whiteRatio: Float,
        /** 置信度 [0,1] */
        val confidence: Float
    )
    
    /**
     * 检测纯色屏（可以是任何颜色的纯色背景）
     * @param bitmap 截图
     * @param whiteScreenThreshold 纯色占比阈值，默认 0.85（保持向后兼容的参数名）
     * @param whiteColorThreshold 未使用（保持向后兼容）
     * @return 检测结果
     */
    fun detect(
        bitmap: Bitmap,
        whiteScreenThreshold: Double = DEFAULT_SOLID_COLOR_THRESHOLD,
        whiteColorThreshold: Int = DEFAULT_WHITE_COLOR_THRESHOLD
    ): WhiteScreenResult {
        OpenCVInitializer.ensureInitialized()
        
        if (bitmap.isRecycled) {
            OmniLog.w(TAG, "Bitmap is recycled")
            return WhiteScreenResult(false, 0f, 0f)
        }
        
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        try {
            // 转换为BGR格式（OpenCV标准格式）
            val bgr = Mat()
            Imgproc.cvtColor(mat, bgr, Imgproc.COLOR_RGBA2BGR)
            
            // 方法1：计算RGB三个通道的方差（方差小说明是纯色）
            val mean = MatOfDouble()
            val stddev = MatOfDouble()
            Core.meanStdDev(bgr, mean, stddev)
            
            // 计算三个通道的方差
            val stddevB = stddev.get(0, 0)[0]
            val stddevG = stddev.get(1, 0)[0]
            val stddevR = stddev.get(2, 0)[0]
            val varianceB = stddevB * stddevB
            val varianceG = stddevG * stddevG
            val varianceR = stddevR * stddevR
            val maxVariance = maxOf(varianceB, varianceG, varianceR)
            
            // 方法2：检测主要颜色占比（改进版：去除不同颜色的矩形区域）
            // 计算主要颜色（均值）的容差范围内的像素占比
            val meanB = mean.get(0, 0)[0]
            val meanG = mean.get(1, 0)[0]
            val meanR = mean.get(2, 0)[0]
            
            // 创建主要颜色的mask（RGB值在容差范围内）
            val mainColorMask = Mat()
            val lowerBound = Scalar(
                (meanB - DEFAULT_COLOR_TOLERANCE).coerceAtLeast(0.0),
                (meanG - DEFAULT_COLOR_TOLERANCE).coerceAtLeast(0.0),
                (meanR - DEFAULT_COLOR_TOLERANCE).coerceAtLeast(0.0)
            )
            val upperBound = Scalar(
                (meanB + DEFAULT_COLOR_TOLERANCE).coerceAtMost(255.0),
                (meanG + DEFAULT_COLOR_TOLERANCE).coerceAtMost(255.0),
                (meanR + DEFAULT_COLOR_TOLERANCE).coerceAtMost(255.0)
            )
            Core.inRange(bgr, lowerBound, upperBound, mainColorMask)
            
            // 检测不同颜色的区域（不在主要颜色范围内的区域）
            // 使用更严格的方法：直接使用differentColorMask，不进行形态学操作，避免误连接
            val differentColorMask = Mat()
            Core.bitwise_not(mainColorMask, differentColorMask)
            
            // 查找不同颜色区域的轮廓（不使用形态学操作，直接检测原始的不同颜色区域）
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                differentColorMask,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )
            
            // 创建用于计算纯色占比的mask（初始化为全0，然后只标记主要颜色区域）
            val filteredMask = Mat()
            filteredMask.create(bgr.rows(), bgr.cols(), mainColorMask.type())
            filteredMask.setTo(Scalar(0.0)) // 初始化为全0
            
            // 先复制主要颜色mask
            mainColorMask.copyTo(filteredMask)
            
            // 将不同颜色的区域按照矩形标记为非纯色（在计算纯色占比时排除这些区域）
            // 不直接去除矩形区域，而是将矩形区域标记为非纯色
            var markedCount = 0
            var totalMarkedArea = 0.0
            val minAreaToMark = 10.0 // 只标记面积大于10像素的区域，避免噪声
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area >= minAreaToMark) {
                    // 获取轮廓的边界矩形
                    val rect = Imgproc.boundingRect(contour)
                    // 在filteredMask中将该矩形区域置为0（标记为非纯色，在计算占比时排除）
                    // 这样矩形标注的区域就不会被计入纯色占比
                    Imgproc.rectangle(
                        filteredMask,
                        Point(rect.x.toDouble(), rect.y.toDouble()),
                        Point((rect.x + rect.width).toDouble(), (rect.y + rect.height).toDouble()),
                        Scalar(0.0),
                        -1 // 填充整个矩形，标记为非纯色
                    )
                    markedCount++
                    totalMarkedArea += (rect.width * rect.height).toDouble()
                    OmniLog.d(TAG, "Marked non-solid color rectangle: rect=${rect.width}x${rect.height}, contourArea=$area")
                }
                contour.release()
            }
            OmniLog.d(TAG, "Total marked as non-solid: $markedCount rectangles, total marked area=$totalMarkedArea")
            
            // 计算排除非纯色区域后的主要颜色占比
            // filteredMask中为1的像素是纯色区域，为0的像素是非纯色区域（已排除）
            val mainColorPixels = Core.countNonZero(filteredMask)
            val totalPixels = filteredMask.rows() * filteredMask.cols()
            val mainColorRatio = if (totalPixels > 0) {
                mainColorPixels.toFloat() / totalPixels
            } else {
                0f
            }
            
            // 释放资源
            bgr.release()
            mean.release()
            stddev.release()
            mainColorMask.release()
            differentColorMask.release()
            hierarchy.release()
            filteredMask.release()
            
            // 判断是否为纯色：方差小 或 主要颜色占比高（去除不同颜色区域后）
            val isSolidColor = maxVariance <= DEFAULT_COLOR_VARIANCE_THRESHOLD || 
                              mainColorRatio >= whiteScreenThreshold
            
            // 使用主要颜色占比作为 whiteRatio（保持接口兼容）
            val whiteRatio = mainColorRatio
            val confidence = if (isSolidColor) {
                // 如果方差小，置信度更高
                if (maxVariance <= DEFAULT_COLOR_VARIANCE_THRESHOLD) {
                    (1.0 - (maxVariance / DEFAULT_COLOR_VARIANCE_THRESHOLD).coerceIn(0.0, 1.0)).toFloat()
                } else {
                    mainColorRatio
                }
            } else {
                0f
            }
            
            OmniLog.d(
                TAG, 
                "Solid color detection: variance=$maxVariance (threshold=${DEFAULT_COLOR_VARIANCE_THRESHOLD}), " +
                "mainColorRatio=$mainColorRatio, meanRGB=[$meanB,$meanG,$meanR], isSolidColor=$isSolidColor"
            )
            
            return WhiteScreenResult(isSolidColor, whiteRatio, confidence)
        } catch (e: Exception) {
            OmniLog.e(TAG, "Solid color detection failed: ${e.message}", e)
            return WhiteScreenResult(false, 0f, 0f)
        } finally {
            mat.release()
        }
    }
    
    /**
     * 快速检测（降采样以提高性能）
     * @param bitmap 截图
     * @param scale 降采样比例，默认 0.3
     * @return 检测结果
     */
    fun detectFast(
        bitmap: Bitmap,
        scale: Float = 0.3f
    ): WhiteScreenResult {
        if (bitmap.isRecycled) {
            return WhiteScreenResult(false, 0f, 0f)
        }
        
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        try {
            // 降采样
            val resized = Mat()
            val newWidth = (mat.width() * scale).toInt().coerceAtLeast(1)
            val newHeight = (mat.height() * scale).toInt().coerceAtLeast(1)
            Imgproc.resize(mat, resized, Size(newWidth.toDouble(), newHeight.toDouble()))
            
            // 转换为BGR格式
            val bgr = Mat()
            Imgproc.cvtColor(resized, bgr, Imgproc.COLOR_RGBA2BGR)
            
            // 计算RGB三个通道的方差
            val mean = MatOfDouble()
            val stddev = MatOfDouble()
            Core.meanStdDev(bgr, mean, stddev)
            
            val stddevB = stddev.get(0, 0)[0]
            val stddevG = stddev.get(1, 0)[0]
            val stddevR = stddev.get(2, 0)[0]
            val varianceB = stddevB * stddevB
            val varianceG = stddevG * stddevG
            val varianceR = stddevR * stddevR
            val maxVariance = maxOf(varianceB, varianceG, varianceR)
            
            // 检测主要颜色占比（改进版：去除不同颜色的矩形区域）
            val meanB = mean.get(0, 0)[0]
            val meanG = mean.get(1, 0)[0]
            val meanR = mean.get(2, 0)[0]
            
            val mainColorMask = Mat()
            val lowerBound = Scalar(
                (meanB - DEFAULT_COLOR_TOLERANCE).coerceAtLeast(0.0),
                (meanG - DEFAULT_COLOR_TOLERANCE).coerceAtLeast(0.0),
                (meanR - DEFAULT_COLOR_TOLERANCE).coerceAtLeast(0.0)
            )
            val upperBound = Scalar(
                (meanB + DEFAULT_COLOR_TOLERANCE).coerceAtMost(255.0),
                (meanG + DEFAULT_COLOR_TOLERANCE).coerceAtMost(255.0),
                (meanR + DEFAULT_COLOR_TOLERANCE).coerceAtMost(255.0)
            )
            Core.inRange(bgr, lowerBound, upperBound, mainColorMask)
            
            // 检测不同颜色的区域（不在主要颜色范围内的区域）
            // 使用更严格的方法：直接使用differentColorMask，不进行形态学操作，避免误连接
            val differentColorMask = Mat()
            Core.bitwise_not(mainColorMask, differentColorMask)
            
            // 查找不同颜色区域的轮廓（不使用形态学操作，直接检测原始的不同颜色区域）
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                differentColorMask,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )
            
            // 创建用于计算纯色占比的mask（初始化为全0，然后只标记主要颜色区域）
            val filteredMask = Mat()
            filteredMask.create(bgr.rows(), bgr.cols(), mainColorMask.type())
            filteredMask.setTo(Scalar(0.0)) // 初始化为全0
            
            // 先复制主要颜色mask
            mainColorMask.copyTo(filteredMask)
            
            // 将不同颜色的区域按照矩形标记为非纯色（在计算纯色占比时排除这些区域）
            // 不直接去除矩形区域，而是将矩形区域标记为非纯色
            var markedCount = 0
            var totalMarkedArea = 0.0
            val minAreaToMark = 10.0 // 只标记面积大于10像素的区域，避免噪声
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area >= minAreaToMark) {
                    // 获取轮廓的边界矩形
                    val rect = Imgproc.boundingRect(contour)
                    // 在filteredMask中将该矩形区域置为0（标记为非纯色，在计算占比时排除）
                    // 这样矩形标注的区域就不会被计入纯色占比
                    Imgproc.rectangle(
                        filteredMask,
                        Point(rect.x.toDouble(), rect.y.toDouble()),
                        Point((rect.x + rect.width).toDouble(), (rect.y + rect.height).toDouble()),
                        Scalar(0.0),
                        -1 // 填充整个矩形，标记为非纯色
                    )
                    markedCount++
                    totalMarkedArea += (rect.width * rect.height).toDouble()
                }
                contour.release()
            }
            
            // 计算排除非纯色区域后的主要颜色占比
            // filteredMask中为1的像素是纯色区域，为0的像素是非纯色区域（已排除）
            val mainColorPixels = Core.countNonZero(filteredMask)
            val totalPixels = filteredMask.rows() * filteredMask.cols()
            val mainColorRatio = if (totalPixels > 0) {
                mainColorPixels.toFloat() / totalPixels
            } else {
                0f
            }
            
            // 释放资源
            resized.release()
            bgr.release()
            mean.release()
            stddev.release()
            mainColorMask.release()
            differentColorMask.release()
            hierarchy.release()
            filteredMask.release()
            
            val isSolidColor = maxVariance <= DEFAULT_COLOR_VARIANCE_THRESHOLD || 
                              mainColorRatio >= DEFAULT_SOLID_COLOR_THRESHOLD
            val whiteRatio = mainColorRatio
            val confidence = if (isSolidColor) {
                if (maxVariance <= DEFAULT_COLOR_VARIANCE_THRESHOLD) {
                    (1.0 - (maxVariance / DEFAULT_COLOR_VARIANCE_THRESHOLD).coerceIn(0.0, 1.0)).toFloat()
                } else {
                    mainColorRatio
                }
            } else {
                0f
            }
            
            return WhiteScreenResult(isSolidColor, whiteRatio, confidence)
        } catch (e: Exception) {
            OmniLog.e(TAG, "Fast solid color detection failed: ${e.message}", e)
            return WhiteScreenResult(false, 0f, 0f)
        } finally {
            mat.release()
        }
    }
}

