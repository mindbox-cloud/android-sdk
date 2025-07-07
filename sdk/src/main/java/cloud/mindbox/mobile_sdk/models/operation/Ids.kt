package cloud.mindbox.mobile_sdk.models.operation

import cloud.mindbox.mobile_sdk.models.operation.adapters.IdsAdapter
import com.google.gson.annotations.JsonAdapter
import androidx.core.util.Pair as AndroidXPair

@JsonAdapter(IdsAdapter::class)
public class Ids(public val ids: Map<String, String?>) {

    private companion object {

        private const val MINDBOX_ID_KEY = "mindboxId"
    }

    public constructor(vararg pairs: Pair<String, String?>) : this(pairs.toMap())

    public constructor(
        vararg pairs: AndroidXPair<String, String?>
    ) : this(*pairs.mapNotNull { pair -> pair.first?.let { Pair(it, pair.second) } }.toTypedArray())

    public constructor(
        mindboxId: Int,
        ids: Map<String, String?>
    ) : this(HashMap<String, String?>(ids).apply { this[MINDBOX_ID_KEY] = "$mindboxId" })

    public constructor(
        mindboxId: Int,
        vararg pairs: Pair<String, String?>
    ) : this(HashMap<String, String?>(pairs.toMap()).apply { this[MINDBOX_ID_KEY] = "$mindboxId" })

    public constructor(
        mindboxId: Int,
        vararg pairs: AndroidXPair<String, String?>
    ) : this(
        mindboxId = mindboxId,
        *pairs.mapNotNull { pair -> pair.first?.let { Pair(it, pair.second) } }.toTypedArray()
    )

    override fun toString(): String = "Ids(ids=$ids)"
}
