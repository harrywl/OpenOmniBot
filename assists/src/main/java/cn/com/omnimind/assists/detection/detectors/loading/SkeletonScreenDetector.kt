package cn.com.omnimind.assists.detection.detectors.loading

import android.graphics.Bitmap
import cn.com.omnimind.assists.detection.OpenCVInitializer
import cn.com.omnimind.baselib.util.OmniLog
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * 骨架屏检测器
 * 通过边缘检测、纹理分析和矩形检测来识别骨架屏
 */
object SkeletonScreenDetector {
    private const val TAG = "SkeletonScreenDetector"
    
    // 骨架屏检测阈值
    private const val DEFAULT_EDGE_RATIO_THRESHOLD = 0.03 // 边缘占比阈值（进一步降低，浅色占位符边缘不明显）
    private const val DEFAULT_VARIANCE_THRESHOLD = 600.0 // 整体灰度方差阈值（放宽，允许一些变化）
    private const val DEFAULT_MIN_RECTANGLES = 3 // 至少检测到的灰色块数量（骨架屏通常有多个灰色块排列在一起）
    private const val DEFAULT_MIN_RECTANGLE_AREA = 50.0 // 最小矩形面积（进一步降低，因为排除高亮后轮廓变小）
    private const val DEFAULT_MAX_RECTANGLE_AREA = 1000000.0 // 最大矩形面积（提高，允许大面积的骨架屏区域，通过面积占比进一步过滤）
    // 宽高比和solidity不再使用，因为骨架屏就是纯色块，不需要严格的形状要求
    // 只保留最小宽高比用于过滤极端细长的线条
    private const val DEFAULT_MIN_BLOCK_ASPECT_RATIO = 0.05 // 最小宽高比（只过滤极端细长的线条，允许条形和方形）
    private const val DEFAULT_MIN_PERIMETER = 30.0 // 最小周长（降低，允许更小的轮廓）
    private const val DEFAULT_STATUS_BAR_HEIGHT_RATIO = 0.08 // 状态栏高度占比（过滤状态栏区域的矩形）
    
    // 纯色判断阈值（关键参数）
    private const val DEFAULT_RECTANGLE_VARIANCE_THRESHOLD = 300.0 // 单个矩形内部灰度方差阈值（进一步放宽，允许轻微变化和渐变）
    private const val DEFAULT_MIN_GRAY_VALUE = 0.0 // 最小灰度值（扩大到全范围，通过RGB色差过滤）
    private const val DEFAULT_MAX_GRAY_VALUE = 255.0 // 最大灰度值（全范围，通过RGB色差过滤）
    private const val DEFAULT_MAX_RGB_DIFF = 20.0 // RGB三个通道之间的最大差值（放宽，确保是黑白灰色）
    private const val DEFAULT_HIGHLIGHT_THRESHOLD = 248.0 // 滚动高亮区域的灰度阈值（进一步提高，只排除接近纯白色的高亮区域，避免误排除浅灰色占位符）
    
    /**
     * 检测结果
     */
    data class SkeletonScreenResult(
        /** 是否是骨架屏 */
        val isSkeletonScreen: Boolean,
        /** 边缘占比 [0,1] */
        val edgeRatio: Float,
        /** 灰度方差 */
        val variance: Double,
        /** 检测到的矩形数量 */
        val rectangleCount: Int,
        /** 置信度 [0,1] */
        val confidence: Float
    )
    
    /**
     * 检测骨架屏
     * @param bitmap 截图
     * @param scale 降采样比例，默认 0.3（提高性能，控制在100ms以内）
     * @param edgeRatioThreshold 边缘占比阈值，默认 0.15
     * @param varianceThreshold 灰度方差阈值，默认 500.0
     * @param minRectangles 最小矩形数量，默认 3
     * @return 检测结果
     */
    fun detect(
        bitmap: Bitmap,
        scale: Float = 0.3f, // 降低到0.3以提高性能
        edgeRatioThreshold: Double = DEFAULT_EDGE_RATIO_THRESHOLD,
        varianceThreshold: Double = DEFAULT_VARIANCE_THRESHOLD,
        minRectangles: Int = DEFAULT_MIN_RECTANGLES
    ): SkeletonScreenResult {
        OpenCVInitializer.ensureInitialized()
        
        if (bitmap.isRecycled) {
            OmniLog.w(TAG, "Bitmap is recycled")
            return SkeletonScreenResult(false, 0f, 0.0, 0, 0f)
        }
        
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        try {
            OmniLog.d(
                TAG, 
                "Starting skeleton detection: bitmapSize=${bitmap.width}x${bitmap.height}, " +
                "scale=$scale, edgeThreshold=$edgeRatioThreshold, varianceThreshold=$varianceThreshold, " +
                "minRectangles=$minRectangles, grayRange=[${DEFAULT_MIN_GRAY_VALUE}, ${DEFAULT_MAX_GRAY_VALUE}], " +
                "rectVarianceThreshold=${DEFAULT_RECTANGLE_VARIANCE_THRESHOLD}, maxRGBDiff=${DEFAULT_MAX_RGB_DIFF}"
            )
            
            // 降采样以提高性能
            val resized = Mat()
            val newWidth = (mat.width() * scale).toInt().coerceAtLeast(1)
            val newHeight = (mat.height() * scale).toInt().coerceAtLeast(1)
            Imgproc.resize(mat, resized, Size(newWidth.toDouble(), newHeight.toDouble()))
            OmniLog.d(TAG, "Resized to: ${newWidth}x${newHeight}")
            
            // 转换为灰度图（用于边缘检测和轮廓检测）
            val gray = Mat()
            Imgproc.cvtColor(resized, gray, Imgproc.COLOR_RGBA2GRAY)
            
            // 保留原始彩色图像用于RGB色差检查（转换为BGR格式，OpenCV标准格式）
            val color = Mat()
            Imgproc.cvtColor(resized, color, Imgproc.COLOR_RGBA2BGR)
            
            // 1. 纹理分析：计算灰度方差（用于整体判断）
            val mean = MatOfDouble()
            val stddev = MatOfDouble()
            Core.meanStdDev(gray, mean, stddev)
            val variance = stddev.get(0, 0)[0] * stddev.get(0, 0)[0]
            val meanGray = mean.get(0, 0)[0]
            
            // 2. 快速估算灰度范围（跳过耗时的直方图计算）
            // 基于均值和方差快速估算，而不是计算完整直方图
            // 骨架屏通常是浅灰色，范围在0-250之间（扩大范围以捕获更多占位符）
            val minGrayForMask = if (meanGray < 100) {
                // 整体较暗，使用更宽的范围
                0.0
            } else if (meanGray > 200) {
                // 整体较亮，但占位符可能比背景稍暗，使用更宽的范围
                // 不要限制下限，确保能捕获到浅灰色占位符
                0.0
            } else {
                // 中等亮度，使用标准范围
                0.0 // 改为0.0，确保能捕获所有可能的占位符
            }
            val maxGrayForMask = if (meanGray > 200) {
                // 整体较亮，但占位符可能接近背景色，使用更宽的范围
                250.0
            } else {
                // 使用标准上限
                250.0
            }
            
            OmniLog.d(
                TAG, 
                "Fast gray range estimation: meanGray=$meanGray, estimatedRange=[$minGrayForMask, $maxGrayForMask]"
            )
            
            // 3. 直接基于颜色范围检测浅灰色占位符（使用快速估算的范围）
            val grayMaskRaw = Mat()
            
            Core.inRange(gray, Scalar(minGrayForMask), Scalar(maxGrayForMask), grayMaskRaw)
            
            // 检测并排除滚动的高亮区域（shimmer effect）
            // 这些高亮区域通常比占位符更亮，灰度值接近白色
            // 但如果整体画面很亮，浅灰色占位符可能被误判，所以使用更保守的阈值
            val highlightMask = Mat()
            val highlightThreshold = 250.0 // 进一步提高到250，只排除接近纯白色的高亮区域
            Core.inRange(gray, Scalar(highlightThreshold), Scalar(255.0), highlightMask)
            
            val totalPixelsForMask = grayMaskRaw.rows() * grayMaskRaw.cols()
            val highlightPixels = Core.countNonZero(highlightMask)
            val highlightRatio = if (totalPixelsForMask > 0) {
                highlightPixels.toFloat() / totalPixelsForMask
            } else {
                0f
            }
            OmniLog.d(TAG, "Highlight mask: threshold=$highlightThreshold, ratio=${highlightRatio * 100}%, pixels=$highlightPixels")
            
            // 从占位符 mask 中排除高亮区域
            // 但如果高亮区域占比太高（> 95%），说明画面整体很亮，可能浅灰色占位符也被误判了
            // 这种情况下，不排除高亮区域，直接使用原始mask
            val grayMask = if (highlightRatio > 0.95) {
                OmniLog.d(TAG, "Highlight ratio too high (${highlightRatio * 100}%), likely bright screen, not excluding highlights")
                // 画面整体很亮，不排除高亮区域，直接使用原始mask
                highlightMask.release()
                grayMaskRaw
            } else {
                // 正常排除高亮区域
                val highlightMaskInverted = Mat()
                Core.bitwise_not(highlightMask, highlightMaskInverted)
                val grayMaskFiltered = Mat()
                Core.bitwise_and(grayMaskRaw, highlightMaskInverted, grayMaskFiltered)
                
                highlightMask.release()
                highlightMaskInverted.release()
                grayMaskRaw.release()
                grayMaskFiltered
            }
            
            val maskPixels = Core.countNonZero(grayMask)
            val maskRatio = if (totalPixelsForMask > 0) {
                maskPixels.toFloat() / totalPixelsForMask
            } else {
                0f
            }
            OmniLog.d(TAG, "Gray mask (after excluding highlights): adaptiveRange=[$minGrayForMask, $maxGrayForMask], maskRatio=${maskRatio * 100}%, maskPixels=$maskPixels")
            
            // 如果 mask 太小，尝试更宽松的范围
            val grayMaskFinal = if (maskRatio < 0.01) {
                // mask 太小（< 1%），尝试更宽松的范围（包括更浅的灰色）
                OmniLog.d(TAG, "Mask too small (ratio=${maskRatio * 100}%), trying wider range [0, 250] without highlight exclusion")
                val widerMaskRaw = Mat()
                Core.inRange(gray, Scalar(0.0), Scalar(250.0), widerMaskRaw)
                
                // 如果mask太小，说明可能浅灰色占位符被高亮排除逻辑误判了
                // 这种情况下，不排除高亮区域，直接使用更宽的范围
                val widerPixels = Core.countNonZero(widerMaskRaw)
                val widerRatio = if (totalPixelsForMask > 0) {
                    widerPixels.toFloat() / totalPixelsForMask
                } else {
                    0f
                }
                OmniLog.d(TAG, "Wider mask (without highlight exclusion): ratio=${widerRatio * 100}%, pixels=$widerPixels")
                
                grayMask.release()
                widerMaskRaw
            } else {
                grayMask
            }
            
            // 形态学操作，填充空洞，连接断开的区域，去除噪声
            // 使用更小的核以提高性能
            val maskKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            val grayMaskMorph = Mat()
            // 只进行一次闭运算，填充内部空洞（跳过开运算以提高性能）
            Imgproc.morphologyEx(grayMaskFinal, grayMaskMorph, Imgproc.MORPH_CLOSE, maskKernel)
            
            val morphPixels = Core.countNonZero(grayMaskMorph)
            val morphRatio = if (totalPixelsForMask > 0) {
                morphPixels.toFloat() / totalPixelsForMask
            } else {
                0f
            }
            OmniLog.d(TAG, "After morphology: ratio=${morphRatio * 100}%, pixels=$morphPixels")
            
            maskKernel.release()
            if (grayMaskFinal != grayMask) {
                grayMaskFinal.release()
            } else {
                grayMask.release()
            }
            
            // 4. 轮廓检测 - 直接基于颜色范围检测
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            // 使用 RETR_TREE 获取所有轮廓（包括嵌套的），因为骨架屏占位符内部可能有更浅的线条
            Imgproc.findContours(
                grayMaskMorph, 
                contours, 
                hierarchy, 
                Imgproc.RETR_TREE, 
                Imgproc.CHAIN_APPROX_SIMPLE
            )
            
            // 如果轮廓数量为0，但mask有像素，说明可能是占位符与背景对比度太低
            // 这种情况下，尝试使用连通域分析来检测色块
            val finalContours = if (contours.isEmpty() && maskRatio > 0.001) {
                OmniLog.d(TAG, "No contours found but mask has pixels (ratio=${maskRatio * 100}%), trying connected components analysis")
                // 使用连通域分析来检测色块
                val labels = Mat()
                val stats = Mat()
                val centroids = Mat()
                val numLabels = Imgproc.connectedComponentsWithStats(grayMaskMorph, labels, stats, centroids)
                
                // 将连通域转换为轮廓
                val connectedContours = mutableListOf<MatOfPoint>()
                for (i in 1 until numLabels) {
                    // 获取连通域的统计信息
                    val x = stats.get(i, Imgproc.CC_STAT_LEFT)[0].toInt()
                    val y = stats.get(i, Imgproc.CC_STAT_TOP)[0].toInt()
                    val width = stats.get(i, Imgproc.CC_STAT_WIDTH)[0].toInt()
                    val height = stats.get(i, Imgproc.CC_STAT_HEIGHT)[0].toInt()
                    val area = stats.get(i, Imgproc.CC_STAT_AREA)[0].toDouble()
                    
                    // 只处理足够大的连通域
                    if (area >= DEFAULT_MIN_RECTANGLE_AREA) {
                        // 创建矩形轮廓
                        val rect = Rect(x, y, width, height)
                        val points = arrayOf(
                            Point(rect.x.toDouble(), rect.y.toDouble()),
                            Point((rect.x + rect.width).toDouble(), rect.y.toDouble()),
                            Point((rect.x + rect.width).toDouble(), (rect.y + rect.height).toDouble()),
                            Point(rect.x.toDouble(), (rect.y + rect.height).toDouble())
                        )
                        connectedContours.add(MatOfPoint(*points))
                    }
                }
                
                labels.release()
                stats.release()
                centroids.release()
                
                OmniLog.d(TAG, "Found ${connectedContours.size} connected components as potential color blocks")
                connectedContours
            } else {
                contours
            }
            
            // 计算边缘像素占比（用于辅助判断）
            val edgePixels = Core.countNonZero(grayMaskMorph)
            val totalPixels = grayMaskMorph.rows() * grayMaskMorph.cols()
            val edgeRatio = if (totalPixels > 0) {
                edgePixels.toFloat() / totalPixels
            } else {
                0f
            }
            
            // 释放临时Mat
            grayMaskMorph.release()
            
            // 统计矩形轮廓数量（包括圆角矩形）
            var rectangleCount = 0
            val screenArea = resized.rows() * resized.cols()
            val screenHeight = resized.rows()
            val statusBarHeight = (screenHeight * DEFAULT_STATUS_BAR_HEIGHT_RATIO).toInt()
            
            OmniLog.d(TAG, "Found ${finalContours.size} contours to analyze, edgeRatio=${edgeRatio * 100}%, variance=$variance")
            
            // 如果轮廓数量很少，放宽颜色检查条件
            val shouldRelaxColorCheck = finalContours.size < 5
            val relaxedRGBDiff = if (shouldRelaxColorCheck) {
                DEFAULT_MAX_RGB_DIFF * 1.5 // 放宽50%
            } else {
                DEFAULT_MAX_RGB_DIFF
            }
            val relaxedVarianceThreshold = if (shouldRelaxColorCheck) {
                DEFAULT_RECTANGLE_VARIANCE_THRESHOLD * 1.5 // 放宽50%
            } else {
                DEFAULT_RECTANGLE_VARIANCE_THRESHOLD
            }
            
            if (shouldRelaxColorCheck) {
                OmniLog.d(TAG, "Relaxing color check: RGB diff threshold=$relaxedRGBDiff, variance threshold=$relaxedVarianceThreshold")
            }
            
            var filteredByArea = 0
            var filteredByAreaRatio = 0
            var filteredByPerimeter = 0
            var filteredByStatusBar = 0
            var filteredByAspectRatio = 0
            var filteredByColor = 0
            
            // 限制分析的轮廓数量以提高性能（只分析前50个最大的轮廓）
            val maxContoursToAnalyze = 50
            val sortedContours = finalContours.sortedByDescending { Imgproc.contourArea(it) }
            val contoursToAnalyze = sortedContours.take(maxContoursToAnalyze)
            
            // 提前终止：如果已经检测到足够的矩形，可以提前返回
            var earlyExit = false
            
            var analyzedCount = 0
            for (contour in contoursToAnalyze) {
                analyzedCount++
                // 如果已经检测到足够的矩形，提前退出循环
                if (rectangleCount >= minRectangles * 2) {
                    earlyExit = true
                    OmniLog.d(TAG, "Early exit: detected enough rectangles ($rectangleCount)")
                    // 释放当前轮廓和剩余未处理的轮廓
                    contour.release()
                    contoursToAnalyze.drop(analyzedCount).forEach { it.release() }
                    break
                }
                val area = Imgproc.contourArea(contour)
                val areaRatio = area / screenArea
                
                // 过滤太小的矩形和过大的矩形
                if (area < DEFAULT_MIN_RECTANGLE_AREA || area > DEFAULT_MAX_RECTANGLE_AREA) {
                    filteredByArea++
                    OmniLog.d(TAG, "Filtered by area: area=$area (min=${DEFAULT_MIN_RECTANGLE_AREA}, max=${DEFAULT_MAX_RECTANGLE_AREA}), areaRatio=${areaRatio * 100}%")
                    contour.release()
                    continue
                }
                
                // 过滤面积占比过大的矩形（可能是整个屏幕）
                // 但如果只有一个轮廓且面积占比很大，可能是骨架屏占位符覆盖了大部分屏幕
                // 这种情况下，我们需要尝试分割这个大轮廓
                // 提高面积占比阈值，允许更大的占位符区域（从0.5提高到0.7）
                if (areaRatio > 0.7) {
                    // 如果只有一个轮廓且面积占比 > 80%，可能是整个屏幕都是骨架屏
                    // 这种情况下，我们应该检查这个区域是否是纯色的浅灰色
                    if (finalContours.size == 1 && areaRatio > 0.8) {
                        OmniLog.d(TAG, "Large single contour detected (areaRatio=${areaRatio * 100}%), checking if it's a skeleton screen")
                        // 检查这个区域的纯色特性
                        val rect = Imgproc.boundingRect(contour)
                        val expandedX = (rect.x - 2).coerceAtLeast(0)
                        val expandedY = (rect.y - 2).coerceAtLeast(0)
                        val expandedWidth = (rect.width + 4).coerceAtMost(gray.cols() - expandedX)
                        val expandedHeight = (rect.height + 4).coerceAtMost(gray.rows() - expandedY)
                        val expandedRect = Rect(expandedX, expandedY, expandedWidth, expandedHeight)
                        
                        if (expandedRect.width > 0 && expandedRect.height > 0 &&
                            expandedRect.x + expandedRect.width <= gray.cols() &&
                            expandedRect.y + expandedRect.height <= gray.rows() &&
                            expandedRect.x + expandedRect.width <= color.cols() &&
                            expandedRect.y + expandedRect.height <= color.rows()) {
                            
                            val roiGray = Mat(gray, expandedRect)
                            val roiColor = Mat(color, expandedRect)
                            
                            val roiMean = MatOfDouble()
                            val roiStddev = MatOfDouble()
                            Core.meanStdDev(roiGray, roiMean, roiStddev)
                            val roiMeanValue = roiMean.get(0, 0)[0]
                            val roiVariance = roiStddev.get(0, 0)[0] * roiStddev.get(0, 0)[0]
                            
                            val roiColorMean = MatOfDouble()
                            val roiColorStddev = MatOfDouble()
                            Core.meanStdDev(roiColor, roiColorMean, roiColorStddev)
                            val meanB = roiColorMean.get(0, 0)[0]
                            val meanG = roiColorMean.get(1, 0)[0]
                            val meanR = roiColorMean.get(2, 0)[0]
                            
                            val maxRGBDiff = maxOf(
                                kotlin.math.abs(meanR - meanG),
                                kotlin.math.abs(meanR - meanB),
                                kotlin.math.abs(meanG - meanB)
                            )
                            
                            val isGrayscale = maxRGBDiff <= relaxedRGBDiff
                            val isInGrayRange = roiMeanValue >= DEFAULT_MIN_GRAY_VALUE && 
                                               roiMeanValue <= DEFAULT_MAX_GRAY_VALUE
                            val adjustedVarianceThreshold = if (roiMeanValue >= 200.0) {
                                relaxedVarianceThreshold * 0.7
                            } else {
                                relaxedVarianceThreshold
                            }
                            val isSolidColor = roiVariance <= adjustedVarianceThreshold
                            
                            roiMean.release()
                            roiStddev.release()
                            roiColorMean.release()
                            roiColorStddev.release()
                            roiGray.release()
                            roiColor.release()
                            
                            if (isInGrayRange && isGrayscale && isSolidColor) {
                                // 这是一个大的骨架屏区域，尝试分割成多个矩形
                                // 使用网格分割或者基于连通域的分割
                                OmniLog.d(TAG, "Large skeleton screen area detected, attempting to count rectangles by grid")
                                // 简单方法：基于面积估算矩形数量
                                // 假设每个骨架屏占位符的平均面积约为屏幕的 5-10%
                                val estimatedRectSize = screenArea * 0.05
                                val estimatedCount = (area / estimatedRectSize).toInt().coerceAtLeast(1).coerceAtMost(20)
                                rectangleCount = estimatedCount
                                OmniLog.d(TAG, "Estimated rectangle count: $estimatedCount (based on area=$area, estimatedSize=$estimatedRectSize)")
                            } else {
                                filteredByAreaRatio++
                                OmniLog.d(TAG, "Filtered large contour: isGrayscale=$isGrayscale, isSolidColor=$isSolidColor, meanGray=$roiMeanValue, variance=$roiVariance")
                            }
                        } else {
                            filteredByAreaRatio++
                        }
                    } else {
                        filteredByAreaRatio++
                        OmniLog.d(TAG, "Filtered by area ratio: areaRatio=${areaRatio * 100}%")
                    }
                    contour.release()
                    continue
                }
                
                val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
                // 过滤周长过小的轮廓
                if (peri < DEFAULT_MIN_PERIMETER) {
                    filteredByPerimeter++
                    contour.release()
                    continue
                }
                
                // 计算边界框
                val rect = Imgproc.boundingRect(contour)
                
                // 过滤状态栏区域的小矩形（通常是图标，不是骨架屏占位符）
                // 但如果矩形足够大，可能是占位符延伸到状态栏区域，应该保留
                // OpenCV Rect 使用 x, y, width, height，而不是 left, top, right, bottom
                if (rect.y < statusBarHeight && area < 500.0) {
                    // 只过滤状态栏区域的小矩形（面积 < 500），大矩形可能是占位符
                    filteredByStatusBar++
                    OmniLog.d(TAG, "Filtered by status bar: area=$area, rect.y=${rect.y}, statusBarHeight=$statusBarHeight")
                    contour.release()
                    continue
                }
                
                // 简化检测逻辑：骨架屏就是纯色色块（方形或条形），不需要严格的形状要求
                // 直接检查是否是纯色块，而不需要严格的矩形形状验证
                    val width = rect.width.toDouble()
                    val height = rect.height.toDouble()
                    
                    if (width > 0 && height > 0) {
                    // 检查宽高比是否合理（允许条形和方形，但过滤极端细长的线条）
                        val aspectRatio = minOf(width, height) / maxOf(width, height)
                        
                    // 放宽宽高比要求，允许条形（长条形）和方形
                    if (aspectRatio < 0.05) {
                        // 只过滤极端细长的线条（宽高比 < 0.05）
                        filteredByAspectRatio++
                        OmniLog.d(TAG, "Filtered by aspect ratio: aspectRatio=$aspectRatio (too thin)")
                            contour.release()
                            continue
                        }
                    
                    OmniLog.d(TAG, "Potential gray block: area=$area, rect=${rect.width}x${rect.height}, aspectRatio=$aspectRatio")
                    
                    // 骨架屏特征：多个独立的灰色或浅灰色矩形块排列在一起
                    // 直接检测每个独立的灰色块，不需要嵌套检测
                    // 提取色块区域（使用边界框，稍微扩大以确保包含整个色块）
                    val expandedX = (rect.x - 2).coerceAtLeast(0)
                    val expandedY = (rect.y - 2).coerceAtLeast(0)
                    val expandedWidth = (rect.width + 4).coerceAtMost(gray.cols() - expandedX)
                    val expandedHeight = (rect.height + 4).coerceAtMost(gray.rows() - expandedY)
                    val expandedRect = Rect(expandedX, expandedY, expandedWidth, expandedHeight)
                    
                    if (expandedRect.width > 0 && expandedRect.height > 0 &&
                        expandedRect.x + expandedRect.width <= gray.cols() &&
                        expandedRect.y + expandedRect.height <= gray.rows() &&
                        expandedRect.x + expandedRect.width <= color.cols() &&
                        expandedRect.y + expandedRect.height <= color.rows()) {
                        
                        val roiGray = Mat(gray, expandedRect)
                        val roiColor = Mat(color, expandedRect)
                        
                        // 计算色块的颜色特性
                        val roiMean = MatOfDouble()
                        val roiStddev = MatOfDouble()
                        Core.meanStdDev(roiGray, roiMean, roiStddev)
                        val roiMeanValue = roiMean.get(0, 0)[0]
                        val roiVariance = roiStddev.get(0, 0)[0] * roiStddev.get(0, 0)[0]
                        
                        // 计算RGB色差（确保是黑白灰色）
                        val roiColorMean = MatOfDouble()
                        val roiColorStddev = MatOfDouble()
                        Core.meanStdDev(roiColor, roiColorMean, roiColorStddev)
                        // BGR格式：B, G, R三个通道
                        val meanB = roiColorMean.get(0, 0)[0]
                        val meanG = roiColorMean.get(1, 0)[0]
                        val meanR = roiColorMean.get(2, 0)[0]
                        
                        // 计算RGB三个通道之间的最大差值
                        val maxRGBDiff = maxOf(
                            kotlin.math.abs(meanR - meanG),
                            kotlin.math.abs(meanR - meanB),
                            kotlin.math.abs(meanG - meanB)
                        )
                        
                        // 检查是否为黑白灰色（RGB色差很小）
                        val isGrayscale = maxRGBDiff <= relaxedRGBDiff
                        
                        // 检查是否是灰色或浅灰色块（灰度值范围：100-250，允许浅灰色到接近白色）
                        val isGrayBlock = roiMeanValue >= 100.0 && roiMeanValue <= 250.0
                        
                        // 允许灰色块有渐变（方差可以稍大，因为可能有渐变效果）
                        val blockVarianceThreshold = relaxedVarianceThreshold * 2.5
                        val isGrayGradient = roiVariance <= blockVarianceThreshold
                        
                        roiMean.release()
                        roiStddev.release()
                        roiColorMean.release()
                        roiColorStddev.release()
                        roiGray.release()
                        roiColor.release()
                        
                        // 核心判断：只要是灰色或浅灰色块（RGB色差小，灰度值在合理范围，允许渐变），就认为是骨架屏块
                        if (isGrayscale && isGrayBlock && isGrayGradient) {
                            rectangleCount++
                            OmniLog.d(
                                TAG, 
                                "Valid skeleton gray block: area=$area, aspectRatio=$aspectRatio, " +
                                "meanGray=$roiMeanValue, variance=$roiVariance, " +
                                "RGB=[$meanB,$meanG,$meanR], maxRGBDiff=$maxRGBDiff"
                            )
                        } else {
                            filteredByColor++
                            OmniLog.d(
                                TAG, 
                                "Filtered gray block: isGrayscale=$isGrayscale, isGrayBlock=$isGrayBlock, " +
                                "isGrayGradient=$isGrayGradient, meanGray=$roiMeanValue, variance=$roiVariance, " +
                                "RGB=[$meanB,$meanG,$meanR], maxRGBDiff=$maxRGBDiff (threshold=$relaxedRGBDiff, varianceThreshold=$blockVarianceThreshold)"
                            )
                        }
                    }
                }
                contour.release()
            }
            
            // 释放未分析的轮廓（sortedContours中未在contoursToAnalyze中的轮廓）
            // 注意：contoursToAnalyze中的轮廓已经在循环中释放了
            sortedContours.drop(maxContoursToAnalyze).forEach { it.release() }
            // 如果提前退出，剩余未处理的轮廓已经在循环中释放了
            
            // 输出过滤统计信息
            OmniLog.d(
                TAG, 
                "Contour filtering stats: total=${finalContours.size}, analyzed=${contoursToAnalyze.size}, " +
                "filteredByArea=$filteredByArea, filteredByAreaRatio=$filteredByAreaRatio, " +
                "filteredByPerimeter=$filteredByPerimeter, filteredByStatusBar=$filteredByStatusBar, " +
                "filteredByAspectRatio=$filteredByAspectRatio, filteredByColor=$filteredByColor, " +
                "validColorBlocks=$rectangleCount, earlyExit=$earlyExit"
            )
            
            // 综合判断（放宽逻辑，更容易识别骨架屏）
            val hasRegularEdges = edgeRatio >= edgeRatioThreshold
            val hasLowVariance = variance < varianceThreshold
            val hasRectangles = rectangleCount >= minRectangles
            
            // 骨架屏特征：浅灰色纯色矩形占位符 + 整体低方差
            // 由于已经对每个矩形进行了纯色检查，主要依赖矩形数量和整体特征
            val isSkeleton = when {
                // 有足够多的纯色矩形（最可靠的判断）
                hasRectangles -> true
                // 有矩形且整体方差较低（颜色单一）
                rectangleCount >= 1 && hasLowVariance -> true
                // 有矩形且边缘占比合理（说明有占位符区域）
                rectangleCount >= 1 && hasRegularEdges -> true
                // 即使没有检测到矩形，如果整体方差很低且边缘占比合理，也可能是骨架屏
                // （可能是因为占位符与背景对比度太低，轮廓检测失败）
                hasLowVariance && edgeRatio > 0.02 && variance < varianceThreshold * 0.5 -> true
                else -> false
            }
            
            // 计算置信度（根据矩形数量和纯色特征动态调整）
            var confidence = 0f
            // 整体低方差（纯色特征）权重更高
            if (hasLowVariance) {
                val varianceScore = (1.0 - (variance / varianceThreshold).coerceIn(0.0, 1.0)).toFloat()
                confidence += varianceScore * 0.4f
            }
            // 矩形数量越多，置信度越高（因为每个矩形都经过了纯色验证）
            if (hasRectangles) {
                val rectangleScore = minOf(1.0f, rectangleCount.toFloat() / (minRectangles * 2.0f))
                confidence += rectangleScore * 0.5f
            }
            // 边缘占比作为辅助判断
            if (hasRegularEdges) {
                confidence += 0.1f
            }
            
            // 释放资源
            resized.release()
            gray.release()
            color.release()
            hierarchy.release()
            // 释放未分析的轮廓（sortedContours中除了contoursToAnalyze之外的轮廓）
            // contoursToAnalyze中的轮廓已经在循环中释放了
            // 注意：sortedContours和finalContours指向相同的对象，所以只需要释放一次
            sortedContours.drop(maxContoursToAnalyze).forEach { it.release() }
            mean.release()
            stddev.release()
            
            OmniLog.d(
                TAG, 
                "Skeleton screen detection: edgeRatio=$edgeRatio (threshold=$edgeRatioThreshold), " +
                "variance=$variance (threshold=$varianceThreshold), " +
                "rectangles=$rectangleCount (min=$minRectangles), " +
                "isSkeleton=$isSkeleton, confidence=$confidence"
            )
            
            return SkeletonScreenResult(
                isSkeletonScreen = isSkeleton,
                edgeRatio = edgeRatio,
                variance = variance,
                rectangleCount = rectangleCount,
                confidence = confidence.coerceIn(0f, 1f)
            )
        } catch (e: Exception) {
            OmniLog.e(TAG, "Skeleton screen detection failed: ${e.message}", e)
            return SkeletonScreenResult(false, 0f, 0.0, 0, 0f)
        } finally {
            mat.release()
        }
    }
    
    /**
     * 快速检测（更激进的降采样）
     * @param bitmap 截图
     * @param scale 降采样比例，默认 0.3
     * @return 检测结果
     */
    fun detectFast(
        bitmap: Bitmap,
        scale: Float = 0.3f
    ): SkeletonScreenResult {
        return detect(bitmap, scale = scale)
    }
}

