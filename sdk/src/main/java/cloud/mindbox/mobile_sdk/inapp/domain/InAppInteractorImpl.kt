package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.InAppEventType
import com.android.volley.VolleyError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class InAppInteractorImpl(
    private val inAppRepositoryImpl: InAppRepository,
    private val inAppGeoRepositoryImpl: InAppGeoRepository,
) :
    InAppInteractor {

    override fun processEventAndConfig(): Flow<InAppType> {
        return inAppRepositoryImpl.listenInAppConfig().filterNotNull()
            //TODO add eventProcessing
            .combine(inAppRepositoryImpl.listenInAppEvents()
                .filter { inAppEventType ->
                    MindboxLoggerImpl.d(this, "Event triggered: $inAppEventType")
                    inAppEventType is InAppEventType.AppStartup
                }) { config, event ->
                fetchGeoTargetingInfo(config)
                val inApp = chooseInAppToShow(config)
                when (val type = inApp?.form?.variants?.firstOrNull()) {
                    is Payload.SimpleImage -> InAppType.SimpleImage(inAppId = inApp.id,
                        imageUrl = type.imageUrl,
                        redirectUrl = type.redirectUrl,
                        intentData = type.intentPayload)
                    else -> {
                        MindboxLoggerImpl.d(this,
                            "No innaps to show found")
                        null
                    }
                }
            }.filterNotNull()
    }

    private suspend fun fetchGeoTargetingInfo(config: InAppConfig) {
        var isGeoCheckRequired = false
        for (inApp in config.inApps) {
            if (isGeoCheckRequired) {
                break
            } else {
                isGeoCheckRequired = checkGeoTargeting(listOf(inApp.targeting))
            }
        }
        if (isGeoCheckRequired) {
            runCatching {
                inAppGeoRepositoryImpl.fetchGeo()
            }.onFailure { throwable ->
                if (throwable is VolleyError) {
                    MindboxLoggerImpl.e(this, "Error fetching geo", throwable)
                } else {
                    throw throwable
                }
            }
        }
    }

    private fun checkGeoTargeting(targetings: List<TreeTargeting>): Boolean {
        var isGeoTargetingExist = false
        for (targeting in targetings) {
            when (targeting) {
                is TreeTargeting.CityNode -> {
                    isGeoTargetingExist = true
                    break
                }
                is TreeTargeting.CountryNode -> {
                    isGeoTargetingExist = true
                    break
                }
                is TreeTargeting.IntersectionNode -> {
                    isGeoTargetingExist = checkGeoTargeting(targeting.nodes)
                }
                is TreeTargeting.RegionNode -> {
                    isGeoTargetingExist = true
                    break
                }
                is TreeTargeting.UnionNode -> {
                    isGeoTargetingExist = checkGeoTargeting(targeting.nodes)
                }
                else -> {}
            }
        }
        return isGeoTargetingExist
    }

    private fun findInAppToShowWithoutCheckingSegmentations(configWithImmediatePreCheck: InAppConfig): InApp? {
        return configWithImmediatePreCheck.inApps.find { inApp ->
            inApp.targeting.getCustomerIsInTargeting(emptyList())
        }
    }

    private suspend fun chooseInAppToShow(
        config: InAppConfig,
    ): InApp? {
        val filteredConfig = prefilterConfig(config)
        MindboxLoggerImpl.d(this,
            "Filtered config has ${filteredConfig.inApps.size} inapps")
        val configWithInAppsBeforeFirstPendingPreCheck =
            getConfigWithInAppsBeforeFirstPendingPreCheck(filteredConfig)
        val configWithInAppsStartingWithFirstPendingPreCheck =
            filteredConfig.copy(inApps = filteredConfig.inApps.subtract(
                configWithInAppsBeforeFirstPendingPreCheck.inApps.toSet())
                .toList())
        return if (configWithInAppsBeforeFirstPendingPreCheck.inApps.isNotEmpty() && findInAppToShowWithoutCheckingSegmentations(
                configWithInAppsBeforeFirstPendingPreCheck) != null
        ) {
            findInAppToShowWithoutCheckingSegmentations(configWithInAppsBeforeFirstPendingPreCheck)

        } else if (configWithInAppsStartingWithFirstPendingPreCheck.inApps.isNotEmpty()) {
            runCatching {
                checkSegmentation(filteredConfig,
                    inAppRepositoryImpl.fetchSegmentations(
                        configWithInAppsStartingWithFirstPendingPreCheck))
            }.getOrElse { throwable ->
                if (throwable is VolleyError) {
                    MindboxLoggerImpl.e(this, throwable.message ?: "", throwable)
                    configWithInAppsStartingWithFirstPendingPreCheck.inApps
                        .filter { inApp -> inApp.targeting.preCheckTargeting() == SegmentationCheckResult.IMMEDIATE }
                        .find { inApp ->
                            inApp.targeting.getCustomerIsInTargeting(emptyList())
                        }
                } else {
                    throw throwable
                }
            }
        } else {
            null
        }
    }

    private fun prefilterConfig(config: InAppConfig): InAppConfig {
        MindboxLoggerImpl.d(this,
            "Already shown innaps: ${inAppRepositoryImpl.getShownInApps()}")
        return config.copy(inApps = config.inApps
            .filter { inApp -> validateInAppNotShown(inApp) })
    }

    private fun getConfigWithInAppsBeforeFirstPendingPreCheck(config: InAppConfig): InAppConfig {
        val calcultation =
            config.inApps.indexOfFirst { inApp -> inApp.targeting.preCheckTargeting() == SegmentationCheckResult.PENDING }
        return if (calcultation == -1) config else config.copy(
            inApps = config.inApps.subList(0,
                calcultation)
        )
    }

    override fun saveShownInApp(id: String) {
        inAppRepositoryImpl.saveShownInApp(id)
    }

    private suspend fun checkSegmentation(
        config: InAppConfig,
        segmentationCheckInApp: SegmentationCheckInApp,
    ): InApp? {
        return suspendCoroutine { continuation ->
            config.inApps.iterator().forEach { inApp ->
                if (validateSegmentation(inApp, segmentationCheckInApp.customerSegmentations)) {
                    continuation.resume(inApp)
                    return@suspendCoroutine
                }
            }
            continuation.resume(null)
        }
    }

    override fun sendInAppShown(inAppId: String) {
        inAppRepositoryImpl.sendInAppShown(inAppId)
    }

    override fun sendInAppClicked(inAppId: String) {
        inAppRepositoryImpl.sendInAppClicked(inAppId)
    }


    private fun validateInAppNotShown(inApp: InApp): Boolean {
        return inAppRepositoryImpl.getShownInApps().contains(inApp.id).not()
    }

    private fun validateSegmentation(
        inApp: InApp,
        customerSegmentationInAppList: List<CustomerSegmentationInApp>,
    ): Boolean {
        return inApp.targeting.getCustomerIsInTargeting(customerSegmentationInAppList)
    }

    override suspend fun fetchInAppConfig() {
        inAppRepositoryImpl.fetchInAppConfig()
    }
}