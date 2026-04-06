package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.app.Application
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.MindboxError
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal interface WebViewOperationExecutor {

    fun executeAsyncOperation(context: Application, payload: String?)

    suspend fun executeSyncOperation(payload: String?): String
}

internal class MindboxWebViewOperationExecutor : WebViewOperationExecutor {

    companion object {
        private const val OPERATION_FIELD = "operation"
        private const val BODY_FIELD = "body"
    }

    override fun executeAsyncOperation(context: Application, payload: String?) {
        val (operation, body) = parseOperationRequest(payload)
        MindboxEventManager.asyncOperation(
            context = context,
            name = operation,
            body = body,
        )
    }

    override suspend fun executeSyncOperation(payload: String?): String {
        val (operation, body) = parseOperationRequest(payload)
        return suspendCancellableCoroutine { continuation ->
            MindboxEventManager.syncOperation(
                name = operation,
                bodyJson = body,
                onSuccess = { responseBody: String ->
                    if (continuation.isActive) {
                        continuation.resume(responseBody)
                    }
                },
                onError = { error: MindboxError ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException(error.toJson())
                        )
                    }
                },
            )
        }
    }

    private fun parseOperationRequest(payload: String?): Pair<String, String> {
        val jsonObject: JsonObject = JsonParser.parseString(payload).asJsonObject
        val operation: String = jsonObject.getAsJsonPrimitive(OPERATION_FIELD)?.asString
            ?: throw IllegalArgumentException("Operation is not provided")
        val body: String = jsonObject.getAsJsonObject(BODY_FIELD)?.toString()
            ?: throw IllegalArgumentException("Body is not provided")
        return operation to body
    }
}
