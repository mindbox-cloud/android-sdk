package cloud.mindbox.mobile_sdk.abmixer

import java.security.MessageDigest

@OptIn(ExperimentalUnsignedTypes::class)
actual fun ByteArray.sha256(): UByteArray {
    val sha256 = MessageDigest.getInstance("SHA-256").apply { reset() }
    return sha256.digest(this).toUByteArray()
}