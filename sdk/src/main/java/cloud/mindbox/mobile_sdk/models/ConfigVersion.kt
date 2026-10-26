package cloud.mindbox.mobile_sdk.models

@JvmInline
internal value class ConfigVersion(val number: Long) : Comparable<ConfigVersion> {

    fun next(): ConfigVersion = ConfigVersion(number + 1)

    override fun compareTo(other: ConfigVersion): Int = number.compareTo(other.number)

    companion object {
        val FIRST = ConfigVersion(1L)
    }
}
