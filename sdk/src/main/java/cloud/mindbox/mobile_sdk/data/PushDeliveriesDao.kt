package cloud.mindbox.mobile_sdk.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import cloud.mindbox.mobile_sdk.managers.DbManager.PUSH_DELIVERIES_TABLE_NAME
import cloud.mindbox.mobile_sdk.models.PushDelivery

@Dao
internal interface PushDeliveriesDao {

    @Insert
    fun insert(delivery: PushDelivery): Long

    @Query("SELECT * FROM $PUSH_DELIVERIES_TABLE_NAME WHERE id = :id")
    fun get(id: Long): PushDelivery?

    @Query("SELECT id FROM $PUSH_DELIVERIES_TABLE_NAME WHERE isInQueue = 0")
    fun getIdsOutOfQueue(): List<Long>

    @Query("SELECT * FROM $PUSH_DELIVERIES_TABLE_NAME WHERE isInQueue = 1 ORDER BY id")
    fun getQueued(): List<PushDelivery>

    @Update
    fun update(delivery: PushDelivery): Int

    @Query("DELETE FROM $PUSH_DELIVERIES_TABLE_NAME WHERE id = :id")
    fun delete(id: Long)

    @Query("DELETE FROM $PUSH_DELIVERIES_TABLE_NAME")
    fun deleteAll()
}
