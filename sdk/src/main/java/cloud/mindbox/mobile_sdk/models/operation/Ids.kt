package cloud.mindbox.mobile_sdk.models.operation

import cloud.mindbox.mobile_sdk.models.operation.adapters.IdsAdapter
import com.google.gson.annotations.JsonAdapter

@JsonAdapter(IdsAdapter::class)
class Ids(val ids: Map<String, String?>) {

    companion object {

        private const val MINDBOX_ID_KEY = "mindboxId"

    }

    constructor(vararg pairs: Pair<String, String?>) : this(pairs.toMap())

    constructor(
        mindboxId: Int,
        ids: Map<String, String?>
    ) : this(HashMap<String, String?>(ids).apply { this[MINDBOX_ID_KEY] = "$mindboxId" })

    constructor(
        mindboxId: Int,
        vararg pairs: Pair<String, String?>
    ) : this(HashMap<String, String?>(pairs.toMap()).apply { this[MINDBOX_ID_KEY] = "$mindboxId" })

}
