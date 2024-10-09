package cloud.mindbox.mobile_sdk.data

import androidx.room.*
import cloud.mindbox.mobile_sdk.managers.DbManager.EVENTS_TABLE_NAME
import cloud.mindbox.mobile_sdk.models.Event

@Dao
internal interface EventsDao {

    @Transaction
    @Query("SELECT * FROM $EVENTS_TABLE_NAME")
    fun getAll(): List<Event>

    @Insert
    fun insert(event: Event)

    @Query("DELETE FROM $EVENTS_TABLE_NAME WHERE transactionId = :transactionId")
    fun delete(transactionId: String)

    @Delete
    fun deleteEvents(events: List<Event>)

    @Query("DELETE FROM $EVENTS_TABLE_NAME")
    fun deleteAll()
}
