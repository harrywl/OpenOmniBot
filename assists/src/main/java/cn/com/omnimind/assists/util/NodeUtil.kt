package cn.com.omnimind.assists.util

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import cn.com.omnimind.baselib.util.OmniLog

object NodeUtil {
    private const val TAG = "[NodeUtil]"

    // 处理文本选择逻辑
     fun handleTextSelection(event: AccessibilityEvent): String {
        // 获取事件来源的应用包名
        val packageName = event.packageName?.toString() ?: "未知应用"

        // 获取选中文字所在的视图节点

        val nodeInfo = event.source ?: return  ""
        OmniLog.e("NodeUtil", "handleTextSelection: $packageName")
        OmniLog.e("NodeUtil", "handleTextSelection: ${nodeInfo.toString()}")

        // 获取选中的文本内容
        val selectedText = getSelectedText(nodeInfo)
        nodeInfo.recycle()
        return selectedText;

    }

    // 从视图节点中提取选中的文字
    private fun getSelectedText(nodeInfo: AccessibilityNodeInfo): String {
        // 获取选中文本的起始和结束位置
        val selectionStart = nodeInfo.textSelectionStart
        val selectionEnd = nodeInfo.textSelectionEnd

        // 无效的选择范围（未选中或选择位置异常）
        if (selectionStart < 0 || selectionEnd <= selectionStart) {
            return ""
        }

        // 获取完整文本内容
        val fullText = nodeInfo.text?.toString() ?: return ""

        // 截取选中的部分
        return try {
            fullText.substring(selectionStart, selectionEnd)
        } catch (e: IndexOutOfBoundsException) {
            ""
        }
    }

}