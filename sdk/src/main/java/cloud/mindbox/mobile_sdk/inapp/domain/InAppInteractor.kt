package cloud.mindbox.mobile_sdk.inapp.domain

import android.content.Context
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import kotlinx.coroutines.flow.Flow

interface InAppInteractor {

    fun processEventAndConfig(
        context: Context,
        configuration: MindboxConfiguration,
    ): Flow<InAppType>

    fun saveShownInApp(id: String)

    fun sendInAppShown(context: Context, inAppId: String)

    fun sendInAppClicked(context: Context, inAppId: String)

    suspend fun fetchInAppConfig(context: Context, configuration: MindboxConfiguration)
}