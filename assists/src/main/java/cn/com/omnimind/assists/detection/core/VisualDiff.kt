package cn.com.omnimind.assists.detection.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import cn.com.omnimind.accessibility.service.AssistsService

/**
 * 计算两张截图的视觉差异（灰度 + 缩放 + MAD算法）
 * 可选择是否裁剪掉状态栏区域
 *
 * @param before 之前的截图
 * @param after 之后的截图
 * @param downSize 缩放后的尺寸，默认64x64
 * @param cropStatusBar 是否裁剪状态栏，默认true（向后兼容）
 * @return 差异值 0..1（0表示完全相同，1表示完全不同）
 */
fun visualDiff(
    before: Bitmap,
    after: Bitmap,
    downSize: Int = 64,
    cropStatusBar: Boolean = true
): Float {
    val b1: Bitmap
    val b2: Bitmap

    if (cropStatusBar) {
        // 获取 AssistsService 实例
        val service = AssistsService.instance ?: return 0f

        // 获取状态栏高度
        val statusBarHeight = getStatusBarHeight(service)

        // 裁剪掉状态栏（从状态栏下方开始）
        val cropTop = statusBarHeight
        val cropHeight = before.height - cropTop

        if (cropHeight <= 0 || cropTop >= before.height) {
            // 如果裁剪后没有内容，返回无差异
            return 0f
        }

        b1 = Bitmap.createBitmap(before, 0, cropTop, before.width, cropHeight)
        b2 = Bitmap.createBitmap(after, 0, cropTop, after.width, cropHeight)
    } else {
        // 不裁剪状态栏，直接使用原图
        b1 = before
        b2 = after
    }

    // 缩放到指定尺寸，降低噪声和计算量
    val s1 = Bitmap.createScaledBitmap(b1, downSize, downSize, true)
    val s2 = Bitmap.createScaledBitmap(b2, downSize, downSize, true)

    var sum = 0L
    val n = downSize * downSize

    // 逐像素计算灰度差异
    for (y in 0 until downSize) {
        for (x in 0 until downSize) {
            val p1 = s1.getPixel(x, y)
            val p2 = s2.getPixel(x, y)

            // 灰度计算（整数近似：0.3R + 0.59G + 0.11B）
            val r1 = (p1 shr 16) and 0xff
            val g1_color = (p1 shr 8) and 0xff
            val b1_color = p1 and 0xff
            val g1 = (r1 * 30 + g1_color * 59 + b1_color * 11) / 100

            val r2 = (p2 shr 16) and 0xff
            val g2_color = (p2 shr 8) and 0xff
            val b2_color = p2 and 0xff
            val g2 = (r2 * 30 + g2_color * 59 + b2_color * 11) / 100

            sum += kotlin.math.abs(g1 - g2).toLong()
        }
    }

    // 清理临时bitmap
    if (cropStatusBar) {
        // 只有在裁剪了状态栏时才需要回收（因为创建了新的bitmap）
        b1.recycle()
        b2.recycle()
    }
    s1.recycle()
    s2.recycle()

    // 计算MAD（Mean Absolute Difference）
    val mad = sum.toFloat() / n.toFloat()          // 0..255
    val diff = mad / 255f                          // 归一化到 0..1

    return diff
}

/**
 * 比对指定点周围圆形区域的视觉差异
 * 用于检测某个点击位置前后的变化
 *
 * @param beforeBitmap 之前的截图
 * @param afterBitmap 之后的截图
 * @param centerX 中心点X坐标
 * @param centerY 中心点Y坐标
 * @param radius 圆形半径，默认15像素
 * @return 差异值 0..1（0表示完全相同，1表示完全不同）
 */
fun compareRegionDiff(
    beforeBitmap: Bitmap,
    afterBitmap: Bitmap,
    centerX: Int,
    centerY: Int,
    radius: Int = 15
): Double {
    try {
        val regionSize = radius * 2
        val halfSize = radius

        // 计算区域边界，确保不超出图像范围
        val left = (centerX - halfSize).coerceAtLeast(0)
        val top = (centerY - halfSize).coerceAtLeast(0)
        val right = (centerX + halfSize).coerceAtMost(beforeBitmap.width)
        val bottom = (centerY + halfSize).coerceAtMost(beforeBitmap.height)

        // 如果区域太小，返回 0（没有差异）
        if (right - left < 10 || bottom - top < 10) {
            return 0.0
        }

        // 裁剪点击区域（矩形）
        val beforeRect = Bitmap.createBitmap(beforeBitmap, left, top, right - left, bottom - top)
        val afterRect = Bitmap.createBitmap(afterBitmap, left, top, right - left, bottom - top)

        // 应用圆形遮罩，只保留圆形区域内的像素
        val beforeRegion = applyCircleMask(beforeRect, halfSize.toFloat())
        val afterRegion = applyCircleMask(afterRect, halfSize.toFloat())

        // 使用 visualDiff 方法计算差异
        // cropStatusBar = false，因为已经裁剪好了小块区域
        val diffRatio = visualDiff(beforeRegion, afterRegion, downSize = 16, cropStatusBar = false).toDouble()

        // 清理资源
        beforeRect.recycle()
        afterRect.recycle()
        beforeRegion.recycle()
        afterRegion.recycle()

        return diffRatio
    } catch (e: Exception) {
        return 0.0
    }
}

/**
 * 应用圆形遮罩到 Bitmap
 * 只保留圆形区域内的像素，圆形外的像素设置为透明
 *
 * @param bitmap 原始 bitmap
 * @param radius 圆形半径
 * @return 应用圆形遮罩后的 bitmap
 */
private fun applyCircleMask(bitmap: Bitmap, radius: Float): Bitmap {
    val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = 0xFFFFFFFF.toInt()

    // 绘制圆形
    val centerX = bitmap.width / 2f
    val centerY = bitmap.height / 2f
    canvas.drawCircle(centerX, centerY, radius, paint)

    // 使用 SRC_IN 模式，只保留圆形区域内的像素
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(bitmap, 0f, 0f, paint)

    return output
}

/**
 * 获取状态栏高度
 */
private fun getStatusBarHeight(service: AssistsService): Int {
    val resourceId = service.resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (resourceId > 0) {
        service.resources.getDimensionPixelSize(resourceId)
    } else {
        0
    }
}
