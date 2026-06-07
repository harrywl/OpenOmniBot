package cn.com.omnimind.assists.api.eventapi

interface ScreenshotImageEventApi {
    /**
     * 截屏后显示浮层
     */
    fun onScreenShotShowOverlay()

    /**
     * 截屏前隐藏浮层
     */
    fun onScreenShotHideOverlay()
}