package cloud.mindbox.mobile_sdk.utils

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class SingleInitDelegate<T> : ReadWriteProperty<Any?, List<T>> {

    @Volatile
    var assigned: List<T>? = null
        private set

    override fun getValue(thisRef: Any?, property: KProperty<*>): List<T> {
        return assigned.orEmpty()
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: List<T>) {
        if (assigned.isNullOrEmpty()) {
            assigned = value
        }
    }
}
