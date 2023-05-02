package cloud.mindbox.mobile_sdk.di

import androidx.room.Room
import cloud.mindbox.mobile_sdk.di.modules.ApiModule
import cloud.mindbox.mobile_sdk.di.modules.AppContextModule
import cloud.mindbox.mobile_sdk.di.modules.DataModule
import cloud.mindbox.mobile_sdk.di.modules.MonitoringModule
import cloud.mindbox.mobile_sdk.monitoring.MonitoringInteractorImpl
import cloud.mindbox.mobile_sdk.monitoring.data.checkers.LogStoringDataCheckerImpl
import cloud.mindbox.mobile_sdk.monitoring.data.mappers.MonitoringMapper
import cloud.mindbox.mobile_sdk.monitoring.data.repositories.MonitoringRepositoryImpl
import cloud.mindbox.mobile_sdk.monitoring.data.room.MonitoringDatabase
import cloud.mindbox.mobile_sdk.monitoring.data.room.dao.MonitoringDao
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.*
import cloud.mindbox.mobile_sdk.monitoring.domain.managers.LogRequestDataManagerImpl
import cloud.mindbox.mobile_sdk.monitoring.domain.managers.LogResponseDataManagerImpl
import java.io.File

internal const val monitoringDatabaseName = "MonitoringDatabase"

internal fun MonitoringModule(
    appContextModule: AppContextModule,
    apiModule: ApiModule,
    dataModule: DataModule,
): MonitoringModule = object : MonitoringModule,
    AppContextModule by appContextModule,
    ApiModule by apiModule,
    DataModule by dataModule {

    override val monitoringMapper: MonitoringMapper by lazy { MonitoringMapper() }

    override val monitoringRepository: MonitoringRepository by lazy {
        MonitoringRepositoryImpl(
            monitoringDao = monitoringDao,
            monitoringMapper = monitoringMapper,
            gson = gson,
            logStoringDataChecker = logStoringDataChecker,
            monitoringValidator = monitoringValidator,
            gatewayManager = gatewayManager
        )
    }

    override val logResponseDataManager: LogResponseDataManager by lazy {
        LogResponseDataManagerImpl()
    }

    override val logRequestDataManager: LogRequestDataManager by lazy {
        LogRequestDataManagerImpl()
    }

    override val logStoringDataChecker: LogStoringDataChecker by lazy {
        LogStoringDataCheckerImpl(
            File(
                "${
                    appContext.filesDir.absolutePath.replace(
                        "files",
                        "databases"
                    )
                }/$monitoringDatabaseName"
            )
        )
    }
    override val monitoringInteractor: MonitoringInteractor by lazy {
        MonitoringInteractorImpl(
            mobileConfigRepository = mobileConfigRepository,
            monitoringRepository = monitoringRepository,
            logResponseDataManager = logResponseDataManager,
            logRequestDataManager = logRequestDataManager
        )
    }
    override val monitoringDatabase: MonitoringDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            MonitoringDatabase::class.java,
            monitoringDatabaseName
        )
            .fallbackToDestructiveMigration()
            .addMigrations(MonitoringDatabase.MIGRATION_1_2)
            .build()
    }

    override val monitoringDao: MonitoringDao by lazy {
        monitoringDatabase.monitoringDao()
    }

}