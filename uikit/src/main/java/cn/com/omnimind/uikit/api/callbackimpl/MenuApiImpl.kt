package cn.com.omnimind.uikit.api.callbackimpl

import cn.com.omnimind.uikit.api.callback.MenuApi

class MenuApiImpl : MenuApi {
    override fun onOpenHomeParams(path: String?, needClear: Boolean) {
        // 悬浮窗菜单的回主页能力已移除。
    }
}
