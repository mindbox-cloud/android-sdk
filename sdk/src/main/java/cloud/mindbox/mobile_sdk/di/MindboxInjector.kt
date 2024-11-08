package cloud.mindbox.mobile_sdk.di

import cloud.mindbox.mobile_sdk.di.modules.AppModule
import kotlin.reflect.KProperty

internal fun <T> mindboxInject(initializer: AppModule.() -> T) = MindboxInjector(initializer)

internal class MindboxInjector<T>(private val initializer: AppModule.() -> T) {
    operator fun getValue(thisRef: Any, property: KProperty<*>): T {
        return initializer.invoke(MindboxDI.appModule)
    }
}
