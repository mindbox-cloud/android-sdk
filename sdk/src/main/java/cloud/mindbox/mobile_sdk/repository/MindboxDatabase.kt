package cloud.mindbox.mobile_sdk.repository

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cloud.mindbox.mobile_sdk.converters.MindboxRoomConverter
import cloud.mindbox.mobile_sdk.data.ConfigurationsDao
import cloud.mindbox.mobile_sdk.data.EventsDao
import cloud.mindbox.mobile_sdk.managers.DbManager.CONFIGURATION_TABLE_NAME
import cloud.mindbox.mobile_sdk.managers.DbManager.EVENTS_TABLE_NAME
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Event

@Database(entities = [Configuration::class, Event::class], version = 3)
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {

            override fun migrate(database: SupportSQLiteDatabase) {
                val query = "ALTER TABLE $EVENTS_TABLE_NAME " +
                        "ADD COLUMN isSending INTEGER NOT NULL DEFAULT 0"
                database.execSQL(query)
            }

        }

        internal var isTestMode = false

        internal fun getInstance(context: Context) = if (!isTestMode) {
            Room.databaseBuilder(
                context.applicationContext,
                MindboxDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        } else {
            Room.inMemoryDatabaseBuilder(context.applicationContext, MindboxDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }

    }

    abstract fun configurationDao(): ConfigurationsDao

    abstract fun eventsDao(): EventsDao

}
