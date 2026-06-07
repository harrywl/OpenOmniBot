package cn.com.omnimind.uikit.api.callback

import android.view.View

/**
 *  创建flutterView监听 下放到宿主Activity
 */
interface HalfScreenApi {
    fun onCreateFlutter(path: String): View;//主要用来处理Task相关的自动卡片
    fun onCreateLearnFlutter(path: String): View;//主要用来处理Task相关的自动卡片
    fun onDestroyOrGone()
    fun onNeedOpenAppMainParam(path: String?,needClear: Boolean)
}
