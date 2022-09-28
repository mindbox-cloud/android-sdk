package cloud.mindbox.mobile_sdk.inapp.domain

import android.content.Context
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.inapp.data.InAppRepositoryImpl
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
                    else -> InAppType.NoInApp
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
                    config).customerSegmentations.forEach { customerSegmentationInAppResponse ->
                    config.inApps.forEach { inApp ->
                        if (inApp.targeting.segment == customerSegmentationInAppResponse.segment.ids.externalId || customerSegmentationInAppResponse.segment.ids.externalId == "") {
                            continuation.resume(inApp.form.variants.first())
                        }
                    }
                }
            }
        }
    }


    fun fetchInAppConfig(context: Context, configuration: MindboxConfiguration) {
        inAppRepositoryImpl.fetchInAppConfig(context, configuration)
    }

}