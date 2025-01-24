package cloud.mindbox.mobile_sdk.utils

interface TimeProvider {
    fun currentTimeMillis(): Long
}

class DefaultTimeProvider : TimeProvider {
    override fun currentTimeMillis() = System.currentTimeMillis()
}
