package cloud.mindbox.mobile_sdk.monitoring.domain.models

import org.threeten.bp.ZonedDateTime
import java.security.MessageDigest

internal data class LogRequest(
    val requestId: String,
    val target: Md5Hash,
    val from: ZonedDateTime,
    val to: ZonedDateTime,
)

@JvmInline
internal value class Md5Hash private constructor(private val hex: String) {

    companion object {
        fun ofHex(hex: String): Md5Hash = Md5Hash(hex.lowercase())

        fun ofDeviceUuid(deviceUuid: String): Md5Hash = Md5Hash(
            MessageDigest.getInstance("MD5")
                .digest(deviceUuid.lowercase().toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
        )
    }
}
