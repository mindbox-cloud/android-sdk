package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.generateRandomUuid
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal interface DeviceUuidResolver {
    suspend fun resolve(context: Context): String
}

internal class DeviceUuidResolverImpl(
    private val pushServiceHandlers: () -> List<PushServiceHandler> = { Mindbox.pushServiceHandlers },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DeviceUuidResolver {

    private val mutex = Mutex()

    override suspend fun resolve(context: Context): String = mutex.withLock {
        MindboxPreferences.deviceUuid.ifEmpty {
            assign(context).also { MindboxPreferences.deviceUuid = it }
        }
    }

    private suspend fun assign(context: Context): String {
        if (!MindboxPreferences.shouldCollectTrackingIds) {
            mindboxLogI("Tracking ids are turned off, deviceUuid is generated from random instead of the advertising id")
            return generateRandomUuid()
        }
        anonymizerDomain()?.let { domain ->
            mindboxLogI("Operations go through operationsDomain $domain, deviceUuid is generated from random instead of the advertising id")
            return generateRandomUuid()
        }
        return withContext(ioDispatcher) {
            pushServiceHandlers().firstNotNullOfOrNull { handler -> handler.getAdsIdentification(context) }
        } ?: generateRandomUuid()
    }
}
