package cloud.mindbox.mobile_sdk.inapp.data.repositories

import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.getOrNull
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.managers.data_filler.DataManager
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.data.validators.*
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.MobileConfigSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.validators.InAppValidator
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppConfig
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTtlData
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.managers.MobileConfigSettingsManager
import cloud.mindbox.mobile_sdk.models.operation.response.*
import cloud.mindbox.mobile_sdk.monitoring.data.validators.MonitoringValidator
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    private val defaultDataManager: DataManager,
    private val ttlParametersValidator: TtlParametersValidator,
    private val inAppConfigTtlValidator: InAppConfigTtlValidator,
    private val sessionStorageManager: SessionStorageManager,
    private val slidingExpirationValidator: SlidingExpirationParametersValidator,
    private val mobileConfigSettingsManager: MobileConfigSettingsManager
) : MobileConfigRepository {

    private val mutex = Mutex()

    private val configState = MutableStateFlow<InAppConfig?>(null)

    init {
        Mindbox.mindboxScope.launch {
            MindboxPreferences.inAppConfigFlow
                .collectLatest { configString ->
                    processConfigUpdate(configString)
                }
        }
    }

    override suspend fun fetchMobileConfig() {
        val configuration = DbManager.listenConfigurations().first()
        MindboxPreferences.inAppConfig = gatewayManager.fetchMobileConfig(
            configuration = configuration
        )
        MindboxPreferences.inAppConfigUpdatedTime = System.currentTimeMillis()
    }

    private suspend fun processConfigUpdate(inAppConfigString: String) {
        mutex.withLock {
            this@MobileConfigRepositoryImpl.mindboxLogD(
                message = "CachedConfig : $inAppConfigString"
            )
            val configBlank =
                mobileConfigSerializationManager.deserializeToConfigDtoBlank(inAppConfigString)

            val filteredConfig = InAppConfigResponse(
                inApps = runCatching { getInApps(configBlank) }.getOrNull {
                    mindboxLogW("Unable to get inApps $it")
                },
                monitoring = runCatching { getMonitoring(configBlank) }.getOrNull {
                    mindboxLogW("Unable to get logs $it")
                },
                settings = runCatching { getSettings(configBlank) }.getOrNull {
                    mindboxLogW("Unable to get settings $it")
                },
                abtests = runCatching { getABTests(configBlank) }.getOrNull {
                    mindboxLogW("Unable to get abtests $it")
                },
            )

            var updatedInAppConfig = inAppMapper.mapToInAppConfig(filteredConfig)
            mobileConfigSettingsManager.saveSessionTime(config = filteredConfig)
            configState.value = updatedInAppConfig
            mindboxLogI(message = "Providing config: $updatedInAppConfig")
        }
    }

    override suspend fun getMonitoringSection() = getConfig().monitoring

    override suspend fun getOperations() = getConfig().operations

    override suspend fun getInAppsSection() = getConfig().inApps

    override suspend fun getABTests() = getConfig().abtests

    override fun resetCurrentConfig() {
        configState.value = null
    }

    private fun getInApps(configBlank: InAppConfigResponseBlank?): List<InAppDto>? {
        val isValidConfig = inAppConfigTtlValidator.isValid(
            InAppTtlData(
                ttl = getInAppTtl(configBlank),
                shouldCheckInAppTtl = sessionStorageManager.configFetchingError
            )
        )

        if (!isValidConfig) return emptyList()

        return configBlank?.inApps
            ?.filter { inAppDtoBlank ->
                inAppValidator.validateInAppVersion(inAppDtoBlank)
            }
            ?.map { inAppDtoBlank ->
                inAppMapper.mapToInAppDto(
                    inAppDtoBlank = inAppDtoBlank,
                    formDto = defaultDataManager.fillFormData(
                        mobileConfigSerializationManager.deserializeToInAppFormDto(
                            inAppDtoBlank.form
                        )
                    ),
                    frequencyDto = defaultDataManager.fillFrequencyData(mobileConfigSerializationManager.deserializeToFrequencyDto(inAppDtoBlank.frequency)),
                    targetingDto = mobileConfigSerializationManager.deserializeToInAppTargetingDto(
                        inAppDtoBlank.targeting
                    )
                )
            }?.filter { inAppDto ->
                inAppValidator.validateInApp(inAppDto)
            }
    }

    private fun getMonitoring(configBlank: InAppConfigResponseBlank?): List<LogRequestDto>? =
        configBlank?.monitoring?.logs?.filter { logRequestDtoBlank ->
            monitoringValidator.validateLogRequestDtoBlank(logRequestDtoBlank)
        }?.map { logRequestDtoBlank ->
            inAppMapper.mapToLogRequestDto(logRequestDtoBlank)
        }

    private fun getSettings(configBlank: InAppConfigResponseBlank?): SettingsDto {
        val operations = configBlank?.settings?.operations
            ?.filter { (name, operation) ->
                operationNameValidator.isValid(name) &&
                    operationValidator.isValid(operation)
            }?.map { (name, operation) ->
                name!! to OperationDto(operation!!.systemName)
            }?.toMap()
            ?: emptyMap()

        val ttl = runCatching { getInAppTtl(configBlank) }.getOrElse {
            mindboxLogW("Unable to get InAppTtl settings $it")
            null
        }

        val slidingExpiration = runCatching { getConfigSession(configBlank) }.getOrNull {
            mindboxLogW("Unable to get slidingExpiration settings $it")
        }

        return SettingsDto(operations, ttl, slidingExpiration)
    }

    private fun getInAppTtl(configBlank: InAppConfigResponseBlank?): TtlDto? =
        try {
            configBlank?.settings?.ttl?.takeIf { ttlParametersDtoBlank ->
                ttlParametersValidator.isValid(ttlParametersDtoBlank)
            }?.let { ttlParametersDtoBlank ->
                inAppMapper.mapToTtlDto(ttlParametersDtoBlank)
            }
        } catch (e: java.lang.Exception) {
            mindboxLogE("Error parse inapps ttl", e)
            null
        }

    private fun getConfigSession(configBlank: InAppConfigResponseBlank?): SlidingExpirationDto? =
        try {
            configBlank?.settings?.slidingExpiration?.takeIf { slidingExpirationDtoBlank ->
                slidingExpirationValidator.isValid(slidingExpirationDtoBlank)
            }?.let { slidingExpirationDtoBlank ->
                inAppMapper.mapToSlidingExpiration(slidingExpirationDtoBlank)
            }
        } catch (e: Exception) {
            mindboxLogE("Error parse config session time", e)
            null
        }

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

    private suspend fun getConfig(): InAppConfig {
        return configState
            .filterNotNull()
            .first()
    }
}
