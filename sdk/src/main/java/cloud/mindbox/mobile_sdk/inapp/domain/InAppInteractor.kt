package cloud.mindbox.mobile_sdk.inapp.domain

import android.content.Context
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.inapp.data.InAppRepository
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.java.KoinJavaComponent.inject

internal class InAppInteractor {
    private val inAppRepository: InAppRepository by inject(InAppRepository::class.java)

    fun processEventAndConfig(
        context: Context,
        configuration: MindboxConfiguration,
    ): Flow<InAppType> {
        return inAppRepository.listenInAppConfig(context, configuration).map { pair ->
            when (val type = pair.first.form?.variants?.first()) {
                is PayloadDto.SimpleImage -> InAppType.SimpleImage(type.imageUrl!!,
                    type.redirectUrl!!,
                    type.intentPayload!!)
                else -> InAppType.NoInApp
            }
        }
    }

    fun fetchInAppConfig(context: Context, configuration: MindboxConfiguration) {
        inAppRepository.fetchInAppConfig(context, configuration)
    }

}