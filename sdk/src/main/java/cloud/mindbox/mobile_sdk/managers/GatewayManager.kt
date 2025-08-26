package cloud.mindbox.mobile_sdk.managers

import android.util.Log
import androidx.annotation.VisibleForTesting
import cloud.mindbox.mobile_sdk.fromJsonTyped
import cloud.mindbox.mobile_sdk.inapp.data.dto.GeoTargetingDto
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.models.operation.OperationResponseBaseInternal
import cloud.mindbox.mobile_sdk.models.operation.request.LogResponseDto
import cloud.mindbox.mobile_sdk.models.operation.request.SegmentationCheckRequest
import cloud.mindbox.mobile_sdk.models.operation.response.SegmentationCheckResponse
import cloud.mindbox.mobile_sdk.network.MindboxServiceGenerator
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.toUrlQueryString
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import com.android.volley.DefaultRetryPolicy
import com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
import com.android.volley.ParseError
import com.android.volley.Request
import com.android.volley.VolleyError
import com.google.gson.Gson
import kotlinx.coroutines.*
import org.json.JSONException
import org.json.JSONObject
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal class GatewayManager(private val mindboxServiceGenerator: MindboxServiceGenerator) {

    companion object {
        private const val TIMEOUT_DELAY = 60000
        private const val MAX_RETRIES = 0
        private const val MONITORING_DELAY = 5000

        private const val OPERATION_MOBILE_SDK_LOGS = "MobileSdk.Logs"
        private const val OPERATION_CHECK_PRODUCT_SEGMENTS = "Tracker.CheckProductSegments"
        private const val OPERATION_CHECK_CUSTOMER_SEGMENTS = "Tracker.CheckCustomerSegments"
    }

    private val gson by lazy { Gson() }
    private val gatewayScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main + Job()) }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun getCustomerSegmentationsUrl(configuration: Configuration): String {
        return buildEventUrl(
            configuration = configuration,
            deviceUuid = MindboxPreferences.deviceUuid,
            shouldCountOffset = false,
            event = Event(
                eventType = EventType.SyncOperation(OPERATION_CHECK_CUSTOMER_SEGMENTS)
            )
        )
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun getProductSegmentationUrl(configuration: Configuration): String {
        return buildEventUrl(
            configuration = configuration,
            deviceUuid = MindboxPreferences.deviceUuid,
            shouldCountOffset = false,
            event = Event(
                eventType = EventType.SyncOperation(OPERATION_CHECK_PRODUCT_SEGMENTS)
            )
        )
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun getLogsUrl(configuration: Configuration): String {
        return buildEventUrl(
            configuration = configuration,
            deviceUuid = MindboxPreferences.deviceUuid,
            shouldCountOffset = false,
            event = Event(
                eventType = EventType.AsyncOperation(OPERATION_MOBILE_SDK_LOGS)
            )
        )
    }

    private fun getConfigUrl(configuration: Configuration): String {
        return "https://${configuration.domain}/mobile/byendpoint/${configuration.endpointId}.json"
    }

    private fun buildEventUrl(
        configuration: Configuration,
        deviceUuid: String,
        shouldCountOffset: Boolean,
        event: Event,
    ): String {
        val urlQueries: LinkedHashMap<String, String> = linkedMapOf(
            UrlQuery.DEVICE_UUID.value to deviceUuid,
        )

        when (event.eventType) {
            is EventType.AppInstalled,
            is EventType.AppInstalledWithoutCustomer,
            is EventType.AppInfoUpdated,
            is EventType.AppKeepalive,
            is EventType.PushClicked,
            is EventType.AsyncOperation,
            -> {
                urlQueries[UrlQuery.ENDPOINT_ID.value] = configuration.endpointId
                urlQueries[UrlQuery.OPERATION.value] = event.eventType.operation
                urlQueries[UrlQuery.TRANSACTION_ID.value] = event.transactionId
                urlQueries[UrlQuery.DATE_TIME_OFFSET.value] =
                    getTimeOffset(event.enqueueTimestamp, shouldCountOffset)
            }

            is EventType.TrackVisit -> {
                urlQueries[UrlQuery.TRANSACTION_ID.value] = event.transactionId
                urlQueries[UrlQuery.DATE_TIME_OFFSET.value] =
                    getTimeOffset(event.enqueueTimestamp, shouldCountOffset)
            }

            is EventType.SyncOperation -> {
                urlQueries[UrlQuery.ENDPOINT_ID.value] = configuration.endpointId
                urlQueries[UrlQuery.OPERATION.value] = event.eventType.operation
            }
        }

        return "https://${configuration.domain}${event.eventType.endpoint}${urlQueries.toUrlQueryString()}"
    }

    fun sendAsyncEvent(
        configuration: Configuration,
        deviceUuid: String,
        event: Event,
        shouldCountOffset: Boolean,
        isSentListener: (Boolean) -> Unit,
    ) = sendEvent(
        configuration = configuration,
        deviceUuid = deviceUuid,
        event = event,
        shouldCountOffset = shouldCountOffset,
        onSuccess = { isSentListener.invoke(true) },
        onError = { error -> isSentListener.invoke(isAsyncSent(error.statusCode)) },
    )

    fun <T : OperationResponseBaseInternal> sendEvent(
        configuration: Configuration,
        deviceUuid: String,
        event: Event,
        classOfT: Class<T>,
        shouldCountOffset: Boolean,
        onSuccess: (T) -> Unit,
        onError: (MindboxError) -> Unit,
    ) = sendEvent(
        configuration = configuration,
        deviceUuid = deviceUuid,
        event = event,
        shouldCountOffset = shouldCountOffset,
        onSuccess = { body -> handleSuccessResponse(body, onSuccess, onError, classOfT) },
        onError = onError,
    )

    fun sendEvent(
        configuration: Configuration,
        deviceUuid: String,
        event: Event,
        shouldCountOffset: Boolean,
        onSuccess: (String) -> Unit,
        onError: (MindboxError) -> Unit,
    ) {
        try {
            val requestType: Int = getRequestType(event.eventType)
            val url: String = buildEventUrl(configuration, deviceUuid, shouldCountOffset, event)
            val jsonRequest: JSONObject? = convertBodyToJson(event.body)
            val request = MindboxRequest(
                methodType = requestType,
                fullUrl = url,
                configuration = configuration,
                jsonRequest = jsonRequest,
                listener = {
                    MindboxLoggerImpl.d(this, "Event from background successful sent")
                    onSuccess.invoke(it.toString())
                },
                errorsListener = { volleyError ->
                    handleError(volleyError, onSuccess, onError)
                },
            ).apply {
                setShouldCache(false)
                retryPolicy = DefaultRetryPolicy(TIMEOUT_DELAY, MAX_RETRIES, DEFAULT_BACKOFF_MULT)
            }

            mindboxServiceGenerator.addToRequestQueue(request)
        } catch (e: Exception) {
            MindboxLoggerImpl.e(this, "Sending event was failure with exception", e)
            onError.invoke(MindboxError.Unknown(e))
        }
    }

    private fun getRequestType(eventType: EventType): Int = when (eventType) {
        is EventType.AppInstalled,
        is EventType.AppInstalledWithoutCustomer,
        is EventType.AppInfoUpdated,
        is EventType.AppKeepalive,
        is EventType.PushClicked,
        is EventType.TrackVisit,
        is EventType.AsyncOperation,
        is EventType.SyncOperation,
        -> Request.Method.POST
    }

    private fun getTimeOffset(
        timeMls: Long,
        shouldCountOffset: Boolean,
    ): String = if (shouldCountOffset) {
        (System.currentTimeMillis() - timeMls).toString()
    } else {
        "0"
    }

    private fun <T : OperationResponseBaseInternal> handleSuccessResponse(
        data: String,
        onSuccess: (T) -> Unit,
        onError: (MindboxError) -> Unit,
        classOfT: Class<T>,
    ) = gatewayScope.launch {
        try {
            val body = convertJsonToBody(data, MindboxResponse::class.java)
            if (body.status != MindboxResponse.STATUS_VALIDATION_ERROR) {
                onSuccess.invoke(convertJsonToBody(data, classOfT))
            } else {
                val validationMessages = body.validationMessages ?: emptyList()
                onError.invoke(MindboxError.Validation(200, body.status, validationMessages))
            }
        } catch (e: Exception) {
            onError.invoke(MindboxError.Unknown(e))
        }
    }

    private fun handleError(
        volleyError: VolleyError,
        onSuccess: (String) -> Unit,
        onError: (MindboxError) -> Unit,
    ) = gatewayScope.launch {
        try {
            val error = volleyError.networkResponse
            if (error == null) {
                onError.invoke(MindboxError.UnknownServer())
                return@launch
            }
            val code = error.statusCode
            val errorData = error.data
            val errorBody: MindboxResponse? = errorData?.let { data ->
                convertJsonToBody(String(data), MindboxResponse::class.java)
            }

            when (val status = errorBody?.status) {
                null -> onError.invoke(MindboxError.UnknownServer())
                MindboxResponse.STATUS_SUCCESS,
                MindboxResponse.STATUS_TRANSACTION_ALREADY_PROCESSED,
                -> {
                    onSuccess.invoke(String(errorData))
                }

                MindboxResponse.STATUS_VALIDATION_ERROR -> onError.invoke(
                    MindboxError.Validation(
                        statusCode = code,
                        status = status,
                        validationMessages = errorBody.validationMessages ?: emptyList(),
                    )
                )

                MindboxResponse.STATUS_PROTOCOL_ERROR -> onError.invoke(
                    MindboxError.Protocol(
                        statusCode = code,
                        status = status,
                        errorMessage = errorBody.errorMessage,
                        errorId = errorBody.errorId,
                        httpStatusCode = errorBody.httpStatusCode,
                    )
                )

                MindboxResponse.STATUS_INTERNAL_SERVER_ERROR -> onError.invoke(
                    MindboxError.InternalServer(
                        statusCode = code,
                        status = status,
                        errorMessage = errorBody.errorMessage,
                        errorId = errorBody.errorId,
                        httpStatusCode = errorBody.httpStatusCode,
                    )
                )

                else -> onError.invoke(
                    MindboxError.UnknownServer(
                        statusCode = code,
                        status = status,
                        errorMessage = errorBody.errorMessage,
                        errorId = errorBody.errorId,
                        httpStatusCode = errorBody.httpStatusCode,
                    )
                )
            }
        } catch (e: Exception) {
            MindboxLoggerImpl.e(this, "Parsing server response was failure", e)
            onError.invoke(MindboxError.Unknown(e))
        }
    }

    private fun convertBodyToJson(body: String?): JSONObject? =
        if (body == null) {
            null
        } else {
            try {
                JSONObject(body)
            } catch (e: JSONException) {
                null
            }
        }

    private suspend fun <T> convertJsonToBody(
        data: String,
        classOfT: Class<T>,
    ) = withContext(Dispatchers.Default) { gson.fromJson(data, classOfT) }

    private fun isAsyncSent(statusCode: Int?) = statusCode?.let { code ->
        code < 300 || code in 400..499
    } ?: false

    suspend fun checkGeoTargeting(configuration: Configuration): GeoTargetingDto {
        return suspendCoroutine { continuation ->
            mindboxServiceGenerator.addToRequestQueue(
                MindboxRequest(
                    Request.Method.GET,
                    "https://${configuration.domain}/geo",
                    configuration,
                    null,
                    { response ->
                        continuation.resumeFromJson<GeoTargetingDto>(
                            json = response.toString()
                        )
                    },
                    { error ->
                        continuation.resumeWithException(GeoError(error))
                    }
                )
            )
        }
    }

    suspend fun checkProductSegmentation(
        configuration: Configuration,
        segmentation: ProductSegmentationRequestDto,
    ): ProductSegmentationResponseDto {
        return suspendCoroutine { continuation ->
            mindboxServiceGenerator.addToRequestQueue(
                MindboxRequest(
                    Request.Method.POST,
                    getProductSegmentationUrl(configuration),
                    configuration,
                    convertBodyToJson(
                        gson.toJson(
                            segmentation,
                            ProductSegmentationRequestDto::class.java
                        )
                    )!!,
                    { response ->
                        continuation.resumeFromJson<ProductSegmentationResponseDto>(
                            json = response.toString()
                        )
                    },
                    { error ->
                        continuation.resumeWithException(ProductSegmentationError(error))
                    }
                )
            )
        }
    }

    suspend fun checkCustomerSegmentations(
        configuration: Configuration,
        segmentationCheckRequest: SegmentationCheckRequest,
    ): SegmentationCheckResponse {
        return suspendCoroutine { continuation ->
            mindboxServiceGenerator.addToRequestQueue(
                MindboxRequest(
                    Request.Method.POST,
                    getCustomerSegmentationsUrl(configuration),
                    configuration,
                    convertBodyToJson(
                        gson.toJson(
                            segmentationCheckRequest,
                            SegmentationCheckRequest::class.java
                        )
                    )!!,
                    { response ->
                        continuation.resumeFromJson<SegmentationCheckResponse>(
                            json = response.toString()
                        )
                    },
                    { error ->
                        continuation.resumeWithException(CustomerSegmentationError(error))
                    }
                )
            )
        }
    }

    fun sendLogEvent(logs: LogResponseDto, configuration: Configuration) {
        try {
            val url = getLogsUrl(configuration)
            val jsonRequest: JSONObject? = convertBodyToJson(gson.toJson(logs))
            val request = MindboxRequest(
                methodType = Request.Method.POST,
                fullUrl = url,
                configuration = configuration,
                jsonRequest = jsonRequest,
                listener = {
                    Log.d("Success", "Sending logs success")
                },
                errorsListener = { volleyError ->
                    Log.e("Error", "Sending logs was failure with exception", volleyError)
                }
            ).apply {
                setShouldCache(false)
                retryPolicy =
                    DefaultRetryPolicy(MONITORING_DELAY, MAX_RETRIES, DEFAULT_BACKOFF_MULT)
            }
            mindboxServiceGenerator.addToRequestQueue(request)
        } catch (e: Exception) {
            Log.e("Error", "Sending event was failure with exception", e)
        }
    }

    suspend fun fetchMobileConfig(configuration: Configuration): String {
        return suspendCoroutine { continuation ->
            mindboxServiceGenerator.addToRequestQueue(
                MindboxRequest(
                    methodType = Request.Method.GET,
                    fullUrl = getConfigUrl(configuration),
                    configuration = configuration,
                    jsonRequest = null,
                    listener = { response ->
                        continuation.resume(response.toString())
                    },
                    errorsListener = { error ->
                        continuation.resumeWithException(error)
                    },
                )
            )
        }
    }

    private inline fun <reified T> Continuation<T>.resumeFromJson(json: String) {
        loggingRunCatching(null) {
            gson.fromJsonTyped<T>(json)
        }?.let { dto ->
            resume(dto)
        } ?: run {
            resumeWithException(
                ParseError(JSONException("Could not parse JSON: $json"))
            )
        }
    }
}
