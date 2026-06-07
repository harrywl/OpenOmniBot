package cn.com.omnimind.assists.controller.accessibility

/**
 * 输入文本时没有找到聚焦节点
 * 用于触发粘贴方式输入的降级流程
 */
class NoFocusedNodeException(
    message: String = "No focused node found on the screen."
) : Exception(message)
