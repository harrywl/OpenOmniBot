package cn.com.omnimind.assists.detection.detectors.loading

import android.graphics.Bitmap
import cn.com.omnimind.assists.detection.OpenCVInitializer
import cn.com.omnimind.baselib.util.OmniLog
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.abs

/**
 * Loading 指示器检测器
 * 检测位于中间部位有明显特征的加载指示器（不限于圆形）
 * 支持检测：圆形、矩形、线条、旋转动画等特征
 */
object LoadingIndicatorDetector {
    private const val TAG = "LoadingIndicatorDetector"
    
    // Loading 框检测阈值
    private const val DEFAULT_MIN_RADIUS = 10
    private const val DEFAULT_MAX_RADIUS = 100
    private const val DEFAULT_MIN_CIRCLES = 1 // 至少检测到1个圆形
    
    // 中间区域定义（更严格的中间区域）
    private const val CENTER_REGION_RATIO = 0.3 // 中间区域占屏幕的比例（30%）
    private const val MIN_DISTANCE_FROM_EDGE = 30 // 指示器中心距离屏幕边缘的最小距离
    private const val MAX_DISTANCE_FROM_CENTER_RATIO = 0.25 // 指示器中心距离屏幕中心的最大距离比例（更严格，只检测正中央）
    
    // 形状验证阈值
    private const val MIN_CIRCLE_RADIUS = 8 // 最小有效圆形半径
    private const val MAX_CIRCLE_RADIUS = 80 // 最大有效圆形半径
    private const val MIN_RECT_SIZE = 15 // 最小矩形尺寸
    private const val MAX_RECT_SIZE = 100 // 最大矩形尺寸
    private const val MIN_LINE_LENGTH = 20 // 最小线条长度
    private const val MAX_LINE_LENGTH = 200 // 最大线条长度
    
    // 特征检测阈值
    private const val MIN_SYMMETRY_SCORE = 0.6 // 最小对称性得分（用于验证指示器特征）
    private const val MIN_CONTRAST_RATIO = 0.3 // 最小对比度比例（指示器应该有明显的对比度）
    
    /**
     * 检测结果
     */
    data class LoadingIndicatorResult(
        /** 是否检测到 loading 指示器 */
        val hasLoadingIndicator: Boolean,
        /** 检测到的圆形数量 */
        val circleCount: Int,
        /** 圆形中心点列表 */
        val circles: List<Circle>,
        /** 检测到的其他形状数量 */
        val otherShapesCount: Int = 0,
        /** 置信度 [0,1] */
        val confidence: Float
    )
    
    /**
     * 圆形信息
     */
    data class Circle(
        val centerX: Int,
        val centerY: Int,
        val radius: Int
    )
    
    /**
     * 形状信息（用于非圆形指示器）
     */
    data class Shape(
        val centerX: Int,
        val centerY: Int,
        val width: Int,
        val height: Int,
        val type: ShapeType
    )
    
    enum class ShapeType {
        CIRCLE,
        RECTANGLE,
        LINE,
        OTHER
    }
    
    /**
     * 检测 loading 指示器（标准方法）
     * 只检测位于中间部位有明显特征的指示器，不限于圆形
     * @param bitmap 截图
     * @param minRadius 最小圆半径，默认 10
     * @param maxRadius 最大圆半径，默认 100
     * @param minCircles 最小圆形数量，默认 1
     * @return 检测结果
     */
    fun detect(
        bitmap: Bitmap,
        minRadius: Int = DEFAULT_MIN_RADIUS,
        maxRadius: Int = DEFAULT_MAX_RADIUS,
        minCircles: Int = DEFAULT_MIN_CIRCLES
    ): LoadingIndicatorResult {
        OpenCVInitializer.ensureInitialized()
        
        if (bitmap.isRecycled) {
            OmniLog.w(TAG, "Bitmap is recycled")
            return LoadingIndicatorResult(false, 0, emptyList(), 0, 0f)
        }
        
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        try {
            val imageWidth = mat.cols()
            val imageHeight = mat.rows()
            val centerX = imageWidth / 2
            val centerY = imageHeight / 2
            val maxDistanceFromCenter = minOf(imageWidth, imageHeight) * MAX_DISTANCE_FROM_CENTER_RATIO
            
            // 转换为灰度图
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            
            // 高斯模糊，减少噪声
            val blurred = Mat()
            Imgproc.GaussianBlur(gray, blurred, Size(9.0, 9.0), 2.0, 2.0)
            
            // 1. 检测圆形指示器
            val circleList = detectCircles(
                blurred, 
                minRadius, 
                maxRadius, 
                imageWidth, 
                imageHeight, 
                centerX, 
                centerY, 
                maxDistanceFromCenter
            )
            
            // 2. 检测其他形状的指示器（矩形、线条等）
            val otherShapes = detectOtherShapes(
                gray,
                blurred,
                imageWidth,
                imageHeight,
                centerX,
                centerY,
                maxDistanceFromCenter
            )
            
            val validCircleCount = circleList.size
            val otherShapesCount = otherShapes.size
            val totalShapes = validCircleCount + otherShapesCount
            val hasLoadingIndicator = totalShapes >= minCircles
            
            // 计算置信度（综合考虑圆形和其他形状）
            val confidence = if (totalShapes > 0) {
                // 圆形权重更高，其他形状也有贡献
                val circleConfidence = (validCircleCount.toFloat() / 3.0f).coerceIn(0f, 1f)
                val otherConfidence = (otherShapesCount.toFloat() / 2.0f).coerceIn(0f, 1f)
                (circleConfidence * 0.7f + otherConfidence * 0.3f).coerceIn(0f, 1f)
            } else {
                0f
            }
            
            // 释放资源
            gray.release()
            blurred.release()
            
            OmniLog.d(
                TAG, 
                "Loading indicator detection: circles=$validCircleCount, otherShapes=$otherShapesCount, " +
                "total=$totalShapes, hasIndicator=$hasLoadingIndicator, confidence=$confidence"
            )
            
            return LoadingIndicatorResult(
                hasLoadingIndicator = hasLoadingIndicator,
                circleCount = validCircleCount,
                circles = circleList,
                otherShapesCount = otherShapesCount,
                confidence = confidence
            )
        } catch (e: Exception) {
            OmniLog.e(TAG, "Loading indicator detection failed: ${e.message}", e)
            return LoadingIndicatorResult(false, 0, emptyList(), 0, 0f)
        } finally {
            mat.release()
        }
    }
    
    /**
     * 检测圆形指示器
     */
    private fun detectCircles(
        blurred: Mat,
        minRadius: Int,
        maxRadius: Int,
        imageWidth: Int,
        imageHeight: Int,
        screenCenterX: Int,
        screenCenterY: Int,
        maxDistanceFromCenter: Double
    ): List<Circle> {
        val circles = Mat()
        Imgproc.HoughCircles(
            blurred,
            circles,
            Imgproc.HOUGH_GRADIENT,
            1.0, // dp: 累加器分辨率与图像分辨率的反比
            50.0, // minDist: 检测到的圆心之间的最小距离
            100.0, // param1: Canny边缘检测的高阈值
            30.0, // param2: 累加器阈值
            minRadius, // minRadius: 最小圆半径
            maxRadius  // maxRadius: 最大圆半径
        )
        
        val circleCount = circles.rows()
        val circleList = mutableListOf<Circle>()
        
        // 解析并验证检测到的圆形
        for (i in 0 until circleCount) {
            val circle = circles.get(i, 0)
            val cx = circle[0].toInt()
            val cy = circle[1].toInt()
            val r = circle[2].toInt()
            
            // 验证圆形是否在中间部位且有明显特征
            if (isValidCenterIndicator(
                centerX = cx,
                centerY = cy,
                size = r * 2,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                screenCenterX = screenCenterX,
                screenCenterY = screenCenterY,
                maxDistanceFromCenter = maxDistanceFromCenter
            ) && hasDistinctiveFeatures(blurred, cx, cy, r)) {
                circleList.add(Circle(cx, cy, r))
            } else {
                OmniLog.d(TAG, "Filtered invalid circle: center=($cx, $cy), radius=$r")
            }
        }
        
        circles.release()
        return circleList
    }
    
    /**
     * 检测其他形状的指示器（矩形、线条等）
     */
    private fun detectOtherShapes(
        gray: Mat,
        blurred: Mat,
        imageWidth: Int,
        imageHeight: Int,
        screenCenterX: Int,
        screenCenterY: Int,
        maxDistanceFromCenter: Double
    ): List<Shape> {
        val shapes = mutableListOf<Shape>()
        
        // 使用Canny边缘检测
        val edges = Mat()
        Imgproc.Canny(blurred, edges, 50.0, 150.0)
        
        // 检测矩形（方形loading指示器）
        val rectangles = detectRectangles(
            edges,
            gray,
            imageWidth,
            imageHeight,
            screenCenterX,
            screenCenterY,
            maxDistanceFromCenter
        )
        shapes.addAll(rectangles)
        
        // 检测线条（进度条、旋转线条等）
        val lines = detectLines(
            edges,
            imageWidth,
            imageHeight,
            screenCenterX,
            screenCenterY,
            maxDistanceFromCenter
        )
        shapes.addAll(lines)
        
        edges.release()
        return shapes
    }
    
    /**
     * 检测矩形指示器
     */
    private fun detectRectangles(
        edges: Mat,
        gray: Mat,
        imageWidth: Int,
        imageHeight: Int,
        screenCenterX: Int,
        screenCenterY: Int,
        maxDistanceFromCenter: Double
    ): List<Shape> {
        val rectangles = mutableListOf<Shape>()
        
        // 查找轮廓
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        
        for (contour in contours) {
            // 近似轮廓为多边形
            val approx = MatOfPoint2f()
            val contour2f = MatOfPoint2f(*contour.toArray())
            val epsilon = 0.02 * Imgproc.arcLength(contour2f, true)
            Imgproc.approxPolyDP(contour2f, approx, epsilon, true)
            
            // 如果是四边形，可能是矩形
            if (approx.rows() == 4) {
                val rect = Imgproc.boundingRect(contour)
                val centerX = rect.x + rect.width / 2
                val centerY = rect.y + rect.height / 2
                val size = maxOf(rect.width, rect.height)
                
                // 验证是否在中间部位且有明显特征
                if (size >= MIN_RECT_SIZE && size <= MAX_RECT_SIZE &&
                    isValidCenterIndicator(
                        centerX, centerY, size,
                        imageWidth, imageHeight,
                        screenCenterX, screenCenterY,
                        maxDistanceFromCenter
                    ) &&
                    hasDistinctiveFeatures(gray, centerX, centerY, size / 2)
                ) {
                    rectangles.add(Shape(centerX, centerY, rect.width, rect.height, ShapeType.RECTANGLE))
                }
            }
            
            approx.release()
            contour2f.release()
        }
        
        hierarchy.release()
        return rectangles
    }
    
    /**
     * 检测线条指示器
     */
    private fun detectLines(
        edges: Mat,
        imageWidth: Int,
        imageHeight: Int,
        screenCenterX: Int,
        screenCenterY: Int,
        maxDistanceFromCenter: Double
    ): List<Shape> {
        val lines = mutableListOf<Shape>()
        
        // 使用霍夫线变换
        val linesMat = Mat()
        Imgproc.HoughLinesP(
            edges,
            linesMat,
            1.0, // rho: 距离精度
            Math.PI / 180.0, // theta: 角度精度
            50, // threshold: 累加器阈值
            30.0, // minLineLength: 最小线段长度
            10.0  // maxLineGap: 最大线段间隙
        )
        
        for (i in 0 until linesMat.rows()) {
            val line = linesMat.get(i, 0)
            val x1 = line[0].toInt()
            val y1 = line[1].toInt()
            val x2 = line[2].toInt()
            val y2 = line[3].toInt()
            
            val centerX = (x1 + x2) / 2
            val centerY = (y1 + y2) / 2
            val length = sqrt((x2 - x1).toDouble().pow(2) + (y2 - y1).toDouble().pow(2)).toInt()
            
            // 验证是否在中间部位
            if (length >= MIN_LINE_LENGTH && length <= MAX_LINE_LENGTH &&
                isValidCenterIndicator(
                    centerX, centerY, length,
                    imageWidth, imageHeight,
                    screenCenterX, screenCenterY,
                    maxDistanceFromCenter
                )
            ) {
                lines.add(Shape(centerX, centerY, abs(x2 - x1), abs(y2 - y1), ShapeType.LINE))
            }
        }
        
        linesMat.release()
        return lines
    }
    
    /**
     * 检测旋转的 loading 指示器（增强版）
     * 使用自适应阈值，更好地检测圆形边界
     * @param bitmap 截图
     * @param minRadius 最小圆半径，默认 10
     * @param maxRadius 最大圆半径，默认 100
     * @return 检测结果
     */
    fun detectRotating(
        bitmap: Bitmap,
        minRadius: Int = DEFAULT_MIN_RADIUS,
        maxRadius: Int = DEFAULT_MAX_RADIUS
    ): LoadingIndicatorResult {
        OpenCVInitializer.ensureInitialized()
        
        if (bitmap.isRecycled) {
            return LoadingIndicatorResult(false, 0, emptyList(), 0, 0f)
        }
        
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        try {
            val imageWidth = mat.cols()
            val imageHeight = mat.rows()
            val centerX = imageWidth / 2
            val centerY = imageHeight / 2
            val maxDistanceFromCenter = minOf(imageWidth, imageHeight) * MAX_DISTANCE_FROM_CENTER_RATIO
            
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            
            // 使用自适应阈值，更好地检测圆形边界
            val binary = Mat()
            Imgproc.adaptiveThreshold(
                gray, 
                binary, 
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV, 
                11, 
                2.0
            )
            
            // 形态学操作，连接断开的边缘
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
            val morphed = Mat()
            Imgproc.morphologyEx(binary, morphed, Imgproc.MORPH_CLOSE, kernel)
            
            // 检测圆形
            val circleList = detectCircles(
                morphed,
                minRadius,
                maxRadius,
                imageWidth,
                imageHeight,
                centerX,
                centerY,
                maxDistanceFromCenter
            )
            
            val validCircleCount = circleList.size
            val hasLoadingIndicator = validCircleCount >= DEFAULT_MIN_CIRCLES
            val confidence = (validCircleCount.toFloat() / 3.0f).coerceIn(0f, 1f)
            
            // 释放资源
            gray.release()
            binary.release()
            kernel.release()
            morphed.release()
            
            OmniLog.d(
                TAG,
                "Rotating loading indicator detection: validCircles=$validCircleCount, hasIndicator=$hasLoadingIndicator"
            )
            
            return LoadingIndicatorResult(
                hasLoadingIndicator = hasLoadingIndicator,
                circleCount = validCircleCount,
                circles = circleList,
                otherShapesCount = 0,
                confidence = confidence
            )
        } catch (e: Exception) {
            OmniLog.e(TAG, "Rotating loading indicator detection failed: ${e.message}", e)
            return LoadingIndicatorResult(false, 0, emptyList(), 0, 0f)
        } finally {
            mat.release()
        }
    }
    
    /**
     * 验证指示器是否位于中间部位
     * @param centerX 指示器中心 X 坐标
     * @param centerY 指示器中心 Y 坐标
     * @param size 指示器尺寸（直径、边长等）
     * @param imageWidth 图片宽度
     * @param imageHeight 图片高度
     * @param screenCenterX 屏幕中心 X 坐标
     * @param screenCenterY 屏幕中心 Y 坐标
     * @param maxDistanceFromCenter 距离屏幕中心的最大距离
     * @return true 如果指示器位于中间部位
     */
    private fun isValidCenterIndicator(
        centerX: Int,
        centerY: Int,
        size: Int,
        imageWidth: Int,
        imageHeight: Int,
        screenCenterX: Int,
        screenCenterY: Int,
        maxDistanceFromCenter: Double
    ): Boolean {
        // 1. 检查指示器是否完全在图片内
        val halfSize = size / 2
        if (centerX - halfSize < 0 || centerX + halfSize >= imageWidth ||
            centerY - halfSize < 0 || centerY + halfSize >= imageHeight) {
            return false
        }
        
        // 2. 检查指示器中心是否距离屏幕边缘足够远（loading 指示器通常不在边缘）
        if (centerX < MIN_DISTANCE_FROM_EDGE || 
            centerX > imageWidth - MIN_DISTANCE_FROM_EDGE ||
            centerY < MIN_DISTANCE_FROM_EDGE || 
            centerY > imageHeight - MIN_DISTANCE_FROM_EDGE) {
            return false
        }
        
        // 3. 检查指示器中心是否在屏幕中央区域（更严格的中间区域检测）
        val distanceFromCenter = sqrt(
            (centerX - screenCenterX).toDouble().pow(2) + 
            (centerY - screenCenterY).toDouble().pow(2)
        )
        if (distanceFromCenter > maxDistanceFromCenter) {
            return false
        }
        
        // 4. 检查指示器大小是否合理（相对于屏幕大小）
        val screenSize = minOf(imageWidth, imageHeight)
        val sizeRatio = size.toDouble() / screenSize
        // Loading 指示器的尺寸通常占屏幕较小比例（0.02-0.15）
        if (sizeRatio < 0.02 || sizeRatio > 0.15) {
            return false
        }
        
        return true
    }
    
    /**
     * 检测指示器是否有明显特征
     * 通过检测对比度、对称性等特征来判断是否是有效的loading指示器
     * @param mat 图像矩阵（灰度图或处理后的图像）
     * @param centerX 指示器中心 X 坐标
     * @param centerY 指示器中心 Y 坐标
     * @param radius 指示器半径（或等效半径）
     * @return true 如果有明显特征
     */
    private fun hasDistinctiveFeatures(
        mat: Mat,
        centerX: Int,
        centerY: Int,
        radius: Int
    ): Boolean {
        if (radius < 5) return false
        
        try {
            // 提取指示器区域
            val x1 = (centerX - radius).coerceAtLeast(0)
            val y1 = (centerY - radius).coerceAtLeast(0)
            val x2 = (centerX + radius).coerceAtMost(mat.cols() - 1)
            val y2 = (centerY + radius).coerceAtMost(mat.rows() - 1)
            
            if (x2 <= x1 || y2 <= y1) return false
            
            val roi = Mat(mat, Rect(x1, y1, x2 - x1, y2 - y1))
            
            // 1. 检测对比度（loading指示器通常有较高的对比度）
            val mean = MatOfDouble()
            val stddev = MatOfDouble()
            Core.meanStdDev(roi, mean, stddev)
            val contrast = stddev.get(0, 0)[0] / 255.0
            
            // 释放资源
            mean.release()
            stddev.release()
            
            if (contrast < MIN_CONTRAST_RATIO) {
                roi.release()
                return false
            }
            
            // 2. 检测对称性（圆形指示器应该具有较好的对称性）
            val symmetryScore = calculateSymmetry(roi)
            if (symmetryScore < MIN_SYMMETRY_SCORE) {
                roi.release()
                return false
            }
            
            roi.release()
            return true
        } catch (e: Exception) {
            OmniLog.w(TAG, "Error checking distinctive features: ${e.message}")
            return false
        }
    }
    
    /**
     * 计算图像的对称性得分
     * @param roi 感兴趣区域
     * @return 对称性得分 [0,1]
     */
    private fun calculateSymmetry(roi: Mat): Double {
        if (roi.cols() < 4 || roi.rows() < 4) return 0.0
        
        try {
            // 水平对称性
            val halfWidth = roi.cols() / 2
            val leftHalf = Mat(roi, Rect(0, 0, halfWidth, roi.rows()))
            val rightHalf = Mat(roi, Rect(roi.cols() - halfWidth, 0, halfWidth, roi.rows()))
            
            val flippedRight = Mat()
            Core.flip(rightHalf, flippedRight, 1) // 水平翻转
            
            val diff = Mat()
            Core.absdiff(leftHalf, flippedRight, diff)
            
            val meanDiff = Core.mean(diff)
            val horizontalSymmetry = 1.0 - (meanDiff.`val`[0] / 255.0)
            
            // 垂直对称性
            val halfHeight = roi.rows() / 2
            val topHalf = Mat(roi, Rect(0, 0, roi.cols(), halfHeight))
            val bottomHalf = Mat(roi, Rect(0, roi.rows() - halfHeight, roi.cols(), halfHeight))
            
            val flippedBottom = Mat()
            Core.flip(bottomHalf, flippedBottom, 0) // 垂直翻转
            
            val diff2 = Mat()
            Core.absdiff(topHalf, flippedBottom, diff2)
            
            val meanDiff2 = Core.mean(diff2)
            val verticalSymmetry = 1.0 - (meanDiff2.`val`[0] / 255.0)
            
            // 综合对称性得分
            val symmetry = (horizontalSymmetry + verticalSymmetry) / 2.0
            
            // 释放资源
            leftHalf.release()
            rightHalf.release()
            flippedRight.release()
            diff.release()
            topHalf.release()
            bottomHalf.release()
            flippedBottom.release()
            diff2.release()
            
            return symmetry
        } catch (e: Exception) {
            OmniLog.w(TAG, "Error calculating symmetry: ${e.message}")
            return 0.0
        }
    }
}

