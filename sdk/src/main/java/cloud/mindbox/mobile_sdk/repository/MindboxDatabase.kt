package cloud.mindbox.mobile_sdk.repository

import android.content.Context
import androidx.room.Database
import androidx.room.Room
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

    companion object {

        private const val DATABASE_NAME = "mindbox_db"
        internal var isTestMode = false

        internal fun getInstance(context: Context) = if (!isTestMode) {
            Room.databaseBuilder(
                context.applicationContext,
                MindboxDatabase::class.java,
                DATABASE_NAME
            ).build()
        } else {
            Room.inMemoryDatabaseBuilder(context.applicationContext, MindboxDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }

    }

    abstract fun configurationDao(): ConfigurationsDao

    abstract fun eventsDao(): EventsDao

}
