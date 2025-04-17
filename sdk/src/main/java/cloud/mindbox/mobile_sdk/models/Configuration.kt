package cloud.mindbox.mobile_sdk.models

import android.os.Build
import androidx.room.Entity
import androidx.room.PrimaryKey
import cloud.mindbox.mobile_sdk.BuildConfig
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.managers.DbManager.CONFIGURATION_TABLE_NAME

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

    internal constructor(mindboxConfiguration: MindboxConfiguration) : this(
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

internal fun Configuration.getUserAgent(): String {
    return String.format(
        MindboxRequest.VALUE_USER_AGENT,
        BuildConfig.VERSION_NAME,
        Build.VERSION.RELEASE,
        Build.MANUFACTURER,
        Build.MODEL,
        packageName,
        versionName,
        versionCode,
    )
}

internal fun Configuration.getShortUserAgent(): String {
    return "$packageName/$versionName-$versionCode mindbox.sdk/${BuildConfig.VERSION_NAME} "
}
