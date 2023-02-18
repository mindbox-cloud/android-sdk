package cloud.mindbox.mobile_sdk.inapp.data.repositories

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMessageMapper
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.MobileConfigSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.validators.InAppValidator
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppConfig
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import cloud.mindbox.mobile_sdk.monitoring.data.validators.MonitoringValidator
import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogRequest
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class MobileConfigRepositoryImpl(
    private val inAppMapper: InAppMessageMapper,
    private val mobileConfigSerializationManager: MobileConfigSerializationManager,
    private val context: Context,
    private val inAppValidator: InAppValidator,
    private val monitoringValidator: MonitoringValidator,
) : MobileConfigRepository {

    private val mutex = Mutex()

    override suspend fun fetchMobileConfig() {
        val configuration = DbManager.listenConfigurations().first()
        MindboxPreferences.inAppConfig = GatewayManager.fetchMobileConfig(
            context = context,
            configuration = configuration
        )
    }

    private fun listenInAppConfig(): Flow<InAppConfig?> {
        return MindboxPreferences.inAppConfigFlow.map { inAppConfigString ->
            mutex.withLock {
                MindboxLoggerImpl.d(
                    parent = this@MobileConfigRepositoryImpl,
                    message = "CachedConfig : $inAppConfigString"
                )
                val configBlank =
                    mobileConfigSerializationManager.deserializeToConfigDtoBlank(inAppConfigString)
                val filteredInApps = configBlank?.inApps
                    ?.filter { inAppDtoBlank ->
                        inAppValidator.validateInAppVersion(inAppDtoBlank)
                    }
                    ?.map { inAppDtoBlank ->
                        inAppMapper.mapToInAppDto(
                            inAppDtoBlank = inAppDtoBlank,
                            formDto = mobileConfigSerializationManager.deserializeToInAppFormDto(
                                inAppDtoBlank.form),
                            targetingDto = mobileConfigSerializationManager.deserializeToInAppTargetingDto(
                                inAppDtoBlank.targeting)
                        )
                    }?.filter { inAppDto ->
                        inAppValidator.validateInApp(inAppDto)
                    }
                val filteredMonitoring =
                    configBlank?.monitoring?.logs?.filter { logRequestDtoBlank ->
                        monitoringValidator.validateMonitoring(logRequestDtoBlank)
                    }?.map { logRequestDtoBlank ->
                        inAppMapper.mapToLogRequestDto(logRequestDtoBlank)
                    }
                val filteredConfig = InAppConfigResponse(
                    inApps = filteredInApps,
                    monitoring = filteredMonitoring
                )

                return@map inAppMapper.mapToInAppConfig(filteredConfig)
                    .also { inAppConfig ->
                        MindboxLoggerImpl.d(
                            parent = this@MobileConfigRepositoryImpl,
                            message = "Providing config: $inAppConfig"
                        )
                    }
            }
        }
    }

    override fun listenMonitoringSection(): Flow<List<LogRequest>?> {
        return listenInAppConfig().map { inAppConfig ->
            inAppConfig?.monitoring
        }
    }

    override fun listenInAppsSection(): Flow<List<InApp>?> {
        return listenInAppConfig().map { inAppConfig ->
            inAppConfig?.inApps
        }
    }

}