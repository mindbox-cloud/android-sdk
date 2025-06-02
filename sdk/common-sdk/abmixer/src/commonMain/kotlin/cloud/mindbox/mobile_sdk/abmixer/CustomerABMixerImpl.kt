package cloud.mindbox.mobile_sdk.abmixer

internal class CustomerAbMixerImpl : CustomerAbMixer {

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun stringModulusHash(identifier: String, salt: String): Int {
        val saltedId = identifier.uppercase() + salt.uppercase()

        val bytes = saltedId.encodeToByteArray()
        val hash = bytes.sha256()

        val bigEndianLastBytesAsInt: Int =
            (hash[28] shl 24) or (hash[29] shl 16) or (hash[30] shl 8) or hash[31].toInt()
        val unsigned = bigEndianLastBytesAsInt.toUInt()

        return (unsigned % 100u).toInt()
    }

    private infix fun UByte.shl(that: Int): Int = this.toInt().shl(that)
}
