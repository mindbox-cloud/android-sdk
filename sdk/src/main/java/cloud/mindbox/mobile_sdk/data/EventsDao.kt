package cloud.mindbox.mobile_sdk.data

import androidx.room.*
import cloud.mindbox.mobile_sdk.managers.DbManager.EVENTS_TABLE_NAME
import cloud.mindbox.mobile_sdk.models.Event

@Dao
internal interface EventsDao {

    @Query("SELECT * FROM $EVENTS_TABLE_NAME")
    fun getAll(): List<Event>

    @Insert
    fun insert(event: Event)

    @Delete
    fun delete(event: Event)

}
