package cloud.mindbox.mobile_sdk_core

import android.content.Context
import android.os.Build
import cloud.mindbox.mobile_sdk_core.logger.MindboxLoggerInternal
import cloud.mindbox.mobile_sdk_core.managers.SharedPreferencesManager
import cloud.mindbox.mobile_sdk_core.repository.MindboxPreferences

open class MindboxConfigurationInternal(
    val previousInstallationId: String,
    val previousDeviceUUID: String,
    val endpointId: String,
    val domain: String,
    val packageName: String,
    val versionName: String,
    val versionCode: String,
    val subscribeCustomerIfCreated: Boolean,
    val shouldCreateCustomer: Boolean,
) {

    fun copy(
        previousInstallationId: String = this.previousInstallationId,
        previousDeviceUUID: String = this.previousDeviceUUID,
        endpointId: String = this.endpointId,
        domain: String = this.domain,
        packageName: String = this.packageName,
        versionName: String = this.versionName,
        versionCode: String = this.versionCode,
        subscribeCustomerIfCreated: Boolean = this.subscribeCustomerIfCreated,
        shouldCreateCustomer: Boolean = this.shouldCreateCustomer,
    ) = MindboxConfigurationInternal(
        previousInstallationId = previousInstallationId,
        previousDeviceUUID = previousDeviceUUID,
        endpointId = endpointId,
        domain = domain,
        packageName = packageName,
        versionName = versionName,
        versionCode = versionCode,
        subscribeCustomerIfCreated = subscribeCustomerIfCreated,
        shouldCreateCustomer = shouldCreateCustomer,
    )

    abstract class BuilderInternal {
        protected var packageNameInternal: String = PLACEHOLDER_APP_PACKAGE_NAME
        protected var versionNameInternal: String = PLACEHOLDER_APP_VERSION_NAME
        protected var versionCodeInternal: String = PLACEHOLDER_APP_VERSION_CODE

        companion object {
            private const val PLACEHOLDER_APP_PACKAGE_NAME = "Unknown package name"
            private const val PLACEHOLDER_APP_VERSION_NAME = "Unknown version"
            private const val PLACEHOLDER_APP_VERSION_CODE = "?"
        }

        protected fun generateAppInfo(context: Context) {
            try {
                val packageManager = context.packageManager
                val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
                packageNameInternal = packageInfo.packageName.trim()
                this.versionNameInternal = packageInfo.versionName?.trim() ?: PLACEHOLDER_APP_PACKAGE_NAME
                this.versionCodeInternal =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode.toString().trim()
                    } else {
                        packageInfo.versionCode.toString().trim()
                    }

                //need for scheduling and stopping one-time background service
                SharedPreferencesManager.with(context)
                MindboxPreferences.hostAppName = packageNameInternal

            } catch (e: Exception) {
                MindboxLoggerInternal.e(
                    this,
                    "Getting app info failed. Identified as an unknown application"
                )
            }
        }
    }

}