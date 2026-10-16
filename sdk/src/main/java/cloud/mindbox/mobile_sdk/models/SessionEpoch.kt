package cloud.mindbox.mobile_sdk.models

@JvmInline
internal value class SessionEpoch(val number: Long) : Comparable<SessionEpoch> {

    fun next(): SessionEpoch = SessionEpoch(number + 1)

    override fun compareTo(other: SessionEpoch): Int = number.compareTo(other.number)

    companion object {
        val FIRST = SessionEpoch(0L)
    }
}
