package cn.com.omnimind.omniintelligence.models

import cn.com.omnimind.baselib.util.ImageQuality
import kotlinx.coroutines.flow.Flow

sealed class AgentRequest {
    data class ClickCoordinate(val header: RequestHeader, val payload: Payload.ClickCoordinatePayload) : AgentRequest()
    data class LongClickCoordinate(val header: RequestHeader, val payload: Payload.LongClickCoordinatePayload) : AgentRequest()
    data class InputText(val header: RequestHeader, val payload: Payload.InputTextPayload) : AgentRequest()
    data class CopyText(val header: RequestHeader, val payload: Payload.CopyTextPayload) : AgentRequest()
    data class CopyToClipboard(val header: RequestHeader, val payload: Payload.CopyToClipboardPayload) : AgentRequest()
    data class PasteText(val header: RequestHeader, val payload: Payload.PasteTextPayload) : AgentRequest()
    data class ScrollCoordinate(val header: RequestHeader, val payload: Payload.ScrollCoordinatePayload) : AgentRequest()
    data class LaunchApplication(val header: RequestHeader, val payload: Payload.LaunchApplicationPayload) : AgentRequest()
    data class ListInstalledApplications(val header: RequestHeader) : AgentRequest()
    data class CaptureScreenshotImage(val header: RequestHeader, val payload: Payload.CaptureScreenshotImagePayload) : AgentRequest()
    data class CaptureScreenshotBitmap(val header: RequestHeader, val payload: Payload.CaptureScreenshotBitmapPayload) : AgentRequest()
    data class CaptureScreenshotXml(val header: RequestHeader) : AgentRequest()
    data class CaptureScreenActivity(val header: RequestHeader) : AgentRequest()
    data class GoHome(val header: RequestHeader) : AgentRequest()
    data class GoBack(val header: RequestHeader) : AgentRequest()
    data class FinishTask(val header: RequestHeader, val payload: Payload.FinishTaskPayload) : AgentRequest()
    data class UpdateTaskType(val header: RequestHeader, val payload: Payload.UpdateTaskTypePayload) : AgentRequest()
    data class GetCurrentPackageName(val header: RequestHeader) : AgentRequest()
    data class WaitCurrentPageStabilize(val header: RequestHeader) : AgentRequest()
    data class WaitLoading(val header: RequestHeader) : AgentRequest()
    data class CheckLoginPage(val header: RequestHeader) : AgentRequest()
    data class SendVLMChat(val header: RequestHeader, val payload: Payload.VLMChatPayload) : AgentRequest()
    data class SendLLMChat(val header: RequestHeader, val payload: Payload.LLMChatPayload) : AgentRequest()
    data class SendStreamingLLMChat(val header: RequestHeader, val payload: Payload.LLMChatPayload) : AgentRequest()
    data class RequestVLMExecution(val header: RequestHeader, val payload: Payload.VLMExecutionPayload) : AgentRequest()
    data class SendVLMChatWithCapture(val header: RequestHeader, val payload: Payload.VLMChatWithCapturePayload) : AgentRequest()
    data class IsInDesktop(val header: RequestHeader) : AgentRequest()
    data class ShowChatBotWithSummary(val header: RequestHeader, val payload: Payload.ShowChatBotWithSummaryPayload) : AgentRequest()
    data class ShowChatBotWithStreamingSummary(val header: RequestHeader, val payload: Payload.ShowChatBotWithStreamingSummaryPayload) : AgentRequest()
    data class UserTakeover(val header: RequestHeader, val payload: Payload.UserTakeoverPayload) : AgentRequest()
    data class DetectBlockingAd(val header: RequestHeader) : AgentRequest()
    data class HandleAdInterception(
        val header: RequestHeader,
        val payload: Payload.HandleAdInterceptionPayload? = null
    ) : AgentRequest()
    data class ShowInfo(val header: RequestHeader, val payload: Payload.ShowInfoPayload) : AgentRequest()
    data class CompareRegionDiff(val header: RequestHeader, val payload: Payload.CompareRegionDiffPayload) : AgentRequest()
    data class OcrMultiClick(val header: RequestHeader, val payload: Payload.OcrMultiClickPayload) : AgentRequest()
    data class Foreach(val header: RequestHeader, val payload: Payload.ForeachPayload) : AgentRequest()
    data class HideKeyboard(val header: RequestHeader) : AgentRequest()
    data class RestoreKeyboard(val header: RequestHeader) : AgentRequest()

    object Payload {
        data class ClickCoordinatePayload(val x: Float, val y: Float)
        data class LongClickCoordinatePayload(val x: Float, val y: Float)
        data class InputTextPayload(
            val text: String,
            val x: Float? = null,
            val y: Float? = null,
            val inputMode: InputMode? = null
        )
        data class CopyTextPayload(val text: String, val key: String)
        data class CopyToClipboardPayload(val content: String)
        data class PasteTextPayload(val key: String)

        data class ScrollCoordinatePayload(
            val x: Float,
            val y: Float,
            val direction: ScrollDirection,
            val distance: Float
        )

        data class LaunchApplicationPayload(val packageName: String)
        data class CaptureScreenshotImagePayload(
            val isFilterOverlay: Boolean,
            val isCheckMostlySingleColor: Boolean,
            val compressQuality: ImageQuality? = null
        )
        data class CaptureScreenshotBitmapPayload(
            val isFilterOverlay: Boolean,
            val isCheckMostlySingleColor: Boolean,
            val compressQuality: ImageQuality? = null
        )
        data class UpdateTaskTypePayload(val taskType: TaskType)
        data class UpdateTakeoverStatePayload(val allowed: Boolean)
        data class PushStreamingChatMessageCardPayload(
            val messageId: String,
            val contentStream: Flow<ChatMessageChunk>
        )

        data class PushAppOptionsCardPayload(
            val title: String,
            val content: String,
            val applications: List<Application>,
        )

        data class PushTaskStepsCardPayload(
            val title: String,
            val content: String,
        )

        data class PushTaskOptionsCardPayload(
            val title: String,
            val content: String,
        )

        data class PushCompanionRecommendationCardPayload(
            val title: String,
            val content: String,
        )

        data class PushComparisonCardPayload(
            val title: String,
            val content: String,
        )

        data class PushCompanionResultCardPayload(
            val title: String,
            val content: String,
        )

        data class VLMChatPayload(val model: String, val images: List<String>, val text: String)
        data class VLMChatWithCapturePayload(val model: String, val text: String)
        data class LLMChatPayload(val model: String, val text: String)
        data class CompareRegionDiffPayload(
            val beforeBitmap: android.graphics.Bitmap,
            val afterBitmap: android.graphics.Bitmap,
            val centerX: Int,
            val centerY: Int,
            val radius: Int = 15
        )
        data class VLMExecutionPayload(
            val prompt: String,
            val model: String? = null,
            val maxSteps: Int? = null
        )
        data class FinishTaskPayload(val state: TaskState, val message: String)
        data class ShowChatBotWithSummaryPayload(val summaryText: String)
        data class ShowChatBotWithStreamingSummaryPayload(
            val model: String,
            val summaryPrompt: String,
            val recordedData: List<String>
        )
        data class ShowInfoPayload(val message: String)
        data class UserTakeoverPayload(val message: String, val e: Exception)
        data class OcrMultiClickPayload(
            val prompts: List<String>,
            val type: String? = null
        )
        data class HandleAdInterceptionPayload(val enabledAdTypes: List<String>? = null)
        data class ForeachPayload(val variableCount: Int = 0)
    }
}
