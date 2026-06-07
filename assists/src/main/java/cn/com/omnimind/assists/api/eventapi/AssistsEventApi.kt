package cn.com.omnimind.assists.api.eventapi

import cn.com.omnimind.assists.api.eventapi.ExecutionTaskEventApi

interface AssistsEventApi {
    /**
     * 获取陪伴模式相关的实现接口
     */
    fun getCompanionEventImpl(): CompanionTaskEventApi?

    /**
     * 获取执行模式相关的实现接口
     */

    fun getExecutionEventImpl(): ExecutionTaskEventApi?

    /**
     * 获取公共相关内容的实现接口
     */
    fun getCommentEventImpl(): CommentEventApi?

    /**
     * 获取截图相关内容接口
     */
    fun getScreenshotImageEventImpl(): ScreenshotImageEventApi?


}
