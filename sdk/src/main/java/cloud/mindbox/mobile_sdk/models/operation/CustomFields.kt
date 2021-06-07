package cloud.mindbox.mobile_sdk.models.operation

import cloud.mindbox.mobile_sdk.models.operation.adapters.CustomerFieldsAdapter
import com.google.gson.annotations.JsonAdapter

@JsonAdapter(CustomerFieldsAdapter::class)
class CustomFields(val fields: Map<String, Any?>? = null) {

    constructor(vararg pairs: Pair<String, Any?>) : this(pairs.toMap())

    override fun toString() = "CustomFields(fields=$fields)"

}
