package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.Mindbox.logI
import cloud.mindbox.mobile_sdk.getErrorResponseBodyData
import cloud.mindbox.mobile_sdk.getImageUrl
import cloud.mindbox.mobile_sdk.inapp.domain.extensions.asVolleyError
import cloud.mindbox.mobile_sdk.inapp.domain.extensions.getProductFromTargetingData
import cloud.mindbox.mobile_sdk.inapp.domain.extensions.getVolleyErrorDetails
import cloud.mindbox.mobile_sdk.inapp.domain.extensions.shouldTrackImageDownloadError
import cloud.mindbox.mobile_sdk.inapp.domain.extensions.shouldTrackTargetingError
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppContentFetcher
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppProcessingManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppTargetingErrorRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.logger.*
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason
import kotlinx.coroutines.*

internal class InAppProcessingManagerImpl(
    private val inAppGeoRepository: InAppGeoRepository,
    private val inAppSegmentationRepository: InAppSegmentationRepository,
    private val inAppTargetingErrorRepository: InAppTargetingErrorRepository,
    private val inAppContentFetcher: InAppContentFetcher,
    private val inAppRepository: InAppRepository,
    private val inAppFailureTracker: InAppFailureTracker
) : InAppProcessingManager {

    companion object {
        private const val RESPONSE_STATUS_CUSTOMER_SEGMENTS_REQUIRE_CUSTOMER =
            "CheckCustomerSegments requires customer"
    }

    override suspend fun chooseInAppToShow(
        inApps: List<InApp>,
        triggerEvent: InAppEventType,
    ): InApp? {
        for (inApp in inApps) {
            val data = getTargetingData(triggerEvent)
            var isTargetingErrorOccurred = false
            var isInAppContentFetched: Boolean? = null
            var targetingCheck = false
            var imageFetchError: Throwable? = null
            withContext(Dispatchers.IO) {
                val imageJob =
                    launch(start = CoroutineStart.LAZY) {
                        runCatching {
                            isInAppContentFetched =
                                inAppContentFetcher.fetchContent(
                                    inApp.id,
                                    inApp.form.variants.first()
                                )
                        }.onFailure { throwable ->
                            when (throwable) {
                                is CancellationException -> {
                                    inAppContentFetcher.cancelFetching(inApp.id)
                                    isInAppContentFetched = null
                                }

                                is InAppContentFetchingError -> {
                                    isInAppContentFetched = false
                                    imageFetchError = throwable
                                }
                            }
                        }
                    }
                val targetingJob = launch(start = CoroutineStart.LAZY) {
                    runCatching {
                        inApp.targeting.fetchTargetingInfo(data)
                        targetingCheck = inApp.targeting.checkTargeting(data)
                    }.onFailure { throwable ->
                        when (throwable) {
                            is GeoError -> {
                                isTargetingErrorOccurred = true
                                inAppGeoRepository.setGeoStatus(GeoFetchStatus.GEO_FETCH_ERROR)
                                if (throwable.shouldTrackTargetingError()) {
                                    inAppTargetingErrorRepository.saveError(
                                        key = TargetingErrorKey.Geo,
                                        error = throwable
                                    )
                                }
                                MindboxLoggerImpl.e(this, "Error fetching geo", throwable)
                            }

                            is CustomerSegmentationError -> {
                                isTargetingErrorOccurred = true
                                inAppSegmentationRepository.setCustomerSegmentationStatus(
                                    CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
                                )
                                if (throwable.shouldTrackTargetingError()) {
                                    inAppTargetingErrorRepository.saveError(
                                        key = TargetingErrorKey.CustomerSegmentation,
                                        error = throwable
                                    )
                                }
                                handleCustomerSegmentationErrorLog(throwable)
                            }

                            else -> {
                                MindboxLoggerImpl.e(this, throwable.message ?: "", throwable)
                                inAppFailureTracker.sendFailure(
                                    inAppId = inApp.id,
                                    failureReason = FailureReason.UNKNOWN_ERROR,
                                    errorDetails = "Unknown exception when checking target ${throwable.message}. ${throwable.cause?.getVolleyErrorDetails() ?: "volleyError=null"}"
                                )
                                throw throwable
                            }
                        }
                    }
                }
                listOf(imageJob, targetingJob.apply {
                    invokeOnCompletion {
                        if (imageJob.isActive && !targetingCheck) {
                            imageJob.cancel()
                            mindboxLogD("Cancelling content loading since targeting is $targetingCheck")
                        }
                    }
                }).onEach {
                    it.start()
                }.joinAll()
            }
            mindboxLogD("loading and targeting fetching finished")
            if (isTargetingErrorOccurred) return chooseInAppToShow(inApps, triggerEvent)
            trackTargetingErrorIfAny(inApp, data)
            if (isInAppContentFetched == false && targetingCheck) {
                imageFetchError?.takeIf { it.shouldTrackImageDownloadError() }?.let { error ->
                    inAppFailureTracker.collectFailure(
                        inAppId = inApp.id,
                        failureReason = FailureReason.IMAGE_DOWNLOAD_FAILED,
                        errorDetails = error.message + "\n Url is ${inApp.form.variants.first().getImageUrl()}"
                    )
                }
            }
            if (isInAppContentFetched == false) {
                mindboxLogD("Skipping inApp with id = ${inApp.id} due to content fetching error.")
                continue
            }
            mindboxLogD("Check ${inApp.targeting.type}: $targetingCheck")
            if (!targetingCheck) {
                mindboxLogD("Skipping inApp with id = ${inApp.id} due to targeting is false")
            }
            if (targetingCheck) {
                sendTargetedInApp(inApp, triggerEvent)
                inAppRepository.saveTargetedInAppWithEvent(
                    inAppId = inApp.id,
                    triggerEvent.hashCode()
                )
                inAppFailureTracker.clearFailures()
                return inApp
            }
        }
        inAppFailureTracker.sendCollectedFailures()
        return null
    }

    override suspend fun sendTargetedInApp(inApp: InApp, triggerEvent: InAppEventType) {
        var isTargetingErrorOccurred = false
        val data = getTargetingData(triggerEvent)
        runCatching {
            inApp.targeting.fetchTargetingInfo(data)
        }.onFailure { throwable ->
            when (throwable) {
                is GeoError -> {
                    isTargetingErrorOccurred = true
                    inAppGeoRepository.setGeoStatus(GeoFetchStatus.GEO_FETCH_ERROR)
                    InAppProcessingManagerImpl.mindboxLogE("Error fetching geo", throwable)
                }

                is CustomerSegmentationError -> {
                    isTargetingErrorOccurred = true
                    inAppSegmentationRepository.setCustomerSegmentationStatus(
                        CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
                    )
                    handleCustomerSegmentationErrorLog(throwable)
                }

                else -> InAppProcessingManagerImpl.mindboxLogE("Error fetching segmentation", throwable)
            }
        }
        if (isTargetingErrorOccurred) return sendTargetedInApp(inApp, triggerEvent)
        if (inApp.targeting.checkTargeting(data)) {
            logI("InApp with id = ${inApp.id} sends targeting by event $triggerEvent")
            inAppRepository.sendUserTargeted(inAppId = inApp.id)
        }
    }

    private fun getTargetingData(triggerEvent: InAppEventType): TargetingData {
        val ordinalEvent = triggerEvent as? InAppEventType.OrdinalEvent

        return TargetingDataWrapper(
            triggerEvent.name,
            ordinalEvent?.body
        )
    }

    private fun handleCustomerSegmentationErrorLog(error: CustomerSegmentationError) {
        val volleyError = error.cause.asVolleyError()
        volleyError?.let {
            if ((volleyError.networkResponse?.statusCode == 400) && (volleyError.getErrorResponseBodyData()
                    .contains(RESPONSE_STATUS_CUSTOMER_SEGMENTS_REQUIRE_CUSTOMER))
            ) {
                mindboxLogI("Cannot check customer segment. It's a new customer")
                return
            }
        }
        mindboxLogW("Error fetching customer segmentations", error)
    }

    private fun trackTargetingErrorIfAny(inApp: InApp, data: TargetingData) {
        when {
            inApp.targeting.hasSegmentationNode() &&
                inAppSegmentationRepository.getCustomerSegmentationFetched() == CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR -> {
                inAppTargetingErrorRepository.getError(TargetingErrorKey.CustomerSegmentation)
                    ?.let { errorDetails ->
                        inAppFailureTracker.collectFailure(
                            inAppId = inApp.id,
                            failureReason = FailureReason.CUSTOMER_SEGMENT_REQUEST_FAILED,
                            errorDetails = errorDetails
                        )
                    }
                return
            }

            inApp.targeting.hasGeoNode() &&
                inAppGeoRepository.getGeoFetchedStatus() == GeoFetchStatus.GEO_FETCH_ERROR -> {
                inAppTargetingErrorRepository.getError(TargetingErrorKey.Geo)
                    ?.let { errorDetails ->
                        inAppFailureTracker.collectFailure(
                            inAppId = inApp.id,
                            failureReason = FailureReason.GEO_TARGETING_FAILED,
                            errorDetails = errorDetails
                        )
                    }
                return
            }

            inApp.targeting.hasProductSegmentationNode() -> {
                data.getProductFromTargetingData()?.let { product ->
                    inAppTargetingErrorRepository.getError(
                        TargetingErrorKey.ProductSegmentation(product)
                    )?.let { errorDetails ->
                        inAppFailureTracker.collectFailure(
                            inAppId = inApp.id,
                            failureReason = FailureReason.PRODUCT_SEGMENT_REQUEST_FAILED,
                            errorDetails = errorDetails
                        )
                    }
                }
            }
        }
    }

    private class TargetingDataWrapper(
        override val triggerEventName: String,
        override val operationBody: String? = null,
    ) : TargetingData.OperationName, TargetingData.OperationBody
}
