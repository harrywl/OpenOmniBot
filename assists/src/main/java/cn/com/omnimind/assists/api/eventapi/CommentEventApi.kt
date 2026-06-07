package cn.com.omnimind.assists.api.eventapi

interface CommentEventApi {
    /**
     * 屏幕锁屏
     */
    fun onScreenLock()

    /**
     * 屏幕解锁
     */
    fun onScreenUnLock(bool: Boolean)
}