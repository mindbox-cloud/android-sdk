package cloud.mindbox.mobile_sdk.models

import androidx.core.util.PatternsCompat
import cloud.mindbox.mobile_sdk.isUuid
import java.util.*

data class ValidationError(
    var messages: List<String> = emptyList()
) {

    companion object {
        private const val ERROR_EMPTY_DOMAIN = "Domain must not be empty"
        private const val ERROR_INVALID_FORMAT_DOMAIN =
            "The domain must not start with https:// and must not end with /"
        private const val ERROR_INVALID_DOMAIN = "The domain is not valid"
        private const val ERROR_EMPTY_ENDPOINT = "Endpoint must not be empty"
        private const val ERROR_INVALID_DEVICE_ID = "Invalid device UUID format"
        private const val ERROR_INVALID_INSTALLATION_ID = "Invalid UUID format of installationId"
    }

    fun validateFields(domain: String, endpoint: String, deviceUuid: String, installId: String) {
        val errors = arrayListOf<String>()

        if (domain.trim().isEmpty()) {
            errors.add(ERROR_EMPTY_DOMAIN)
        }

        if (domain.startsWith("http") || domain.startsWith("/") || domain.endsWith("/")) {
            errors.add(ERROR_INVALID_FORMAT_DOMAIN)
        } else if (domain.trim().isNotEmpty() && !PatternsCompat.WEB_URL.matcher("https://$domain/")
                .matches()
        ) {
            errors.add(ERROR_INVALID_DOMAIN)
        }

        if (endpoint.trim().isEmpty()) {
            errors.add(ERROR_EMPTY_ENDPOINT)
        }

        if (deviceUuid.trim().isNotEmpty()) {
            try {
                UUID.fromString(deviceUuid)
            } catch (e: Exception) {
                errors.add(ERROR_INVALID_DEVICE_ID)
            }
        }

        if (!installId.isUuid()) {
            errors.add(ERROR_INVALID_INSTALLATION_ID)
        }

        this.messages = errors.toList()
    }
}