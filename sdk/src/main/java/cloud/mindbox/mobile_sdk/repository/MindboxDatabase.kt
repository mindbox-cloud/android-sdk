package cloud.mindbox.mobile_sdk.repository

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cloud.mindbox.mobile_sdk.converters.MindboxRoomConverter
import cloud.mindbox.mobile_sdk.data.ConfigurationsDao
import cloud.mindbox.mobile_sdk.data.EventsDao
import cloud.mindbox.mobile_sdk.managers.DbManager.CONFIGURATION_TABLE_NAME
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Event

@Database(entities = [Configuration::class, Event::class], exportSchema = false, version = 2)
@TypeConverters(MindboxRoomConverter::class)
internal abstract class MindboxDatabase : RoomDatabase() {

    companion object {

        private const val DATABASE_NAME = "mindbox_db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {

            override fun migrate(database: SupportSQLiteDatabase) {
                val query = "ALTER TABLE $CONFIGURATION_TABLE_NAME " +
                    "ADD COLUMN shouldCreateCustomer INTEGER NOT NULL DEFAULT 1"
                database.execSQL(query)
            }
        }

        internal var isTestMode = false

        internal fun getInstance(context: Context) = if (!isTestMode) {
            Room
                .databaseBuilder(
                    context.applicationContext,
                    MindboxDatabase::class.java,
                    DATABASE_NAME,
                ).addMigrations(MIGRATION_1_2)
                .build()
        } else {
            Room
                .inMemoryDatabaseBuilder(context.applicationContext, MindboxDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }
    }

    abstract fun configurationDao(): ConfigurationsDao

    abstract fun eventsDao(): EventsDao
}
