package cn.com.omnimind.assists.task.execution.agent

import cn.com.omnimind.omniintelligence.models.ResponseHeader

/**
 * 仅保留通用响应头工具，供 VLM 执行实现复用。
 */
object Agent {
    fun getSuccessHeader(reqId: String, startTime: Long): ResponseHeader {
        val timeStamp = System.currentTimeMillis()
        return ResponseHeader(reqId, timeStamp, timeStamp - startTime, 200, "Success")
    }

    fun getErrorHeader(reqId: String, startTime: Long, message: String): ResponseHeader {
        val timeStamp = System.currentTimeMillis()
        return ResponseHeader(reqId, timeStamp, timeStamp - startTime, 500, message)
    }
}
