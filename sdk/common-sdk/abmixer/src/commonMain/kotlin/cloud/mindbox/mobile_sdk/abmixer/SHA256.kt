package cloud.mindbox.mobile_sdk.abmixer

@OptIn(ExperimentalUnsignedTypes::class)
internal expect fun ByteArray.sha256(): UByteArray
