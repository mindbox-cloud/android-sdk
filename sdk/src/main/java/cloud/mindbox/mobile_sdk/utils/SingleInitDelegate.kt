package cloud.mindbox.mobile_sdk.utils

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class SingleInitDelegate<T> : ReadWriteProperty<Any?, List<T>> {

    @Volatile
    var value: List<T> = emptyList()
        private set

    @Volatile
    var isAssigned: Boolean = false
        private set

    override fun getValue(thisRef: Any?, property: KProperty<*>): List<T> {
        return value
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: List<T>) {
        isAssigned = true
        if (this.value.isEmpty() && value.isNotEmpty()) {
            this.value = value
        }
    }
}
