package cloud.mindbox.mobile_sdk.inapp.data.dto.deserializers

import cloud.mindbox.mobile_sdk.models.TimeSpan
import com.google.gson.JsonObject

internal fun JsonObject.getAsIntOrNull(key: String): Int? {
    return get(key)?.let { element ->
        when {
            element.isJsonPrimitive && element.asJsonPrimitive.isNumber -> {
                val number = element.asNumber
                if (number.toLong() in Int.MIN_VALUE..Int.MAX_VALUE) number.toInt() else null
            }
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString.toIntOrNull()
            else -> null
        }
    }
}

internal fun JsonObject.getAsTimeSpan(key: String): TimeSpan? {
    return get(key)?.let { element ->
        when {
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> TimeSpan.fromStringOrNull(element.asString)
            else -> null
        }
    }
}
