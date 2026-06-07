package cn.com.omnimind.uikit.api.eventimpl

import cn.com.omnimind.assists.api.eventapi.ScreenshotImageEventApi
import cn.com.omnimind.uikit.api.uievent.UIBaseEvent

class ScreenshotImageUIImpl(val uiBaseEvent: UIBaseEvent): ScreenshotImageEventApi {
    override fun onScreenShotShowOverlay() {
        uiBaseEvent.visibleCatInMain()
    }

    override fun onScreenShotHideOverlay() {
        uiBaseEvent.goneCatInMain()
    }
}