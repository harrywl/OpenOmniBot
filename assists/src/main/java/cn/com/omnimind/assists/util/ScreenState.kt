package cn.com.omnimind.assists.util

import cn.com.omnimind.accessibility.action.ScreenStateListener
import cn.com.omnimind.baselib.util.OmniLog

class ScreenState(val pauseAllDoing: () -> Unit?, val resumeDoingUI: () -> Unit) :
    ScreenStateListener {


    override fun onLocked() {
        pauseAllDoing()
    }

    override fun onUnlocked() {
        // 设备解锁时，显示UI
        resumeDoingUI()
    }

    override fun onOperableStateChanged(isOperable: Boolean) {
        // 可操作状态变化时的处理
        if (isOperable) {
            resumeDoingUI()
        } else {
            pauseAllDoing()
        }
    }


}