package cloud.mindbox.mobile_sdk

import androidx.core.util.PatternsCompat
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler

internal object SdkValidation {

    enum class Error(val critical: Boolean, val message: String) {

        EMPTY_DOMAIN(true, "Domain must not be empty"),
        INVALID_FORMAT_DOMAIN(true, "The domain must not start with https:// and must not end with /"),
        INVALID_DOMAIN(true, "The domain is not valid"),
        EMPTY_ENDPOINT(true, "Endpoint must not be empty"),
        INVALID_DEVICE_ID(false, "Invalid previous device UUID format"),
        INVALID_INSTALLATION_ID(false, "Invalid UUID format of previous installationId");

        override fun toString() = "$name(critical=$critical, message=$message)"
    }

    fun validateConfiguration(
        domain: String,
        endpointId: String,
        previousDeviceUUID: String,
        previousInstallationId: String
    ) = LoggingExceptionHandler.runCatching(defaultValue = listOf()) {
        mutableListOf<Error>().apply {
            when {
                domain.isBlank() -> add(Error.EMPTY_DOMAIN)
                !isDomainWellFormatted(domain) -> add(Error.INVALID_FORMAT_DOMAIN)
                !isDomainValid(domain) -> add(Error.INVALID_DOMAIN)
            }

            if (endpointId.isBlank()) {
                add(Error.EMPTY_ENDPOINT)
            }

            if (previousDeviceUUID.isNotEmpty() && !previousDeviceUUID.isUuid()) {
                add(Error.INVALID_DEVICE_ID)
            }

            if (previousInstallationId.isNotEmpty() && !previousInstallationId.isUuid()) {
                add(Error.INVALID_INSTALLATION_ID)
            }
        }
    }

    private fun isDomainWellFormatted(domain: String) = !domain.startsWith("http") &&
        !domain.startsWith("/") &&
        !domain.endsWith("/")

    private fun isDomainValid(
        domain: String
    ) = PatternsCompat.DOMAIN_NAME.matcher(domain).matches()
}
