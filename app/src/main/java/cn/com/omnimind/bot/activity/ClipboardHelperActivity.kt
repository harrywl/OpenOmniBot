package cn.com.omnimind.bot.activity

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import cn.com.omnimind.assists.task.vlmserver.AndroidDeviceOperator
import cn.com.omnimind.baselib.util.OmniLog

/**
 * 透明Activity，用于在前台获取焦点以访问剪贴板
 * Android 10+ 限制后台应用访问剪贴板，需要通过前台Activity来执行剪贴板操作
 */
class ClipboardHelperActivity : Activity() {

    companion object {
        private const val TAG = "ClipboardHelperActivity"
        private const val EXTRA_TEXT = "clipboard_text"
        private const val EXTRA_OPERATION = "clipboard_operation"
        const val OPERATION_COPY = "copy"
        const val OPERATION_GET = "get"
    }

    private var textToCopy: String? = null
    private var operation: String? = null
    private var hasProcessed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        operation = intent.getStringExtra(EXTRA_OPERATION) ?: OPERATION_COPY
        textToCopy = intent.getStringExtra(EXTRA_TEXT)

        // GET 操作不需要检查 textToCopy
        if (operation == OPERATION_COPY && textToCopy.isNullOrEmpty()) {
            notifyResult(false, null)
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || hasProcessed) return
        hasProcessed = true

        when (operation) {
            OPERATION_COPY -> {
                val text = textToCopy
                if (text.isNullOrEmpty()) {
                    notifyResult(false, null)
                    finish()
                    return
                }
                val success = performClipboardCopy(text)
                OmniLog.d(TAG, "Clipboard copy result: $success")
                notifyResult(success, null)
                finish()
            }
            OPERATION_GET -> {
                val clipboardText = performClipboardGet()
                OmniLog.d(TAG, "Clipboard get result: ${clipboardText != null}")
                notifyResult(clipboardText != null, clipboardText)
                finish()
            }
            else -> {
                notifyResult(false, null)
                finish()
            }
        }
    }

    private fun notifyResult(success: Boolean, text: String?) {
        try {
            val callbackTarget = intent.getStringExtra("callback_target")
            if (operation == OPERATION_GET) {
                AndroidDeviceOperator.notifyClipboardGetResult(text)
            } else {
                // 复制操作也根据 callback_target 决定调用哪个回调
                if (callbackTarget == "AccessibilityController") {
                    cn.com.omnimind.assists.controller.accessibility.AccessibilityController.notifyClipboardCopyResult(success)
                } else {
                    AndroidDeviceOperator.notifyClipboardResult(success)
                }
            }
        } catch (e: Exception) {
            OmniLog.e(TAG, "notifyResult failed: ${e.message}")
        }
    }

    private fun performClipboardCopy(text: String): Boolean {
        return try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("omni_clipboard", text))
            clipboard.hasPrimaryClip()
        } catch (e: Exception) {
            OmniLog.e(TAG, "performClipboardCopy failed: ${e.message}")
            false
        }
    }

    private fun performClipboardGet(): String? {
        return try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        } catch (e: Exception) {
            OmniLog.e(TAG, "performClipboardGet failed: ${e.message}")
            null
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
