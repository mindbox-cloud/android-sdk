package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.app.Application
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.MindboxError
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal interface WebViewOperationExecutor {

    fun executeAsyncOperation(
        context: Application,
        payload: String?,
        hostInAppId: String,
        hostTags: Map<String, String>?,
    )

    suspend fun executeSyncOperation(payload: String?, hostInAppId: String, hostTags: Map<String, String>?): String
}

internal interface WebViewOperationSender {

    fun asyncOperation(context: Application, name: String, body: String)

    fun syncOperation(
        name: String,
        body: String,
        onSuccess: (String) -> Unit,
        onError: (MindboxError) -> Unit,
    )
}

internal object MindboxWebViewOperationSender : WebViewOperationSender {

    override fun asyncOperation(context: Application, name: String, body: String) {
        MindboxEventManager.asyncOperation(context = context, name = name, body = body)
    }

    override fun syncOperation(
        name: String,
        body: String,
        onSuccess: (String) -> Unit,
        onError: (MindboxError) -> Unit,
    ) {
        MindboxEventManager.syncOperation(name = name, bodyJson = body, onSuccess = onSuccess, onError = onError)
    }
}

internal class MindboxWebViewOperationExecutor(
    private val gson: Gson,
    private val mobileConfigRepository: MobileConfigRepository,
    private val featureToggleManager: FeatureToggleManager,
    private val sender: WebViewOperationSender,
) : WebViewOperationExecutor {

    companion object {
        private const val OPERATION_FIELD = "operation"
        private const val BODY_FIELD = "body"
        private const val TAGS_FIELD = "tags"
    }

    override fun executeAsyncOperation(
        context: Application,
        payload: String?,
        hostInAppId: String,
        hostTags: Map<String, String>?,
    ) {
        val (operation, body) = parseOperationRequest(payload, hostInAppId, hostTags)
        sender.asyncOperation(
            context = context,
            name = operation,
            body = body,
        )
    }

    override suspend fun executeSyncOperation(payload: String?, hostInAppId: String, hostTags: Map<String, String>?): String {
        val (operation, body) = parseOperationRequest(payload, hostInAppId, hostTags)
        return suspendCancellableCoroutine { continuation ->
            sender.syncOperation(
                name = operation,
                body = body,
                onSuccess = { responseBody: String ->
                    if (continuation.isActive) {
                        continuation.resume(responseBody)
                    }
                },
                onError = { error: MindboxError ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            WebViewSyncOperationException(error.toWebViewDataJson(gson))
                        )
                    }
                },
            )
        }
    }

    private fun parseOperationRequest(payload: String?, hostInAppId: String, hostTags: Map<String, String>?): Pair<String, String> {
        payload ?: throw BridgeRefusalException(BridgeErrorCode.INVALID_PAYLOAD, "Payload is not provided")
        val jsonObject: JsonObject = runCatching { JsonParser.parseString(payload).asJsonObject }
            .getOrElse { throw BridgeRefusalException(BridgeErrorCode.INVALID_PAYLOAD, "Payload is not a valid JSON object", it) }
        val operation: String = runCatching { jsonObject.getAsJsonPrimitive(OPERATION_FIELD)?.asString }.getOrNull()
            ?: throw BridgeRefusalException(BridgeErrorCode.INVALID_PAYLOAD, "Operation is not provided")
        val bodyObject: JsonObject = runCatching { jsonObject.getAsJsonObject(BODY_FIELD) }.getOrNull()
            ?: throw BridgeRefusalException(BridgeErrorCode.INVALID_PAYLOAD, "Body is not provided")
        val tags = OperationTagsResolver.resolve(jsonObject, hostInAppId, hostTags, mobileConfigRepository, featureToggleManager)
        return operation to buildOperationBody(bodyObject, tags)
    }

    private fun buildOperationBody(bodyObject: JsonObject, tags: Map<String, String>?): String {
        if (!tags.isNullOrEmpty()) {
            mergeTags(bodyObject, tags)
        }
        return bodyObject.toString()
    }

    private fun mergeTags(bodyObject: JsonObject, tags: Map<String, String>) {
        val existingTags: JsonElement? = bodyObject.get(TAGS_FIELD)
        when {
            existingTags == null || existingTags.isJsonNull -> {
                bodyObject.add(TAGS_FIELD, gson.toJsonTree(tags))
            }

            existingTags.isJsonObject -> {
                val tagsObject: JsonObject = existingTags.asJsonObject
                tags.forEach { (key: String, value: String) ->
                    if (tagsObject.has(key)) {
                        mindboxLogW(
                            "WebView operation body `tags` already contains key `$key`; " +
                                "keeping client value, skipping in-app value"
                        )
                    } else {
                        tagsObject.addProperty(key, value)
                    }
                }
            }

            else -> {
                mindboxLogW(
                    "WebView operation body `tags` is not a JSON object; " +
                        "keeping client value, skipping in-app tags"
                )
            }
        }
    }
}
