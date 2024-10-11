package cloud.mindbox.mobile_sdk

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.SharedPreferencesManager
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences

/**
 * The configuration object used to initialize Mindbox SDK
 * The parameters are taken into account only during first initialization
 */
class MindboxConfiguration private constructor(
    internal val previousInstallationId: String,
    internal val previousDeviceUUID: String,
    internal val endpointId: String,
    internal val domain: String,
    internal val packageName: String,
    internal val versionName: String,
    internal val versionCode: String,
    internal val subscribeCustomerIfCreated: Boolean,
    internal val shouldCreateCustomer: Boolean,
    internal val uuidDebugEnabled: Boolean,
) {

    constructor(builder: Builder) : this(
        previousInstallationId = builder.previousInstallationId,
        previousDeviceUUID = builder.previousDeviceUUID,
        endpointId = builder.endpointId,
        domain = builder.domain,
        packageName = builder.packageName,
        versionName = builder.versionName,
        versionCode = builder.versionCode,
        subscribeCustomerIfCreated = builder.subscribeCustomerIfCreated,
        shouldCreateCustomer = builder.shouldCreateCustomer,
        uuidDebugEnabled = builder.uuidDebugEnabled,
    )

    internal fun copy(
        previousInstallationId: String = this.previousInstallationId,
        previousDeviceUUID: String = this.previousDeviceUUID,
        endpointId: String = this.endpointId,
        domain: String = this.domain,
        packageName: String = this.packageName,
        versionName: String = this.versionName,
        versionCode: String = this.versionCode,
        subscribeCustomerIfCreated: Boolean = this.subscribeCustomerIfCreated,
        shouldCreateCustomer: Boolean = this.shouldCreateCustomer,
        uuidDebugEnabled: Boolean = this.uuidDebugEnabled,
    ) = MindboxConfiguration(
        previousInstallationId = previousInstallationId,
        previousDeviceUUID = previousDeviceUUID,
        endpointId = endpointId,
        domain = domain,
        packageName = packageName,
        versionName = versionName,
        versionCode = versionCode,
        subscribeCustomerIfCreated = subscribeCustomerIfCreated,
        shouldCreateCustomer = shouldCreateCustomer,
        uuidDebugEnabled = uuidDebugEnabled,
    )

    override fun toString(): String {
        return "MindboxConfiguration(previousInstallationId = $previousInstallationId, " +
            "previousDeviceUUID = $previousDeviceUUID, " +
            "endpointId = $endpointId, " +
            "domain = $domain, " +
            "packageName = $packageName, " +
            "versionName = $versionName, " +
            "versionCode = $versionCode, " +
            "subscribeCustomerIfCreated = $subscribeCustomerIfCreated, " +
            "shouldCreateCustomer = $shouldCreateCustomer, " +
            "uuidDebugEnabled = $uuidDebugEnabled)"
    }

    /**
     * A Builder for MindboxConfiguration
     */
    class Builder(private val context: Context, val domain: String, val endpointId: String) {

        companion object {

            private const val PLACEHOLDER_APP_PACKAGE_NAME = "Unknown package name"
            private const val PLACEHOLDER_APP_VERSION_NAME = "Unknown version"
            private const val PLACEHOLDER_APP_VERSION_CODE = "?"
        }

        internal var previousInstallationId: String = ""
        internal var previousDeviceUUID: String = ""
        internal var subscribeCustomerIfCreated: Boolean = false
        internal var packageName: String = PLACEHOLDER_APP_PACKAGE_NAME
        internal var versionName: String = PLACEHOLDER_APP_VERSION_NAME
        internal var versionCode: String = PLACEHOLDER_APP_VERSION_CODE
        internal var shouldCreateCustomer: Boolean = true
        internal var uuidDebugEnabled: Boolean = true

        /**
         * Specifies deviceUUID for Mindbox
         *
         * @param previousDeviceUUID - deprecate - old device id which was used to find a customer by the device in our DB
         */
        fun setPreviousDeviceUuid(previousDeviceUUID: String): Builder {
            this.previousDeviceUUID = previousDeviceUUID
            return this
        }

        /**
         * Specifies installationId for Mindbox
         *
         * @param previousInstallationId - deprecate - old id which was used to send mobile push
         */
        fun setPreviousInstallationId(previousInstallationId: String): Builder {
            this.previousInstallationId = previousInstallationId
            return this
        }

        /**
         * Specifies subscribeCustomerIfCreated for Mindbox
         *
         * @param subscribe - flag which determines subscription status of the user
         */
        fun subscribeCustomerIfCreated(subscribe: Boolean): Builder {
            this.subscribeCustomerIfCreated = subscribe
            return this
        }

        /**
         * Specifies shouldCreateCustomer for Mindbox. Usable only during first initialisation
         *
         * @param shouldCreateCustomer - flag which determines create or not anonymous users.
         * Default value is true.
         */
        fun shouldCreateCustomer(shouldCreateCustomer: Boolean): Builder {
            this.shouldCreateCustomer = shouldCreateCustomer
            return this
        }

        /**
         * Specifies if Mindbox UUID copy to clipboard functionality is enabled. If enabled - UUID
         * can be copied to clipboard by minimizing and maximizing your app 5 times in 10 seconds
         *
         * @param uuidDebugEnabled - flag which determines if Mindbox UUID copy to clipboard
         * functionality is enabled.
         * Default value is true.
         */
        fun uuidDebugEnabled(uuidDebugEnabled: Boolean): Builder {
            this.uuidDebugEnabled = uuidDebugEnabled
            return this
        }

        /**
         * Creates a new MindboxConfiguration.Builder.
         */
        fun build(): MindboxConfiguration {
            generateAppInfo(context)
            return MindboxConfiguration(this)
        }

        private fun generateAppInfo(context: Context) {
            try {
                val packageManager = context.packageManager
                //noinspection deprecation
                val packageInfo = packageManager.getPackageInfoCompat(context, 0)
                packageName = packageInfo.packageName.trim()
                this.versionName = packageInfo.versionName?.trim()
                    ?: PLACEHOLDER_APP_PACKAGE_NAME
                this.versionCode =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode.toString().trim()
                    } else {
                        PackageInfoCompat.getLongVersionCode(packageInfo).toString().trim()
                    }

                // need for scheduling and stopping one-time background service
                SharedPreferencesManager.with(context)
                MindboxPreferences.hostAppName = packageName
            } catch (e: Exception) {
                MindboxLoggerImpl.e(
                    this,
                    "Getting app info failed. Identified as an unknown application",
                )
            }
        }
    }
}
