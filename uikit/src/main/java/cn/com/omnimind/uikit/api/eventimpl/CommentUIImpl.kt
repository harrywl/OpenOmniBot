package cn.com.omnimind.uikit.api.eventimpl

import cn.com.omnimind.assists.api.eventapi.CommentEventApi
import cn.com.omnimind.uikit.api.uievent.UIBaseEvent
import cn.com.omnimind.uikit.api.uievent.UIChatEvent
import cn.com.omnimind.uikit.api.uievent.UITaskEvent

class CommentUIImpl(
    val uiChatEvent: UIChatEvent, val uiBaseEvent: UIBaseEvent, val uiTaskEvent: UITaskEvent
) : CommentEventApi {
    override fun onScreenLock() {
        uiBaseEvent.goneCatInMain()
        uiBaseEvent.goneLockMaskInMain()
        uiChatEvent.dismissHalfScreenInMain()
    }

    override fun onScreenUnLock(isCompanionRunning: Boolean) {
        if (isCompanionRunning) {
            uiBaseEvent.visibleCatInMain()
        }
        uiBaseEvent.goneLockMaskInMain()
    }
}