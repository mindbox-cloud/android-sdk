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
    val shouldCreateCustomer: Boolean
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
        shouldCreateCustomer: Boolean = this.shouldCreateCustomer
    ) = MindboxConfigurationInternal(
        previousInstallationId = previousInstallationId,
        previousDeviceUUID = previousDeviceUUID,
        endpointId = endpointId,
        domain = domain,
        packageName = packageName,
        versionName = versionName,
        versionCode = versionCode,
        subscribeCustomerIfCreated = subscribeCustomerIfCreated,
        shouldCreateCustomer = shouldCreateCustomer
    )

    abstract class BuilderInternal {
        private var packageName: String = PLACEHOLDER_APP_PACKAGE_NAME
        private var versionName: String = PLACEHOLDER_APP_VERSION_NAME
        private var versionCode: String = PLACEHOLDER_APP_VERSION_CODE

        companion object {
            private const val PLACEHOLDER_APP_PACKAGE_NAME = "Unknown package name"
            private const val PLACEHOLDER_APP_VERSION_NAME = "Unknown version"
            private const val PLACEHOLDER_APP_VERSION_CODE = "?"
        }

        protected fun generateAppInfo(context: Context) {
            try {
                val packageManager = context.packageManager
                val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
                packageName = packageInfo.packageName.trim()
                this.versionName = packageInfo.versionName?.trim() ?: PLACEHOLDER_APP_PACKAGE_NAME
                this.versionCode =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode.toString().trim()
                    } else {
                        packageInfo.versionCode.toString().trim()
                    }

                //need for scheduling and stopping one-time background service
                SharedPreferencesManager.with(context)
                MindboxPreferences.hostAppName = packageName

            } catch (e: Exception) {
                MindboxLoggerInternal.e(
                    this,
                    "Getting app info failed. Identified as an unknown application"
                )
            }
        }
    }

}