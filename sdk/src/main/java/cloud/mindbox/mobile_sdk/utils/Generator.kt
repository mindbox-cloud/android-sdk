package cloud.mindbox.mobile_sdk.utils

import java.util.concurrent.atomic.AtomicInteger

internal object Generator {

    private val atomicInteger = AtomicInteger()

    fun generateUniqueInt() = System.currentTimeMillis().toInt() + atomicInteger.incrementAndGet()

}