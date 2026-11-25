package cloud.mindbox.mobile_sdk.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import cloud.mindbox.mobile_sdk.managers.DbManager.PUSH_DELIVERIES_TABLE_NAME
import cloud.mindbox.mobile_sdk.models.PushDelivery

@Dao
internal interface PushDeliveriesDao {

    @Insert
    fun insert(delivery: PushDelivery): Long

    @Query("SELECT * FROM $PUSH_DELIVERIES_TABLE_NAME WHERE id = :id")
    fun get(id: Long): PushDelivery?

    @Query("SELECT id FROM $PUSH_DELIVERIES_TABLE_NAME WHERE nextAttemptAt IS NULL")
    fun getIdsOutOfQueue(): List<Long>

    @Query(
        "SELECT * FROM $PUSH_DELIVERIES_TABLE_NAME " +
            "WHERE nextAttemptAt IS NOT NULL AND (nextAttemptAt <= :now OR nextAttemptAt > :latest) " +
            "ORDER BY attempts, nextAttemptAt, RANDOM() LIMIT :limit"
    )
    fun getDue(now: Long, latest: Long, limit: Int): List<PushDelivery>

    @Query("SELECT MIN(nextAttemptAt) FROM $PUSH_DELIVERIES_TABLE_NAME")
    fun getEarliestAttemptTime(): Long?

    @Query(
        "UPDATE $PUSH_DELIVERIES_TABLE_NAME SET attempts = :attempts, throttles = :throttles, " +
            "nextAttemptAt = :nextAttemptAt WHERE id = :id"
    )
    fun update(
        id: Long,
        attempts: Int,
        throttles: Int,
        nextAttemptAt: Long?
    ): Int

    @Query(
        "UPDATE $PUSH_DELIVERIES_TABLE_NAME SET nextAttemptAt = :until - 1, " +
            "attempts = MAX(attempts, :attempts), throttles = :throttles " +
            "WHERE nextAttemptAt IS NOT NULL AND attempts >= :minAttempts " +
            "AND (attempts >= :ownPauseAttempts OR receivedAt <= :ownPauseReceivedAfter) " +
            "AND (nextAttemptAt < :until OR nextAttemptAt > :latest)"
    )
    fun postpone(
        minAttempts: Int,
        attempts: Int,
        throttles: Int,
        until: Long,
        latest: Long,
        ownPauseAttempts: Int,
        ownPauseReceivedAfter: Long
    ): Int

    @Query("DELETE FROM $PUSH_DELIVERIES_TABLE_NAME WHERE id = :id")
    fun delete(id: Long)

    @Query("DELETE FROM $PUSH_DELIVERIES_TABLE_NAME")
    fun deleteAll()
}
