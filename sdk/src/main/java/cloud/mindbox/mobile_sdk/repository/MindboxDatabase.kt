package cloud.mindbox.mobile_sdk.repository

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import cloud.mindbox.mobile_sdk.converters.MindboxRoomConverter
import cloud.mindbox.mobile_sdk.data.ConfigurationsDao
import cloud.mindbox.mobile_sdk.data.EventsDao
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Event

@Database(entities = [Configuration::class, Event::class], version = 1)
@TypeConverters(MindboxRoomConverter::class)
internal abstract class MindboxDatabase : RoomDatabase() {

    abstract fun configurationDao(): ConfigurationsDao

    abstract fun eventsDao(): EventsDao

}
