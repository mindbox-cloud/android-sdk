package cloud.mindbox.mobile_sdk

import android.content.Context
import cloud.mindbox.mobile_sdk_core.MindboxConfigurationInternal

/**
 * The configuration object used to initialize Mindbox SDK
 * The parameters are taken into account only during first initialization
 */
class MindboxConfiguration private constructor(
    previousInstallationId: String,
    previousDeviceUUID: String,
    endpointId: String,
    domain: String,
    packageName: String,
    versionName: String,
    versionCode: String,
    subscribeCustomerIfCreated: Boolean,
    shouldCreateCustomer: Boolean
) : MindboxConfigurationInternal(
    previousInstallationId,
    previousDeviceUUID,
    endpointId,
    domain,
    packageName,
    versionName,
    versionCode,
    subscribeCustomerIfCreated,
    shouldCreateCustomer
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
        shouldCreateCustomer = builder.shouldCreateCustomer
    )

    /**
     * A Builder for MindboxConfiguration
     */
    class Builder(
        private val context: Context,
        val domain: String,
        val endpointId: String
        ) : MindboxConfigurationInternal.BuilderInternal() {

        internal var previousInstallationId: String = ""
        internal var previousDeviceUUID: String = ""
        internal var subscribeCustomerIfCreated: Boolean = false
        internal var packageName: String = PLACEHOLDER_APP_PACKAGE_NAME
        internal var versionName: String = PLACEHOLDER_APP_VERSION_NAME
        internal var versionCode: String = PLACEHOLDER_APP_VERSION_CODE
        internal var shouldCreateCustomer: Boolean = true

        companion object {
            private const val PLACEHOLDER_APP_PACKAGE_NAME = "Unknown package name"
            private const val PLACEHOLDER_APP_VERSION_NAME = "Unknown version"
            private const val PLACEHOLDER_APP_VERSION_CODE = "?"
        }

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
         * Creates a new MindboxConfiguration.Builder.
         */
        fun build(): MindboxConfiguration {
            generateAppInfo(context)
            return MindboxConfiguration(this)
        }

    }

}