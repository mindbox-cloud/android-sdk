package cloud.mindbox.mobile_sdk

import androidx.core.util.PatternsCompat
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler

internal object SdkValidation {

    enum class Error(val critical: Boolean, val message: String) {

        EMPTY_DOMAIN(true, "Domain must not be empty"),
        INVALID_FORMAT_DOMAIN(true, "The domain format is not valid"),
        INVALID_DOMAIN(true, "The domain is not valid"),
        EMPTY_ENDPOINT(true, "Endpoint must not be empty"),
        INVALID_DEVICE_ID(false, "Invalid previous device UUID format"),
        INVALID_INSTALLATION_ID(false, "Invalid UUID format of previous installationId"),
        INVALID_OPERATIONS_DOMAIN(false, "The operationsDomain is not valid, it will be ignored");

        override fun toString() = "$name(critical=$critical, message=$message)"
    }

    /**
     * Strips http:// or https:// scheme and trailing slashes from [input].
     * "https://api.mindbox.ru/" → "api.mindbox.ru"
     * "api.mindbox.ru/" → "api.mindbox.ru"
     */
    fun extractHost(input: String): String =
        input.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')

    /**
     * Returns a full base URL. If [hostOrUrl] already contains a scheme (http:// or https://),
     * it is preserved. Otherwise https:// is prepended.
     * "api.mindbox.ru" → "https://api.mindbox.ru"
     * "http://proxy.example.com" → "http://proxy.example.com"
     */
    fun toBaseUrl(hostOrUrl: String): String =
        if (hostOrUrl.startsWith("http://") || hostOrUrl.startsWith("https://")) {
            hostOrUrl.trimEnd('/')
        } else {
            "https://${hostOrUrl.trimEnd('/')}"
        }

    /**
     * Returns true if [domain] is a valid domain host, accepting optional http:// or https:// prefix
     * and optional trailing slash.
     */
    fun isValidDomain(domain: String): Boolean {
        val host = extractHost(domain)
        return host.isNotBlank() && isDomainValid(host)
    }

    fun validateConfiguration(
        domain: String,
        endpointId: String,
        previousDeviceUUID: String,
        previousInstallationId: String,
        operationsDomain: String? = null,
    ) = LoggingExceptionHandler.runCatching(defaultValue = listOf()) {
        mutableListOf<Error>().apply {
            when {
                domain.isBlank() -> add(Error.EMPTY_DOMAIN)
                else -> {
                    val host = extractHost(domain)
                    when {
                        host.isBlank() -> add(Error.INVALID_FORMAT_DOMAIN)
                        !isDomainValid(host) -> add(Error.INVALID_DOMAIN)
                    }
                }
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

            if (operationsDomain != null && !isValidDomain(operationsDomain)) {
                add(Error.INVALID_OPERATIONS_DOMAIN)
            }
        }
    }

    private fun isDomainValid(domain: String) = PatternsCompat.DOMAIN_NAME.matcher(domain).matches()
}
