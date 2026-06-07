package cn.com.omnimind.accessibility.util

import BaseApplication
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

object ScreenStateUtil {
    /**
     * 检测设备是否处于可操作状态
     * 可操作状态：屏幕亮屏且已解锁（用户可以在桌面或应用内操作）
     * 非可操作状态：熄屏、锁屏、屏保
     * @param context 上下文
     * @return true 表示可操作状态，false 表示非可操作状态
     */
    fun isOperable(): Boolean {
        val keyguardManager =
            BaseApplication.instance.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val powerManager =  BaseApplication.instance.getSystemService(Context.POWER_SERVICE) as PowerManager

        // 1. 检查屏幕是否亮屏且可交互
        val isInteractive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            powerManager.isInteractive
        } else {
            @Suppress("DEPRECATION")
            powerManager.isScreenOn
        }

        if (!isInteractive) {
            // 屏幕关闭（熄屏/屏保），不可操作
            return false
        }

        // 2. 检查设备是否已解锁
        val isUnlocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            !keyguardManager.isDeviceLocked
        } else {
            @Suppress("DEPRECATION")
            !keyguardManager.isKeyguardLocked
        }

        if (!isUnlocked) {
            // 未解锁，不可操作
            return false
        }


        // 只有屏幕亮屏、已解锁且不在屏保状态时，才是可操作状态
        return true
    }


}