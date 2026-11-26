package cloud.mindbox.mobile_sdk.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import cloud.mindbox.mobile_sdk.managers.DbManager.PUSH_DELIVERIES_TABLE_NAME

@Entity(tableName = PUSH_DELIVERIES_TABLE_NAME)
internal data class PushDelivery(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val url: String,
    val receivedAt: Long,
    val attempts: Int = 0,
    val isHighPriority: Boolean = false,
    val provider: String? = null,
    val isInQueue: Boolean = false,
) {
    fun isTooOld(now: Timestamp): Boolean = elapsedAt(now).interval >= LIFETIME.interval

    fun elapsedAt(now: Timestamp): Milliseconds = Milliseconds((now.ms - receivedAt).coerceAtLeast(0L))

    private companion object {
        val LIFETIME = Milliseconds(15_552_000_000L)
    }
}
