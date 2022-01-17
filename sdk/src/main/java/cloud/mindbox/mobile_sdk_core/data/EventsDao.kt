package cloud.mindbox.mobile_sdk_core.data

import androidx.room.*
import cloud.mindbox.mobile_sdk_core.managers.DbManager.EVENTS_TABLE_NAME
import cloud.mindbox.mobile_sdk_core.models.Event

@Dao
internal interface EventsDao {

    @Transaction
    @Query("SELECT * FROM $EVENTS_TABLE_NAME")
    fun getAll(): List<Event>

    @Insert
    fun insert(event: Event)

    @Delete
    fun delete(event: Event)

    @Delete
    fun deleteEvents(events: List<Event>)

}
