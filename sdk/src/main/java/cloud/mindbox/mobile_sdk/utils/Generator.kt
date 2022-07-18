package cloud.mindbox.mobile_sdk.utils

import java.util.concurrent.atomic.AtomicInteger

internal object Generator {

    private val atomicInteger = AtomicInteger()

    fun generateInt(): Int {
        return System.currentTimeMillis().toInt() + atomicInteger.incrementAndGet()
    }

}