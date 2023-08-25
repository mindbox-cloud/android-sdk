package cloud.mindbox.mobile_sdk.managers

import android.util.Log
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
import com.android.volley.DefaultRetryPolicy
import com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
import com.android.volley.Request
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.google.gson.Gson
import kotlinx.coroutines.*
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal class GatewayManager(private val mindboxServiceGenerator: MindboxServiceGenerator) {

    companion object {
        private const val TIMEOUT_DELAY = 60000
        private const val MAX_RETRIES = 0
        private const val MONITORING_DELAY = 5000
    }

    private val gson by lazy { Gson() }
    private val gatewayScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main + Job()) }
    private fun getCustomerSegmentationsUrl(configuration: Configuration): String {
        return "https://${configuration.domain}/v3/operations/sync?endpointId=${configuration.endpointId}&operation=Tracker.CheckCustomerSegments&deviceUUID=${MindboxPreferences.deviceUuid}"
    }

    private fun getProductSegmentationUrl(configuration: Configuration): String {
        return "https://${configuration.domain}/v3/operations/sync?endpointId=${configuration.endpointId}&operation=Tracker.CheckProductSegments&transactionId=${UUID.randomUUID()}"
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
                urlQueries[UrlQuery.DATE_TIME_OFFSET.value] =
                    getTimeOffset(event.enqueueTimestamp, shouldCountOffset)
            }

            is EventType.PushDelivered -> {
                urlQueries[UrlQuery.ENDPOINT_ID.value] = configuration.endpointId
                urlQueries[UrlQuery.UNIQ_KEY.value] =
                    event.additionalFields?.get(EventParameters.UNIQ_KEY.fieldName) ?: ""
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
        is EventType.PushClicked,
        is EventType.TrackVisit,
        is EventType.AsyncOperation,
        is EventType.SyncOperation,
        -> Request.Method.POST

        is EventType.PushDelivered -> Request.Method.GET
    }

    private fun getTimeOffset(
        timeMls: Long,
        shouldCountOffset: Boolean,
    ): String = if (shouldCountOffset) {
        (System.currentTimeMillis() - timeMls).toString()
    } else "0"

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

    private fun convertBodyToJson(body: String?): JSONObject? {
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

    suspend fun checkGeoTargeting(configuration: Configuration): GeoTargetingDto {
        return suspendCoroutine { continuation ->
            mindboxServiceGenerator.addToRequestQueue(
                MindboxRequest(
                    Request.Method.GET,
                    "https://${configuration.domain}/geo",
                    configuration,
                    null,
                    { jsonObject ->
                        continuation.resume(
                            gson.fromJson(
                                jsonObject.toString(),
                                GeoTargetingDto::class.java
                            )
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
                        continuation.resume(
                            gson.fromJson(
                                response.toString(),
                                ProductSegmentationResponseDto::class.java
                            )
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
                        continuation.resume(
                            gson.fromJson(
                                response.toString(),
                                SegmentationCheckResponse::class.java
                            )
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
            val url =
                "https://${configuration.domain}/v3/operations/async?endpointId=${configuration.endpointId}&operation=MobileSdk.Logs&deviceUUID=${MindboxPreferences.deviceUuid}&transactionId=${
                    UUID.randomUUID()
                }"
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
                StringRequest(
                    Request.Method.GET,
                    getConfigUrl(configuration),
                    { response ->
                        //val testJsonSnackBarAndModal =
                      //      "{\"monitoring\":{\"logs\":[{\"requestId\":\"4d37535e-f1c5-4c84-8622-58070601856e\",\"deviceUUID\":\"d2170bcf-b83e-49dc-954c-67f909fbeb99\",\"from\":\"2023-07-19T08:27:00\",\"to\":\"2023-07-30T11:30:00\"}]},\"settings\":{\"operations\":{\"viewProduct\":{\"systemName\":\"viewProduct\"},\"viewCategory\":{\"systemName\":\"viewCategory\"}}},\"inapps\":[{\"id\":\"094c077d-d1b1-4030-8920-5fe56b4e4e27\",\"sdkVersion\":{\"min\":8,\"max\":null},\"targeting\":{\"nodes\":[{\"${"$"}type\":\"true\"}],\"${"$"}type\":\"and\"},\"form\":{\"variants\":[{\"${"$"}type\":\"modal\",\"imageUrl\":\"\",\"redirectUrl\":\"\",\"intentPayload\":\"\",\"content\":{\"background\":{\"layers\":[{\"${"$"}type\":\"image\",\"action\":{\"${"$"}type\":\"redirectUrl\",\"intentPayload\":\"testpayload1\",\"value\":\"https://w7.pngwing.com/pngs/715/287/png-transparent-number-1-number-1-creative-cartoon.png\"},\"source\":{\"${"$"}type\":\"url\",\"value\":\"https://klike.net/uploads/posts/2020-06/1593061853_23.jpg\"}}]},\"elements\":[{\"${"$"}type\":\"closeButton\",\"color\":\"#FF0000\",\"lineWidth\":4,\"size\":{\"kind\":\"dp\",\"width\":32,\"height\":32},\"position\":{\"margin\":{\"kind\":\"proportion\",\"top\":0.03,\"right\":0.03,\"left\":0.0,\"bottom\":0.0}}}]}}]}},{\"id\":\"094c077d-d1b1-4030-8920-5fe56b4e4e27\",\"sdkVersion\":{\"min\":3,\"max\":7},\"targeting\":{\"nodes\":[{\"${"$"}type\":\"true\"}],\"${"$"}type\":\"and\"},\"form\":{\"variants\":[{\"imageUrl\":\"https://klike.net/uploads/posts/2020-06/1593061853_23.jpg\",\"redirectUrl\":\"https://w7.pngwing.com/pngs/715/287/png-transparent-number-1-number-1-creative-cartoon.png\",\"intentPayload\":\"testpayload1\",\"${"$"}type\":\"simpleImage\"}]}},{\"id\":\"5696ac18-70cb-496f-80c5-a47eb7573df7\",\"sdkVersion\":{\"min\":8,\"max\":null},\"targeting\":{\"nodes\":[{\"${"$"}type\":\"true\"}],\"${"$"}type\":\"and\"},\"form\":{\"variants\":[{\"${"$"}type\":\"snackbar\",\"imageUrl\":\"\",\"redirectUrl\":\"\",\"intentPayload\":\"\",\"content\":{\"background\":{\"layers\":[{\"${"$"}type\":\"image\",\"action\":{\"${"$"}type\":\"redirectUrl\",\"intentPayload\":\"testpayload2\",\"value\":\"https://img.freepik.com/free-psd/a-3d-letter-2-with-a-yellow-background_220664-4522.jpg\"},\"source\":{\"${"$"}type\":\"url\",\"value\":\"https://fikiwiki.com/uploads/posts/2022-02/1645022241_1-fikiwiki-com-p-kartinki-tsifra-2-1.png\"}}]},\"position\":{\"gravity\":{\"horizontal\":\"center\",\"vertical\":\"top\"},\"margin\":{\"kind\":\"dp\",\"top\":20,\"bottom\":20,\"left\":20,\"right\":20}},\"elements\":[{\"${"$"}type\":\"closeButton\",\"color\":\"#0000FF\",\"lineWidth\":4,\"size\":{\"kind\":\"dp\",\"width\":32,\"height\":32},\"position\":{\"margin\":{\"kind\":\"proportion\",\"right\":0.03,\"top\":0.03,\"left\":0.0,\"bottom\":0.0}}}]}}]}},{\"id\":\"5696ac18-70cb-496f-80c5-a47eb7573df7\",\"sdkVersion\":{\"min\":4,\"max\":7},\"targeting\":{\"nodes\":[{\"${"$"}type\":\"true\"}],\"${"$"}type\":\"and\"},\"form\":{\"variants\":[{\"imageUrl\":\"https://fikiwiki.com/uploads/posts/2022-02/1645022241_1-fikiwiki-com-p-kartinki-tsifra-2-1.png\",\"redirectUrl\":\"https://img.freepik.com/free-psd/a-3d-letter-2-with-a-yellow-background_220664-4522.jpg\",\"intentPayload\":\"testpayload2\",\"${"$"}type\":\"simpleImage\"}]}}]}"
                     //   val testJsonOnlySnackbar =
                     //       "{\"monitoring\":{\"logs\":[{\"requestId\":\"4d37535e-f1c5-4c84-8622-58070601856e\",\"deviceUUID\":\"d2170bcf-b83e-49dc-954c-67f909fbeb99\",\"from\":\"2023-07-19T08:27:00\",\"to\":\"2023-07-30T11:30:00\"}]},\"settings\":{\"operations\":{\"viewProduct\":{\"systemName\":\"viewProduct\"},\"viewCategory\":{\"systemName\":\"viewCategory\"}}},\"inapps\":[{\"id\":\"5696ac18-70cb-496f-80c5-a47eb7573df7\",\"sdkVersion\":{\"min\":8,\"max\":null},\"targeting\":{\"nodes\":[{\"${"$"}type\":\"true\"}],\"${"$"}type\":\"and\"},\"form\":{\"variants\":[{\"${"$"}type\":\"snackbar\",\"imageUrl\":\"\",\"redirectUrl\":\"\",\"intentPayload\":\"\",\"content\":{\"background\":{\"layers\":[{\"${"$"}type\":\"image\",\"action\":{\"${"$"}type\":\"redirectUrl\",\"intentPayload\":\"testpayload2\",\"value\":\"https://img.freepik.com/free-psd/a-3d-letter-2-with-a-yellow-background_220664-4522.jpg\"},\"source\":{\"${"$"}type\":\"url\",\"value\":\"https://mobpush-images.mindbox.ru/Mpush-test/76/e521856b-d4b3-406e-a797-b0753074bfc8.png\"}}]},\"position\":{\"gravity\":{\"horizontal\":\"center\",\"vertical\":\"bottom\"},\"margin\":{\"kind\":\"dp\",\"top\":20,\"bottom\":20,\"left\":90,\"right\":90}},\"elements\":[{\"${"$"}type\":\"closeButton\",\"color\":\"#0000FF\",\"lineWidth\":4,\"size\":{\"kind\":\"dp\",\"width\":32,\"height\":32},\"position\":{\"margin\":{\"kind\":\"proportion\",\"right\":0.03,\"top\":0.03,\"left\":0.0,\"bottom\":0.0}}}]}}]}},{\"id\":\"5696ac18-70cb-496f-80c5-a47eb7573df7\",\"sdkVersion\":{\"min\":4,\"max\":7},\"targeting\":{\"nodes\":[{\"${"$"}type\":\"true\"}],\"${"$"}type\":\"and\"},\"form\":{\"variants\":[{\"imageUrl\":\"https://fikiwiki.com/uploads/posts/2022-02/1645022241_1-fikiwiki-com-p-kartinki-tsifra-2-1.png\",\"redirectUrl\":\"https://img.freepik.com/free-psd/a-3d-letter-2-with-a-yellow-background_220664-4522.jpg\",\"intentPayload\":\"testpayload2\",\"${"$"}type\":\"simpleImage\"}]}}]}"
                        continuation.resume(response)
                    },
                    { error ->
                        continuation.resumeWithException(error)
                    },
                )
            )
        }
    }
}
