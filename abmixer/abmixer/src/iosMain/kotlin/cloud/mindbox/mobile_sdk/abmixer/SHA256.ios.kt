package cloud.mindbox.mobile_sdk.abmixer

import kotlinx.cinterop.*
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class)
actual fun ByteArray.sha256(): UByteArray {
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    this.usePinned { inputPinned ->
        digest.usePinned { digestPinned ->
            CC_SHA256(inputPinned.addressOf(0), this.size.convert(), digestPinned.addressOf(0))
        }
    }
    return digest
}