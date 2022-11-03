package cloud.mindbox.mobile_sdk.inapp.domain

import android.content.Context
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.inapp.data.InAppRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManager
import cloud.mindbox.mobile_sdk.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.koin.java.KoinJavaComponent.inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class InAppInteractor {
    private val inAppRepositoryImpl: InAppRepository by inject(InAppRepositoryImpl::class.java)

    fun processEventAndConfig(
        context: Context,
        configuration: MindboxConfiguration,
    ): Flow<InAppType> {
        return inAppRepositoryImpl.listenInAppConfig()
            //TODO add eventProcessing
            .combine(inAppRepositoryImpl.listenInAppEvents()) { config, event ->
                val inApp =
                    checkSegmentation(config, inAppRepositoryImpl.fetchSegmentations(context,
                        configuration,
                        config))
                when (val type = inApp.form.variants.first()) {
                    is Payload.SimpleImage -> InAppType.SimpleImage(inAppId = inApp.id,
                        imageUrl = type.imageUrl,
                        redirectUrl = type.redirectUrl,
                        intentData = type.intentPayload)
                }
            }
    }

    fun saveShownInApp(id: String) {
        inAppRepositoryImpl.saveShownInApp(id)
    }


    private suspend fun checkSegmentation(
        config: InAppConfig,
        segmentationCheckInApp: SegmentationCheckInApp,
    ): InApp {
        return suspendCoroutine { continuation ->
            config.inApps.forEach { inApp ->
                segmentationCheckInApp.customerSegmentations.forEach { customerSegmentationInAppResponse ->
                    if ((validateSegmentation(inApp,
                            customerSegmentationInAppResponse) && validateInAppVersion(inApp) && validateInAppNotShown(
                            inApp))
                    ) {
                        continuation.resume(inApp)
                        return@suspendCoroutine
                    }
                }
            }
        }
    }

    fun sendInAppShown(context: Context, inAppId: String) {
        inAppRepositoryImpl.sendInAppShown(context, inAppId)
    }

    fun sendInAppClicked(context: Context, inAppId: String) {
        inAppRepositoryImpl.sendInAppClicked(context, inAppId)
    }


    private fun validateInAppNotShown(inApp: InApp): Boolean {
        return inAppRepositoryImpl.getShownInApps().contains(inApp.id).not()
    }

    private fun validateSegmentation(
        inApp: InApp,
        customerSegmentationInApp: CustomerSegmentationInApp,
    ): Boolean {
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
            (inApp.targeting.segmentation == null && inApp.targeting.segment == null) -> {
                true
            }
            (customerSegmentationInApp.segment == null) -> {
                false
            }
            else -> {
                inApp.targeting.segment == customerSegmentationInApp.segment.ids?.externalId
            }
        }
    }

    private fun validateInAppVersion(inApp: InApp): Boolean {
        return ((inApp.minVersion?.let { min -> min <= InAppMessageManager.CURRENT_IN_APP_VERSION }
            ?: true) && (inApp.maxVersion?.let { max -> max >= InAppMessageManager.CURRENT_IN_APP_VERSION }
            ?: true))
    }


    suspend fun fetchInAppConfig(context: Context, configuration: MindboxConfiguration) {
        inAppRepositoryImpl.fetchInAppConfig(context, configuration)
    }

}