package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.models.operation.OperationResponseBaseInternal
import cloud.mindbox.mobile_sdk.models.operation.response.SegmentationCheckResponse
import cloud.mindbox.mobile_sdk.network.MindboxServiceGenerator
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.toUrlQueryString
import cloud.mindbox.mobile_sdk.utils.BuildConfiguration
import com.android.volley.DefaultRetryPolicy
import com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
import com.android.volley.Request
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONException
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal object GatewayManager {

    private const val TIMEOUT_DELAY = 60000
    private const val MAX_RETRIES = 0
    private const val IN_APP_CONFIG_URL =
        "/inapps/byendpoint/someTestMobileEndpoint.json"


    private val gson by lazy { Gson() }
    val eventFlow = MutableSharedFlow<InAppEventType>()
    private val gatewayScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main + Job()) }

    private fun getSegmentationUrl(configuration: MindboxConfiguration): String {
        return "https://${configuration.domain}/v3/operations/sync?endpointId=${configuration.endpointId}&operation=Tracker.CheckCustomerSegments&deviceUUID=${MindboxPreferences.deviceUuid}"
    }

    private fun buildEventUrl(
        configuration: Configuration,
        deviceUuid: String,
        event: Event,
    ): String {

        val urlQueries: HashMap<String, String> = hashMapOf(
            UrlQuery.DEVICE_UUID.value to deviceUuid,
        )

        when (event.eventType) {
            is EventType.AppInstalled,
            is EventType.AppInstalledWithoutCustomer,
            is EventType.AppInfoUpdated,
            is EventType.PushClicked,
            is EventType.AsyncOperation,
            -> {
                urlQueries[UrlQuery.ENDPOINT_ID.value] = configuration.endpointId
                urlQueries[UrlQuery.OPERATION.value] = event.eventType.operation
                urlQueries[UrlQuery.TRANSACTION_ID.value] = event.transactionId
                urlQueries[UrlQuery.DATE_TIME_OFFSET.value] = getTimeOffset(event.enqueueTimestamp)
            }
            is EventType.PushDelivered -> {
                urlQueries[UrlQuery.ENDPOINT_ID.value] = configuration.endpointId
                urlQueries[UrlQuery.UNIQ_KEY.value] =
                    event.additionalFields?.get(EventParameters.UNIQ_KEY.fieldName) ?: ""
                urlQueries[UrlQuery.TRANSACTION_ID.value] = event.transactionId
                urlQueries[UrlQuery.DATE_TIME_OFFSET.value] = getTimeOffset(event.enqueueTimestamp)
            }
            is EventType.TrackVisit -> {
                urlQueries[UrlQuery.TRANSACTION_ID.value] = event.transactionId
                urlQueries[UrlQuery.DATE_TIME_OFFSET.value] = getTimeOffset(event.enqueueTimestamp)
            }
            is EventType.SyncOperation -> {
                urlQueries[UrlQuery.ENDPOINT_ID.value] = configuration.endpointId
                urlQueries[UrlQuery.OPERATION.value] = event.eventType.operation
            }
        }

        return "https://${configuration.domain}${event.eventType.endpoint}${urlQueries.toUrlQueryString()}"
    }

    fun sendAsyncEvent(
        context: Context,
        configuration: Configuration,
        deviceUuid: String,
        event: Event,
        isSentListener: (Boolean) -> Unit,
    ) = sendEvent(
        context = context,
        configuration = configuration,
        deviceUuid = deviceUuid,
        event = event,
        onSuccess = { isSentListener.invoke(true) },
        onError = { error -> isSentListener.invoke(isAsyncSent(error.statusCode)) },
    )

    fun <T : OperationResponseBaseInternal> sendEvent(
        context: Context,
        configuration: Configuration,
        deviceUuid: String,
        event: Event,
        classOfT: Class<T>,
        onSuccess: (T) -> Unit,
        onError: (MindboxError) -> Unit,
    ) = sendEvent(
        context = context,
        configuration = configuration,
        deviceUuid = deviceUuid,
        event = event,
        onSuccess = { body -> handleSuccessResponse(body, onSuccess, onError, classOfT) },
        onError = onError,
    )

    fun sendEvent(
        context: Context,
        configuration: Configuration,
        deviceUuid: String,
        event: Event,
        onSuccess: (String) -> Unit,
        onError: (MindboxError) -> Unit,
    ) {
        try {
            val requestType: Int = getRequestType(event.eventType)
            val url: String = buildEventUrl(configuration, deviceUuid, event)
            val jsonRequest: JSONObject? = convertBodyToJson(event.body)
            val isDebug = BuildConfiguration.isDebug(context)
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
                isDebug = isDebug,
            ).apply {
                setShouldCache(false)
                retryPolicy = DefaultRetryPolicy(TIMEOUT_DELAY, MAX_RETRIES, DEFAULT_BACKOFF_MULT)
            }

            MindboxServiceGenerator.getInstance(context)?.addToRequestQueue(request)
        } catch (e: Exception) {
            MindboxLoggerImpl.e(this, "Sending event was failure with exception", e)
            onError.invoke(MindboxError.Unknown(e))
        }
    }

    private fun getRequestType(eventType: EventType): Int = when (eventType) {
        is EventType.AppInstalled,
        is EventType.AppInstalledWithoutCustomer,
        is EventType.AppInfoUpdated,
        is EventType.PushClicked,
        is EventType.TrackVisit,
        is EventType.AsyncOperation,
        is EventType.SyncOperation,
        -> Request.Method.POST
        is EventType.PushDelivered -> Request.Method.GET
    }

    private fun getTimeOffset(
        timeMls: Long,
    ): String = (System.currentTimeMillis() - timeMls).toString()

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

    fun convertBodyToJson(body: String?): JSONObject? {
        return if (body == null) {
            null
        } else try {
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

    suspend fun checkSegmentation(
        context: Context,
        configuration: MindboxConfiguration,
        jsonObj: JSONObject,
    ): SegmentationCheckResponse {
        return suspendCoroutine { continuation ->
            MindboxServiceGenerator.getInstance(context)
                ?.addToRequestQueue(MindboxRequest(Request.Method.POST,
                    getSegmentationUrl(configuration),
                    DbManager.getConfigurations()!!,
                    jsonObj,
                    { response ->
                        gatewayScope.launch {
                            continuation.resume(gson.fromJson(response.toString(),
                                SegmentationCheckResponse::class.java))
                        }
                    },
                    { error ->
                        continuation.resumeWithException(error)
                    }, true))
        }

    }

    suspend fun fetchInAppConfig(context: Context, configuration: MindboxConfiguration): String {
        return suspendCoroutine { continuation ->
            MindboxServiceGenerator.getInstance(context)
                ?.addToRequestQueue(StringRequest(Request.Method.GET,
                    "https://${configuration.domain}$IN_APP_CONFIG_URL",
                    { response ->
                        continuation.resume(response)
                    },
                    { error ->
                        continuation.resumeWithException(error)
                    }))
        }

    }


}
