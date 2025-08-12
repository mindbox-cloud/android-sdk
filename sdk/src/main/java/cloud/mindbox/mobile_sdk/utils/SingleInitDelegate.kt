package cloud.mindbox.mobile_sdk.utils

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class SingleInitDelegate<T> : ReadWriteProperty<Any?, List<T>> {

    private var value: List<T> = emptyList()

    override fun getValue(thisRef: Any?, property: KProperty<*>): List<T> {
        return value
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: List<T>) {
        if (this.value.isEmpty() && value.isNotEmpty()) {
            this.value = value
        }
    }
}
