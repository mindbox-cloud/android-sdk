package cloud.mindbox.mobile_sdk

import androidx.core.util.PatternsCompat

internal object SdkValidation {

    internal const val ERROR_EMPTY_DOMAIN = "Domain must not be empty"
    internal const val ERROR_INVALID_FORMAT_DOMAIN =
        "The domain must not start with https:// and must not end with /"
    internal const val ERROR_INVALID_DOMAIN = "The domain is not valid"
    internal const val ERROR_EMPTY_ENDPOINT = "Endpoint must not be empty"
    internal const val ERROR_INVALID_DEVICE_ID = "Invalid previous device UUID format"
    internal const val ERROR_INVALID_INSTALLATION_ID = "Invalid UUID format of previous installationId"

    fun validateConfiguration(
        domain: String,
        endpointId: String,
        previousDeviceUUID: String,
        previousInstallationId: String
    ) = runCatching {
        mutableListOf<String>().apply {
            when {
                domain.isBlank() -> add(ERROR_EMPTY_DOMAIN)
                !isDomainWellFormatted(domain) -> add(ERROR_INVALID_FORMAT_DOMAIN)
                !isDomainValid(domain) -> add(ERROR_INVALID_DOMAIN)
            }

            if (endpointId.isBlank()) {
                add(ERROR_EMPTY_ENDPOINT)
            }

            if (previousDeviceUUID.isNotEmpty() && !previousDeviceUUID.isUuid()) {
                add(ERROR_INVALID_DEVICE_ID)
            }

            if (previousDeviceUUID.isNotEmpty() && !previousInstallationId.isUuid()) {
                add(ERROR_INVALID_INSTALLATION_ID)
            }
        }
    }.returnOnException { emptyList() }

    private fun isDomainWellFormatted(domain: String) = !domain.startsWith("http")
            && !domain.startsWith("/")
            && !domain.endsWith("/")

    private fun isDomainValid(
        domain: String
    ) = PatternsCompat.DOMAIN_NAME.matcher(domain).matches()

}