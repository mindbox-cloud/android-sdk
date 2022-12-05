package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.MindboxConfiguration
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

internal class InAppInteractorImpl(private val inAppRepositoryImpl: InAppRepository) :
    InAppInteractor {

    override fun processEventAndConfig(
        configuration: MindboxConfiguration,
    ): Flow<InAppType> {
        return inAppRepositoryImpl.listenInAppConfig().filterNotNull()
            //TODO add eventProcessing
            .combine(inAppRepositoryImpl.listenInAppEvents()
                .filter { inAppEventType ->
                    MindboxLoggerImpl.d(this, "Event triggered: $inAppEventType")
                    inAppEventType is InAppEventType.AppStartup
                }) { config, event ->
                val inApp = chooseInAppToShow(config,
                    configuration)
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

    override suspend fun chooseInAppToShow(
        config: InAppConfig,
        configuration: MindboxConfiguration,
    ): InApp? {
        val filteredConfig = prefilterConfig(config)
        MindboxLoggerImpl.d(this,
            "Filtered config has ${filteredConfig.inApps.size} inapps")
        val filteredConfigWithTargeting = getConfigWithTargeting(filteredConfig)
        val inAppsWithoutTargeting =
            filteredConfig.inApps.subtract(filteredConfigWithTargeting.inApps.toSet())
        return if (inAppsWithoutTargeting.isNotEmpty()) {
            inAppsWithoutTargeting.first().also {
                MindboxLoggerImpl.d(this,
                    "Inapp without targeting found: ${it.id}")
            }
        } else if (filteredConfigWithTargeting.inApps.isNotEmpty()) {
            runCatching {
                checkSegmentation(filteredConfig,
                    inAppRepositoryImpl.fetchSegmentations(
                        configuration,
                        filteredConfigWithTargeting))
            }.getOrElse { throwable ->
                if (throwable is VolleyError) {
                    MindboxLoggerImpl.e("", throwable.message ?: "", throwable)
                    null
                } else {
                    throw throwable
                }
            }
        } else {
            null
        }
    }

    override fun prefilterConfig(config: InAppConfig): InAppConfig {
        MindboxLoggerImpl.d(this,
            "Already shown innaps: ${inAppRepositoryImpl.getShownInApps()}")
        return config.copy(inApps = config.inApps
            .filter { inApp -> validateInAppNotShown(inApp)})
    }

    override fun getConfigWithTargeting(config: InAppConfig): InAppConfig {
        return config.copy(
            inApps = config.inApps.filter { inApp ->
                inApp.targeting !is TreeTargeting.TrueNode
            }
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
                segmentationCheckInApp.customerSegmentations.iterator()
                    .forEach { customerSegmentationInAppResponse ->
                        if (validateSegmentation(inApp, customerSegmentationInAppResponse)) {
                            continuation.resume(inApp)
                            return@suspendCoroutine
                        }
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


    override fun validateInAppNotShown(inApp: InApp): Boolean {
        return inAppRepositoryImpl.getShownInApps().contains(inApp.id).not()
    }

    override fun validateSegmentation(
        inApp: InApp,
        customerSegmentationInApp: CustomerSegmentationInApp,
    ): Boolean {
        return if (customerSegmentationInApp.segment == null) {
            false
        } else {
            inApp.targeting?.getCustomerIsInTargeting(customerSegmentationInApp.segment.ids?.externalId)
                ?: false
        }
    }

    override suspend fun fetchInAppConfig(configuration: MindboxConfiguration) {
        inAppRepositoryImpl.fetchInAppConfig(configuration)
    }
}