package cn.com.omnimind.omniintelligence.models

enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }
enum class TaskType { CHAT, COMPANION, EXECUTION }
data class Application(
    val packageName: String
)

data class RequestHeader(
    val version: String? = "1.0",
    val requestId: String,
    val appId: String?,
    val taskId: String,
    val timestamp: Long? = System.currentTimeMillis(),
)

data class ResponseHeader(
    val requestId: String,
    val timestamp: Long? = System.currentTimeMillis(),
    val costTime: Long?,
    val code: Int,
    val message: String?
)

data class ChatMessageChunk(
    val text: String,
)
