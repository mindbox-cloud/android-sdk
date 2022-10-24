package cloud.mindbox.mobile_sdk.inapp.domain

import android.content.Context
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.inapp.data.InAppRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManager
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
                val inApp =
                    checkSegmentation(context, configuration, config)
                when (val type = inApp.form.variants.first()) {
                    is Payload.SimpleImage -> InAppType.SimpleImage(inAppId = inApp.id,
                        imageUrl = type.imageUrl,
                        redirectUrl = type.redirectUrl,
                        intentData = type.intentPayload)
                }
            }
    }

    private fun saveShownInApp(id: String) {
        inAppRepositoryImpl.saveShownInApp(id)
    }

    private suspend fun checkSegmentation(
        context: Context,
        configuration: MindboxConfiguration,
        config: InAppConfig,
    ): InApp {
        return suspendCoroutine { continuation ->
            interactorScope.launch {
                inAppRepositoryImpl.fetchSegmentations(context,
                    configuration,
                    config).customerSegmentations.apply {
                    config.inApps.forEach { inApp ->
                        forEach { customerSegmentationInAppResponse ->
                            if ((inApp.targeting == null || validateSegmentation(inApp,
                                    customerSegmentationInAppResponse) && validateInAppVersion(inApp) && validateInAppNotShown(
                                    inApp))
                            ) {
                                saveShownInApp(inApp.id)
                                continuation.resume(inApp)
                                return@apply
                            }
                        }
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
        return customerSegmentationInApp.segment.ids.externalId == inApp.targeting?.segment
    }

    private fun validateInAppVersion(inApp: InApp): Boolean {
        return ((inApp.minVersion?.let { min -> min <= InAppMessageManager.CURRENT_IN_APP_VERSION }
            ?: true) && (inApp.maxVersion?.let { max -> max >= InAppMessageManager.CURRENT_IN_APP_VERSION }
            ?: true))
    }


    fun fetchInAppConfig(context: Context, configuration: MindboxConfiguration) {
        inAppRepositoryImpl.fetchInAppConfig(context, configuration)
    }

}