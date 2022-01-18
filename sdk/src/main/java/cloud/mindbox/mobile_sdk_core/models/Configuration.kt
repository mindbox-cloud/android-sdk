package cloud.mindbox.mobile_sdk_core.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import cloud.mindbox.mobile_sdk_core.MindboxConfigurationInternal
import cloud.mindbox.mobile_sdk_core.managers.DbManager.CONFIGURATION_TABLE_NAME

@Entity(tableName = CONFIGURATION_TABLE_NAME)
internal data class Configuration(
    @PrimaryKey val configurationId: Long = 0L,
    val previousInstallationId: String,
    val previousDeviceUUID: String,
    val endpointId: String,
    val domain: String,
    val packageName: String,
    val versionName: String,
    val versionCode: String,
    val subscribeCustomerIfCreated: Boolean,
    val shouldCreateCustomer: Boolean
) {

    internal constructor(mindboxConfiguration: MindboxConfigurationInternal) : this(
        previousInstallationId = mindboxConfiguration.previousInstallationId,
        previousDeviceUUID = mindboxConfiguration.previousDeviceUUID,
        endpointId = mindboxConfiguration.endpointId,
        domain = mindboxConfiguration.domain,
        packageName = mindboxConfiguration.packageName,
        versionName = mindboxConfiguration.versionName,
        versionCode = mindboxConfiguration.versionCode,
        subscribeCustomerIfCreated = mindboxConfiguration.subscribeCustomerIfCreated,
        shouldCreateCustomer = mindboxConfiguration.shouldCreateCustomer
    )

}
