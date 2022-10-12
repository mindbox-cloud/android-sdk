package cloud.mindbox.mobile_sdk.inapp.domain

import android.content.Context
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.inapp.data.InAppRepositoryImpl
import cloud.mindbox.mobile_sdk.models.CustomerSegmentationInApp
import cloud.mindbox.mobile_sdk.models.InApp
import cloud.mindbox.mobile_sdk.models.InAppConfig
import cloud.mindbox.mobile_sdk.models.Payload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class InAppInteractor {
    private val inAppRepositoryImpl: InAppRepository by inject(InAppRepositoryImpl::class.java)
    private val interactorScope = CoroutineScope(Dispatchers.IO)

    fun processEventAndConfig(
        context: Context,
        configuration: MindboxConfiguration,
    ): Flow<InAppType> {
        return inAppRepositoryImpl.listenInAppConfig()
            //TODO add eventProcessing
            .combine(inAppRepositoryImpl.listenInAppEvents()) { config, event ->
                when (val type = checkSegmentation(context, configuration, config)) {
                    is Payload.SimpleImage -> InAppType.SimpleImage(type.imageUrl,
                        type.redirectUrl,
                        type.intentPayload)
                }
            }
    }

    private suspend fun checkSegmentation(
        context: Context,
        configuration: MindboxConfiguration,
        config: InAppConfig,
    ): Payload {
        return suspendCoroutine { continuation ->
            interactorScope.launch {
                inAppRepositoryImpl.fetchSegmentations(context,
                    configuration,
                    config).customerSegmentations.apply {
                    config.inApps.forEach { inApp ->
                        forEach { customerSegmentationInAppResponse ->
                            if (inApp.targeting == null || (validateSegmentation(inApp,
                                    customerSegmentationInAppResponse) && validateSdkVersion(inApp))
                            ) {
                                continuation.resume(inApp.form.variants.first())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun validateSegmentation(
        inApp: InApp,
        customerSegmentationInApp: CustomerSegmentationInApp,
    ): Boolean {
        return customerSegmentationInApp.segment.ids.externalId == inApp.targeting?.segment
    }

    private fun validateSdkVersion(inApp: InApp): Boolean {
        return (inApp.minVersion?.let { min -> min > 0 }
            ?: true) && (inApp.maxVersion?.let { max -> max < 0 } ?: true)
    }


    fun fetchInAppConfig(context: Context, configuration: MindboxConfiguration) {
        inAppRepositoryImpl.fetchInAppConfig(context, configuration)
    }

}