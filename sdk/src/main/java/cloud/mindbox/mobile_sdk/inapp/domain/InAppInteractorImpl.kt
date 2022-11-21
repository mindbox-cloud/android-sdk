package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.inapp.data.InAppRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManager
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.*
import com.android.volley.VolleyError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import org.koin.java.KoinJavaComponent.inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class InAppInteractorImpl : InAppInteractor {
    private val inAppRepositoryImpl: InAppRepository by inject(InAppRepositoryImpl::class.java)

    override fun processEventAndConfig(
        configuration: MindboxConfiguration,
    ): Flow<InAppType> {
        return inAppRepositoryImpl.listenInAppConfig()
            //TODO add eventProcessing
            .combine(inAppRepositoryImpl.listenInAppEvents()
                .filter { inAppEventType -> inAppEventType is InAppEventType.AppStartup }) { config, event ->
                val filteredConfig = prefilterConfig(config)
                val filteredConfigWithTargeting = getConfigWithTargeting(filteredConfig)
                val inAppsWithoutTargeting =
                    filteredConfig.inApps.subtract(filteredConfigWithTargeting.inApps.toSet())
                val inApp = if (inAppsWithoutTargeting.isNotEmpty()) {
                    inAppsWithoutTargeting.first()
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

                when (val type = inApp?.form?.variants?.first()) {
                    is Payload.SimpleImage -> InAppType.SimpleImage(inAppId = inApp.id,
                        imageUrl = type.imageUrl,
                        redirectUrl = type.redirectUrl,
                        intentData = type.intentPayload)
                    else -> null
                }
            }.filterNotNull()
    }

    override fun prefilterConfig(config: InAppConfig): InAppConfig {
        return config.copy(inApps = config.inApps.filter { inApp -> validateInAppVersion(inApp) }
            .filter { inApp -> validateInAppNotShown(inApp) && validateInAppTargeting(inApp) })
    }

    override fun validateInAppTargeting(inApp: InApp): Boolean {
        return when {
            (inApp.targeting == null) -> {
                false
            }
            (inApp.targeting.segmentation == null && inApp.targeting.segment != null) -> {
                false
            }
            (inApp.targeting.segmentation != null && inApp.targeting.segment == null) -> {
                false
            }
            else -> {
                true
            }
        }
    }

    override fun getConfigWithTargeting(config: InAppConfig): InAppConfig {
        return config.copy(inApps = config.inApps.filter { inApp -> inApp.targeting?.segmentation != null && inApp.targeting.segment != null })
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


    private fun validateInAppNotShown(inApp: InApp): Boolean {
        return inAppRepositoryImpl.getShownInApps().contains(inApp.id).not()
    }

    override fun validateSegmentation(
        inApp: InApp,
        customerSegmentationInApp: CustomerSegmentationInApp,
    ): Boolean {
        return if (customerSegmentationInApp.segment == null) {
            false
        } else {
            inApp.targeting?.segment == customerSegmentationInApp.segment.ids?.externalId
        }
    }

    override fun validateInAppVersion(inApp: InApp): Boolean {
        return ((inApp.minVersion?.let { min -> min <= InAppMessageManager.CURRENT_IN_APP_VERSION }
            ?: true) && (inApp.maxVersion?.let { max -> max >= InAppMessageManager.CURRENT_IN_APP_VERSION }
            ?: true))
    }


    override suspend fun fetchInAppConfig(configuration: MindboxConfiguration) {
        inAppRepositoryImpl.fetchInAppConfig(configuration)
    }
}