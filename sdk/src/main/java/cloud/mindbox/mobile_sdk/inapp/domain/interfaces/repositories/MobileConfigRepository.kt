package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories

import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.OperationName
import cloud.mindbox.mobile_sdk.inapp.domain.models.OperationSystemName
import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogRequest
import kotlinx.coroutines.flow.Flow

internal interface MobileConfigRepository {

    suspend fun fetchMobileConfig()

    suspend fun getInAppsSection(): List<InApp>

    fun listenMonitoringSection(): Flow<List<LogRequest>?>

    suspend fun getOperations(): Map<OperationName, OperationSystemName>

}