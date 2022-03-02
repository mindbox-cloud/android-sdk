package cloud.mindbox.mobile_sdk.models

import java.util.*

internal fun String.isUuid() = if (this.isNotBlank()) {
    try {
        UUID.fromString(this)
        true
    } catch (e: Exception) {
        false
    }
} else {
    false
}