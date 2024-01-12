package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.getErrorResponseBodyData
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppContentFetcher
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppChoosingManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.models.InAppEventType
import com.android.volley.VolleyError
import kotlinx.coroutines.*

internal class InAppChoosingManagerImpl(
    private val inAppGeoRepository: InAppGeoRepository,
    private val inAppSegmentationRepository: InAppSegmentationRepository,
    private val inAppContentFetcher: InAppContentFetcher
) : InAppChoosingManager {

    companion object {
        private const val RESPONSE_STATUS_CUSTOMER_SEGMENTS_REQUIRE_CUSTOMER =
            "CheckCustomerSegments requires customer"
    }
    override suspend fun chooseInAppToShow(
        inApps: List<InApp>,
        triggerEvent: InAppEventType,
    ): InAppType? {
        for (inApp in inApps) {
            val data = getTargetingData(triggerEvent)
            var isTargetingErrorOccurred = false
            var isInAppContentFetched: Boolean? = null
            var targetingCheck = false
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
                                MindboxLoggerImpl.e(this, "Error fetching geo", throwable)
                            }

                            is CustomerSegmentationError -> {
                                isTargetingErrorOccurred = true
                                inAppSegmentationRepository.setCustomerSegmentationStatus(
                                    CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
                                )
                                handleCustomerSegmentationErrorLog(throwable)
                            }

                            else -> {
                                MindboxLoggerImpl.e(this, throwable.message ?: "", throwable)
                                throw throwable
                            }
                        }
                    }
                }
                listOf(imageJob.apply {
                    invokeOnCompletion {
                        if (targetingJob.isActive && isInAppContentFetched == false) {
                            targetingJob.cancel()
                            mindboxLogD("Cancelling targeting checking since content loading is $isInAppContentFetched")
                        }
                    }
                }, targetingJob.apply {
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
            if (isInAppContentFetched == false) {
                mindboxLogD("Skipping inApp with id = ${inApp.id} due to content fetching error.")
                continue
            }
            mindboxLogD("Check ${inApp.targeting.type}: $targetingCheck")
            if (!targetingCheck) {
                mindboxLogD("Skipping inApp with id = ${inApp.id} due to targeting is false")
            }
            if (targetingCheck) {
                return inApp.form.variants.firstOrNull()
            }
        }
        return null
    }

    private fun getTargetingData(triggerEvent: InAppEventType): TargetingData {
        val ordinalEvent = triggerEvent as? InAppEventType.OrdinalEvent

        return TargetingDataWrapper(
            triggerEvent.name,
            ordinalEvent?.body
        )
    }

    private fun handleCustomerSegmentationErrorLog(error: CustomerSegmentationError) {
        val volleyError = error.cause as? VolleyError
        volleyError?.let { error ->
            if ((error.networkResponse?.statusCode == 400) && (error.getErrorResponseBodyData()
                    .contains(RESPONSE_STATUS_CUSTOMER_SEGMENTS_REQUIRE_CUSTOMER))
            ) {
                mindboxLogI("Cannot check customer segment. It's a new customer")
                return
            }
        }
        mindboxLogW("Error fetching customer segmentations", error)
    }

    private class TargetingDataWrapper(
        override val triggerEventName: String,
        override val operationBody: String? = null,
    ) : TargetingData.OperationName, TargetingData.OperationBody

}