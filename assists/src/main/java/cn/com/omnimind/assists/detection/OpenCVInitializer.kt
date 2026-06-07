package cn.com.omnimind.assists.detection

import cn.com.omnimind.baselib.util.OmniLog
import org.opencv.android.OpenCVLoader

/**
 * OpenCV 初始化器
 * 提供统一的 OpenCV 初始化入口，采用懒加载方式
 */
object OpenCVInitializer {
    private const val TAG = "[OpenCVInitializer]"
    private var initialized = false

    /**
     * 确保 OpenCV 已初始化
     * 采用懒加载方式，第一次使用时才初始化
     * 多次调用是安全的（幂等性）
     */
    @Synchronized
    fun ensureInitialized() {
        if (initialized) return

        if (OpenCVLoader.initDebug()) {
            OmniLog.i(TAG, "OpenCV loaded successfully")
            initialized = true
        } else {
            OmniLog.e(TAG, "OpenCV initialization failed")
            throw RuntimeException("OpenCV initialization failed")
        }
    }
}
