package cloud.mindbox.mobile_sdk.models.operation

import cloud.mindbox.mobile_sdk.models.operation.adapters.CustomerFieldsAdapter
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.google.gson.Gson
import com.google.gson.annotations.JsonAdapter
import androidx.core.util.Pair as AndroidXPair

@JsonAdapter(CustomerFieldsAdapter::class)
public class CustomFields(public val fields: Map<String, Any?>? = null) {

    /**
     * Convert [CustomFields] value to [T] typed object.
     *
     * @param classOfT Class type for result [CustomFields] object.
     */
    public fun <T> convertTo(classOfT: Class<T>): T? = LoggingExceptionHandler.runCatching(defaultValue = null) {
        val gson = Gson()
        gson.fromJson(gson.toJson(fields), classOfT)
    }

    public constructor(vararg pairs: Pair<String, Any?>) : this(pairs.toMap())

    public constructor(
        vararg pairs: AndroidXPair<String, Any?>
    ) : this(*pairs.mapNotNull { pair -> pair.first?.let { Pair(it, pair.second) } }.toTypedArray())

    override fun toString(): String = "CustomFields(fields=$fields)"
}
