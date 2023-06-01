package cloud.mindbox.mobile_sdk.inapp.data.repositories

import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.data.validators.ABTestValidator
import cloud.mindbox.mobile_sdk.inapp.data.validators.OperationNameValidator
import cloud.mindbox.mobile_sdk.inapp.data.validators.OperationValidator
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.MobileConfigSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.validators.InAppValidator
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.models.operation.response.*
import cloud.mindbox.mobile_sdk.monitoring.data.validators.MonitoringValidator
import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogRequest
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class MobileConfigRepositoryImpl(
    private val inAppMapper: InAppMapper,
    private val mobileConfigSerializationManager: MobileConfigSerializationManager,
    private val inAppValidator: InAppValidator,
    private val monitoringValidator: MonitoringValidator,
    private val abTestValidator: ABTestValidator,
    private val operationNameValidator: OperationNameValidator,
    private val operationValidator: OperationValidator,
    private val gatewayManager: GatewayManager,
) : MobileConfigRepository {

    private val mutex = Mutex()

    private var inApps: List<InApp>? = null
    private var operations: Map<OperationName, OperationSystemName>? = null
    private var abtests: List<ABTest>? = null

    override suspend fun fetchMobileConfig() {
        val configuration = DbManager.listenConfigurations().first()
        MindboxPreferences.inAppConfig = gatewayManager.fetchMobileConfig(
            configuration = configuration
        )
    }

    private fun listenInAppConfig(): Flow<InAppConfig?> {
        return MindboxPreferences.inAppConfigFlow.map { inAppConfigString ->
            mutex.withLock {
                this@MobileConfigRepositoryImpl.mindboxLogD(
                    message = "CachedConfig : $inAppConfigString"
                )
                val configBlank =
                    mobileConfigSerializationManager.deserializeToConfigDtoBlank(inAppConfigString)

                val filteredConfig = InAppConfigResponse(
                    inApps = getInApps(configBlank),
                    monitoring = getMonitoring(configBlank),
                    settings = getSettings(configBlank),
                    abtests = getABTests(configBlank),
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

    private fun getInApps(configBlank: InAppConfigResponseBlank?): List<InAppDto>? =
        configBlank?.inApps
            ?.filter { inAppDtoBlank ->
                inAppValidator.validateInAppVersion(inAppDtoBlank)
            }
            ?.map { inAppDtoBlank ->
                inAppMapper.mapToInAppDto(
                    inAppDtoBlank = inAppDtoBlank,
                    formDto = mobileConfigSerializationManager.deserializeToInAppFormDto(
                        inAppDtoBlank.form
                    ),
                    targetingDto = mobileConfigSerializationManager.deserializeToInAppTargetingDto(
                        inAppDtoBlank.targeting
                    )
                )
            }?.filter { inAppDto ->
                inAppValidator.validateInApp(inAppDto)
            }

    private fun getMonitoring(configBlank: InAppConfigResponseBlank?): List<LogRequestDto>? =
        configBlank?.monitoring?.logs?.filter { logRequestDtoBlank ->
            monitoringValidator.validateLogRequestDtoBlank(logRequestDtoBlank)
        }?.map { logRequestDtoBlank ->
            inAppMapper.mapToLogRequestDto(logRequestDtoBlank)
        }

    private fun getSettings(configBlank: InAppConfigResponseBlank?): Map<String, OperationDto> =
        configBlank?.settings?.operations
            ?.filter { (name, operation) ->
                operationNameValidator.isValid(name)
                        && operationValidator.isValid(operation)
            }?.map { (name, operation) ->
                name!! to OperationDto(operation!!.systemName!!)
            }?.toMap()
            ?: emptyMap()


    private fun getABTests(configBlank: InAppConfigResponseBlank?): List<ABTestDto> {
        return try {
            if (configBlank?.abtests == null) return listOf()

            return configBlank.abtests.takeIf { abtests ->
                abtests.all { abTestValidator.isValid(it) }
            } ?: listOf()
        } catch (e: Exception) {
            mindboxLogE("Error parse abtests", e)
            listOf()
        }
    }

    override fun listenMonitoringSection(): Flow<List<LogRequest>?> {
        return listenInAppConfig().map { inAppConfig ->
            inAppConfig?.monitoring
        }
    }

    override suspend fun getOperations(): Map<OperationName, OperationSystemName> {
        return operations ?: run {
            val operationsMap: Map<OperationName, OperationSystemName> =
                listenInAppConfig().map { inAppConfig ->
                    inAppConfig?.operations
                }.first() ?: mapOf()
            operations = operationsMap
            operationsMap
        }
    }

    override suspend fun getInAppsSection(): List<InApp> {
        return inApps ?: run {
            val inAppList: List<InApp> = listenInAppConfig().map { inAppConfig ->
                inAppConfig?.inApps
            }.first() ?: listOf()
            inApps = inAppList
            inAppList
        }
    }

    override suspend fun getABTests(): List<ABTest> {
        return abtests ?: run {
            val list: List<ABTest> = listenInAppConfig().map { inAppConfig ->
                inAppConfig?.abtests
            }.first() ?: listOf()
            abtests = list
            list
        }
    }
}