package cn.com.omnimind.omniintelligence.models

import kotlinx.coroutines.flow.Flow

sealed class HostResponse {
    data class ClickCoordinateResponse(val header: ResponseHeader) : HostResponse()
    data class LongClickCoordinateResponse(val header: ResponseHeader) : HostResponse()
    data class InputTextResponse(val header: ResponseHeader) : HostResponse()
    data class CopyTextResponse(val header: ResponseHeader) : HostResponse()
    data class CopyToClipboardResponse(val header: ResponseHeader) : HostResponse()
    data class PasteTextResponse(val header: ResponseHeader) : HostResponse()
    data class ScrollCoordinateResponse(val header: ResponseHeader) : HostResponse()
    data class LaunchApplicationResponse(val header: ResponseHeader) : HostResponse()
    data class ListInstalledApplicationsResponse(
        val header: ResponseHeader,
        val payload: Payload.ListInstalledApplicationsPayload
    ) : HostResponse()
    data class CaptureScreenshotImageResponse(
        val header: ResponseHeader,
        val payload: Payload.CaptureScreenshotImagePayload
    ) : HostResponse()
    data class CaptureScreenshotBitmapResponse(
        val header: ResponseHeader,
        val payload: Payload.CaptureScreenshotBitmapPayload
    ) : HostResponse()
    data class CaptureScreenshotXmlResponse(
        val header: ResponseHeader,
        val payload: Payload.CaptureScreenshotXmlPayload
    ) : HostResponse()
    data class CaptureScreenActivityResponse(
        val header: ResponseHeader,
        val payload: Payload.CaptureScreenActivityPayload
    ) : HostResponse()
    data class GoHomeResponse(val header: ResponseHeader) : HostResponse()
    data class GoBackResponse(val header: ResponseHeader) : HostResponse()
    data class FinishTaskResponse(val header: ResponseHeader) : HostResponse()
    data class UpdateTaskTypeResponse(val header: ResponseHeader) : HostResponse()
    data class UpdateUserTakeoverStateResponse(val header: ResponseHeader) : HostResponse()
    data class PushStreamingChatMessageCard(val header: ResponseHeader) : HostResponse()
    data class PushAppOptionsCardResponse(
        val header: ResponseHeader,
        val payload: Payload.PushAppOptionsCardResponse
    ) : HostResponse()
    data class PushTaskStepsCardResponse(
        val header: ResponseHeader,
        val payload: Payload.PushTaskStepsCardPayload
    ) : HostResponse()
    data class PushTaskOptionsCardResponse(
        val header: ResponseHeader,
        val payload: Payload.PushTaskOptionsCardPayload
    ) : HostResponse()
    data class PushCompanionRecommendationCardResponse(
        val header: ResponseHeader,
        val payload: Payload.PushCompanionRecommendationCardPayload
    ) : HostResponse()
    data class PushComparisonCardResponse(val header: ResponseHeader) : HostResponse()
    data class PushCompanionResultCardResponse(
        val header: ResponseHeader,
        val payload: Payload.PushCompanionResultCardPayload
    ) : HostResponse()
    data class GetCurrentPackageNameResponse(
        val header: ResponseHeader,
        val payload: Payload.GetCurrentPackageNamePayload
    ) : HostResponse()
    data class WaitCurrentPageStabilizeResponse(val header: ResponseHeader) : HostResponse()
    data class WaitLoadingResponse(val header: ResponseHeader) : HostResponse()
    data class CheckLoginPageResponse(
        val header: ResponseHeader,
        val payload: Payload.CheckLoginPagePayload
    ) : HostResponse()
    data class SendLLMChatResponse(
        val header: ResponseHeader,
        val payload: Payload.SendLLMChatResponsePayload
    ) : HostResponse()
    data class SendVLMChatResponse(
        val header: ResponseHeader,
        val payload: Payload.SendVLMChatResponsePayload
    ) : HostResponse()
    data class SendVLMChatWithCaptureResponse(
        val header: ResponseHeader,
        val payload: Payload.SendVLMChatWithCaptureResponsePayload
    ) : HostResponse()
    data class SendStreamingLLMChatResponse(
        val header: ResponseHeader,
        val payload: Payload.SendStreamingLLMChatResponsePayload
    ) : HostResponse()
    data class RequestVLMExecutionResponse(
        val header: ResponseHeader,
        val payload: Payload.VLMExecutionResponsePayload
    ) : HostResponse()
    data class IsInDesktopResponse(
        val header: ResponseHeader,
        val payload: Payload.IsInDesktopResponsePayload
    ) : HostResponse()
    data class ShowChatBotWithSummaryResponse(val header: ResponseHeader) : HostResponse()
    data class ShowChatBotWithStreamingSummaryResponse(val header: ResponseHeader) : HostResponse()
    data class UserTakeoverResponse(
        val header: ResponseHeader,
        val payload: Payload.UserTakeoverPayload
    ) : HostResponse()
    data class DetectBlockingAdResponse(
        val header: ResponseHeader,
        val payload: Payload.DetectBlockingAdPayload
    ) : HostResponse()
    data class HandleAdInterceptionResponse(
        val header: ResponseHeader,
        val payload: Payload.HandleAdInterceptionPayload
    ) : HostResponse()
    data class CompareRegionDiffResponse(
        val header: ResponseHeader,
        val payload: Payload.CompareRegionDiffPayload
    ) : HostResponse()
    data class ShowInfoResponse(val header: ResponseHeader) : HostResponse()
    data class OcrMultiClickResponse(val header: ResponseHeader) : HostResponse()
    data class ForeachResponse(val header: ResponseHeader) : HostResponse()
    data class HideKeyboardResponse(val header: ResponseHeader) : HostResponse()
    data class RestoreKeyboardResponse(val header: ResponseHeader) : HostResponse()

    object Payload {
        data class CaptureScreenshotBitmapPayload(
            val bitmap: android.graphics.Bitmap?,
            val originalWidth: Int = 0,
            val originalHeight: Int = 0,
            val compressedWidth: Int = 0,
            val compressedHeight: Int = 0,
            val appliedScale: Float = 1f
        ) {
            fun scaleCoordinatesToOriginal(coords: Pair<Float, Float>): Pair<Float, Float> {
                return cn.com.omnimind.baselib.util.ImageCompressor.scaleCoordinatesToOriginal(coords, appliedScale)
            }
        }

        data class CaptureScreenshotImagePayload(
            val imageBase64: String?,
            val isSingleColor: Boolean,
            val originalWidth: Int = 0,
            val originalHeight: Int = 0,
            val compressedWidth: Int = 0,
            val compressedHeight: Int = 0,
            val appliedScale: Float = 1f
        ) {
            fun scaleCoordinatesToOriginal(coords: Pair<Float, Float>): Pair<Float, Float> {
                return cn.com.omnimind.baselib.util.ImageCompressor.scaleCoordinatesToOriginal(coords, appliedScale)
            }
        }

        data class CaptureScreenshotXmlPayload(val xml: String?)
        data class CaptureScreenActivityPayload(val activity: String?)
        data class ListInstalledApplicationsPayload(
            val packageNames: List<String>,
            val applicationNames: List<String>
        )
        data class GetCurrentPackageNamePayload(val packageName: String?)

        data class PushAppOptionsCardResponse(
            val selectedApplications: List<Application>
        )
        data class PushTaskStepsCardPayload(val selection: String)
        data class PushTaskOptionsCardPayload(val selection: String)
        data class PushCompanionRecommendationCardPayload(val selection: String)
        data class PushCompanionResultCardPayload(val selection: String)
        data class SendLLMChatResponsePayload(val message: String)
        data class SendVLMChatResponsePayload(val message: String)
        data class SendVLMChatWithCaptureResponsePayload(val message: String)
        data class SendStreamingLLMChatResponsePayload(
            val contentStream: Flow<ChatMessageChunk>
        )
        data class VLMExecutionResponsePayload(val success: Boolean, val message: String? = null)
        data class IsInDesktopResponsePayload(val isInDesktop: Boolean)
        data class UserTakeoverPayload(val isContinue: Boolean, val isCancel: Boolean)

        data class DetectBlockingAdPayload(
            val hasPopup: Boolean,
            val hasCloseButton: Boolean,
            val popupType: String,
            val closeButtonX: Int?,
            val closeButtonY: Int?,
            val popupScore: Int,
            val closeButtonScore: Int,
            val elapsedTimeMs: Long
        )

        data class HandleAdInterceptionPayload(
            val success: Boolean,
            val message: String,
            val adHandled: Boolean = false
        )

        data class CompareRegionDiffPayload(
            val diffRatio: Double
        )

        data class WaitingPausePayload(
            val isPause: Boolean = false
        )

        data class CheckLoginPagePayload(
            val isLoginPage: Boolean,
            val confidence: Float,
            val reasons: List<String>
        )
    }
}
