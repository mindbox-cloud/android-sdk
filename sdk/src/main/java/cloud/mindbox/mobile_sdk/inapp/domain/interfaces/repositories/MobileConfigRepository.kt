package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories

import cloud.mindbox.mobile_sdk.inapp.domain.models.ABTest
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.OperationName
import cloud.mindbox.mobile_sdk.inapp.domain.models.OperationSystemName
import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogRequest

internal interface MobileConfigRepository {

    suspend fun fetchMobileConfig()

    suspend fun getInAppsSection(): List<InApp>

    suspend fun getMonitoringSection(): List<LogRequest>

    suspend fun getOperations(): Map<OperationName, OperationSystemName>

    suspend fun getABTests(): List<ABTest>
}
